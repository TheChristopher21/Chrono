import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';

// Supply tokens through the process environment; tokens and response bodies are never logged.
const base = (__ENV.LOAD_BASE_URL || 'http://127.0.0.1:8081').replace(/\/$/, '');
const staff = JSON.parse(__ENV.LOAD_STAFF_JSON || '[]');
const writes = __ENV.LOAD_ALLOW_WRITES === 'true';
const writeFraction = Number(__ENV.LOAD_WRITE_FRACTION || 0.05);
const day = __ENV.LOAD_BUSINESS_DATE || new Date().toISOString().slice(0, 10);
const roles = ['reception', 'housekeeping', 'finance', 'management'];
const readLatency = new Trend('pms_staff_read_ms', true);
const writeLatency = new Trend('pms_staff_write_ms', true);
const failures = new Rate('pms_staff_failures');
const conflicts = new Rate('pms_staff_write_conflicts');
const authFailures = new Rate('pms_staff_auth_failures');
const scenarios = {};
for (const role of roles) {
    if (staff.some((person) => person.role === role)) scenarios[role] = {
        executor: 'constant-vus', vus: Number(__ENV[`LOAD_${role.toUpperCase()}_VUS`] || 5),
        duration: __ENV.LOAD_DURATION || '5m', exec: 'staffWork', tags: { role },
    };
}
export const options = {
    scenarios,
    thresholds: {
        pms_staff_read_ms: [`p(95)<${Number(__ENV.LOAD_READ_P95_MS || 750)}`, 'p(99)<2000'],
        pms_staff_failures: ['rate<0.01'], pms_staff_auth_failures: ['rate==0'],
        ...Object.fromEntries(roles.filter((role) => scenarios[role]).map((role) => [
            `pms_staff_read_ms{role:${role}}`, [`p(95)<${Number(__ENV.LOAD_READ_P95_MS || 750)}`],
        ])),
        ...(writes ? { pms_staff_write_ms: [`p(95)<${Number(__ENV.LOAD_WRITE_P95_MS || 1500)}`, 'p(99)<3000'], pms_staff_write_conflicts: ['rate<0.1'] } : {}),
    },
};
export function setup() {
    if (!staff.length || staff.some((person) => !person.token || !person.propertyId || !roles.includes(person.role)))
        throw new Error('LOAD_STAFF_JSON requires [{token, propertyId, role}] with reception, housekeeping, finance or management roles.');
    if (writes && __ENV.LOAD_ENVIRONMENT !== 'isolated') throw new Error('Write workloads require LOAD_ENVIRONMENT=isolated and LOAD_ALLOW_WRITES=true.');
    if (writes && !staff.some((person) => person.role === 'reception' || person.role === 'housekeeping' && person.taskId))
        throw new Error('Write workloads require a reception identity or a housekeeping identity with an isolated taskId.');
    if (!(writeFraction >= 0 && writeFraction <= 1)) throw new Error('LOAD_WRITE_FRACTION must be between zero and one.');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) throw new Error('LOAD_BUSINESS_DATE must be YYYY-MM-DD.');
    for (const person of staff) {
        const response = http.get(`${base}/api/pms/access/me`, params(person, 'access-check'));
        if (response.status !== 200) throw new Error('A staff identity could not access PMS. Check token lifetime and hotel grants.');
        const grants = response.json();
        if (!grants.master && !grants.properties.some((property) => Number(property.propertyId) === Number(person.propertyId)))
            throw new Error('A staff identity lacks its configured hotel grant.');
    }
}
function params(person, operation, allowConflict = false) {
    return { headers: { Authorization: `Bearer ${person.token}`, 'Content-Type': 'application/json' },
        tags: { operation, role: person.role, property: String(person.propertyId) },
        responseCallback: allowConflict ? http.expectedStatuses(200, 201, 409) : http.expectedStatuses(200, 201) };
}
function record(response, mutation) {
    (mutation ? writeLatency : readLatency).add(response.timings.duration, response.tags);
    const allowed = response.status >= 200 && response.status < 300 || mutation && response.status === 409;
    failures.add(!allowed); authFailures.add(response.status === 401 || response.status === 403);
    if (mutation) conflicts.add(response.status === 409);
    check(response, { 'staff operation succeeds or returns a controlled write conflict': () => allowed });
}
export function staffWork() {
    const candidates = staff.filter((person) => person.role === exec.scenario.name);
    const person = candidates[(exec.vu.idInTest - 1) % candidates.length];
    const prefix = `${base}/api/pms/properties/${person.propertyId}`;
    if (person.role === 'reception') {
        const paths = [`${prefix}/room-plan?from=${day}&days=30&page=${__ITER % 5}&size=50`, `${base}/api/pms/operations?propertyId=${person.propertyId}&businessDate=${day}`, `${prefix}/guests/search?q=LOAD&limit=25`];
        record(http.get(paths[__ITER % paths.length], params(person, 'reception-read')), false);
        if (writes && Math.random() < writeFraction) {
            const suffix = `${exec.vu.idInTest}-${__ITER}-${Date.now()}`;
            record(http.post(`${prefix}/guests`, JSON.stringify({ firstName: 'LOAD', lastName: suffix,
                email: `load-${suffix}@example.invalid`, notes: 'Isolated mixed staff load fixture' }), params(person, 'guest-create', true)), true);
        }
    } else if (person.role === 'housekeeping') {
        const response = http.get(`${prefix}/housekeeping/work-orders?businessDate=${day}`, params(person, 'housekeeping-read'));
        record(response, false);
        if (writes && person.taskId && response.status === 200 && Math.random() < writeFraction) {
            const task = response.json().find((value) => Number(value.id) === Number(person.taskId));
            if (!task) throw new Error('Configured isolated housekeeping fixture task is absent on the selected date.');
            record(http.put(`${prefix}/housekeeping/work-orders/${task.id}`, JSON.stringify({ version: task.version,
                workStatus: task.workStatus === 'IN_PROGRESS' ? 'OPEN' : 'IN_PROGRESS', priority: task.priority,
                estimatedMinutes: task.estimatedMinutes, notes: 'Isolated staff load fixture', assignedTo: task.assignedTo }),
                params(person, 'housekeeping-update', true)), true);
        }
    } else if (person.role === 'finance') {
        record(http.get(`${prefix}/history/invoices?page=${__ITER % 10}&size=50`, params(person, 'finance-history')), false);
        record(http.get(`${prefix}/receivables?openOnly=true`, params(person, 'receivables-read')), false);
    } else {
        record(http.get(`${base}/api/pms/reports/portfolio?businessDate=${day}`, params(person, 'portfolio-read')), false);
    }
    sleep(Number(__ENV.LOAD_THINK_SECONDS || 0.5));
}
