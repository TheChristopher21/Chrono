import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.LOAD_BASE_URL || 'http://127.0.0.1:8081';
const propertyCode = __ENV.LOAD_PROPERTY_CODE;
const arrival = __ENV.LOAD_ARRIVAL;
const departure = __ENV.LOAD_DEPARTURE;
const ratePlanId = Number(__ENV.LOAD_RATE_PLAN_ID || 0);
const allowWrites = (__ENV.LOAD_ALLOW_WRITES || 'false').toLowerCase() === 'true';
const startRate = Number(__ENV.LOAD_START_RATE || 5);
const peakRate = Number(__ENV.LOAD_PEAK_RATE || 75);
const preAllocatedVUs = Number(__ENV.LOAD_PREALLOCATED_VUS || 40);
const maxVUs = Number(__ENV.LOAD_MAX_VUS || 200);
const rampDuration = __ENV.LOAD_RAMP_DURATION || '1m';
const peakDuration = __ENV.LOAD_PEAK_DURATION || '3m';
const cooldownDuration = __ENV.LOAD_COOLDOWN_DURATION || '1m';

const bookingFailures = new Rate('pms_booking_failures');
const availabilityLatency = new Trend('pms_availability_latency', true);

export const options = {
    scenarios: {
        availability: {
            executor: 'ramping-arrival-rate',
            startRate,
            timeUnit: '1s',
            preAllocatedVUs,
            maxVUs,
            stages: [
                { target: Math.max(startRate, Math.ceil(peakRate / 3)), duration: rampDuration },
                { target: peakRate, duration: peakDuration },
                { target: 0, duration: cooldownDuration },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        pms_availability_latency: ['p(95)<750'],
        pms_booking_failures: ['rate<0.01'],
    },
};

export function setup() {
    if (!propertyCode || !arrival || !departure) {
        throw new Error('LOAD_PROPERTY_CODE, LOAD_ARRIVAL and LOAD_DEPARTURE are required.');
    }
    if (allowWrites && !ratePlanId) {
        throw new Error('LOAD_RATE_PLAN_ID is required when LOAD_ALLOW_WRITES=true.');
    }
}

export default function () {
    const availability = http.get(
        `${baseUrl}/api/public/pms/booking/${propertyCode}/availability?arrival=${arrival}&departure=${departure}`,
        { tags: { operation: 'public-availability' } },
    );
    availabilityLatency.add(availability.timings.duration);
    check(availability, { 'availability is successful': (r) => r.status === 200 });

    if (allowWrites && availability.status === 200) {
        const idempotencyKey = `load-${__VU}-${__ITER}-${Date.now()}`;
        const response = http.post(
            `${baseUrl}/api/public/pms/booking/${propertyCode}/reservations`,
            JSON.stringify({
                arrivalDate: arrival,
                departureDate: departure,
                ratePlanId,
                adults: 1,
                children: 0,
                firstName: 'Load',
                lastName: `Guest-${__VU}-${__ITER}`,
                email: `load-${__VU}-${__ITER}@example.invalid`,
                termsAccepted: true,
                privacyAccepted: true,
            }),
            {
                headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
                tags: { operation: 'public-booking' },
                responseCallback: http.expectedStatuses(201, 409),
            },
        );
        bookingFailures.add(![201, 409].includes(response.status));
    }
    sleep(0.2);
}
