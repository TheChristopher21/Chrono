// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ spawnSync: vi.fn(), readFileSync: vi.fn(), readdirSync: vi.fn() }));
vi.mock('node:child_process', () => ({ spawnSync: mocks.spawnSync }));
vi.mock('node:fs', () => ({ readFileSync: mocks.readFileSync, readdirSync: mocks.readdirSync }));

const routerFinding = { severity: 'high', via: [{ url: 'https://github.com/advisories/GHSA-qwww-vcr4-c8h2' }] };
const auditReport = (vulnerabilities = {}, status = Object.keys(vulnerabilities).length ? 1 : 0) => {
    mocks.spawnSync.mockReturnValue({ status, stdout: JSON.stringify({
        vulnerabilities, metadata: { vulnerabilities: { total: Object.keys(vulnerabilities).length } },
    }) });
};
const runAudit = () => import('../check-production-audit.mjs');

beforeEach(() => {
    vi.resetModules();
    vi.resetAllMocks();
    vi.spyOn(process, 'exit').mockImplementation(code => { throw new Error(`audit exited with ${code}`); });
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(process.stderr, 'write').mockImplementation(() => true);
    mocks.readFileSync.mockReturnValue(JSON.stringify({ version: '7.18.2' }));
    mocks.readdirSync.mockReturnValue([]);
    auditReport();
});
afterEach(() => vi.restoreAllMocks());

describe('production dependency audit gate', () => {
    it('accepts a clean report without requiring the obsolete DOMPurify version', async () => {
        await runAudit();
        expect(console.log).toHaveBeenCalledWith('[audit] No known production dependency vulnerabilities.');
        expect(mocks.readFileSync.mock.calls.some(([path]) => path.includes('dompurify'))).toBe(false);
    });

    it.each([
        ['axios', { severity: 'high', via: [{ url: 'https://github.com/advisories/GHSA-r4gj-5m52-g5wh' }] }],
        ['dompurify', { severity: 'moderate', via: [{ url: 'https://github.com/advisories/GHSA-55q2-fjhq-7xh7' }] }],
        ['dompurify', { severity: 'low', via: [{ url: 'https://github.com/advisories/GHSA-6688-9rhm-gjv2' }] }],
        ['jspdf', { severity: 'moderate', via: ['dompurify'] }],
    ])('rejects the production finding in %s, including former DOMPurify exceptions', async (name, finding) => {
        auditReport({ [name]: finding });
        await expect(runAudit()).rejects.toThrow('audit exited with 1');
        expect(console.error).toHaveBeenCalledWith(`[audit] ${finding.severity}: ${name}`);
    });

    it.each([
        { status: 1, stdout: JSON.stringify({ error: { code: 'ENOTFOUND' } }) },
        { status: 1, stdout: '{}' },
        { status: 2, stdout: JSON.stringify({ vulnerabilities: {}, metadata: { vulnerabilities: {} } }) },
        { status: 1, stdout: JSON.stringify({ vulnerabilities: {}, metadata: { vulnerabilities: {} } }) },
    ])('rejects an incomplete or failed audit: %j', async result => {
        mocks.spawnSync.mockReturnValue(result);
        await expect(runAudit()).rejects.toThrow('audit exited with 1');
        expect(console.warn).not.toHaveBeenCalled();
    });

    it('rejects malformed JSON', async () => {
        mocks.spawnSync.mockReturnValue({ status: 1, stdout: '{"vulnerabilities":' });
        await expect(runAudit()).rejects.toThrow('valid JSON report');
    });

    it('keeps the exact browser-only React Router exception', async () => {
        auditReport({ 'react-router': routerFinding, 'react-router-dom': { severity: 'high', via: ['react-router'] } });
        await runAudit();
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('GHSA-qwww-vcr4-c8h2'));
        expect(process.exit).not.toHaveBeenCalled();
    });

    it('rejects a different React Router version', async () => {
        auditReport({ 'react-router': routerFinding });
        mocks.readFileSync.mockReturnValue(JSON.stringify({ version: '7.18.3' }));
        await expect(runAudit()).rejects.toThrow('audit exited with 1');
    });

    it('rejects the exception if RSC server APIs become reachable in production source', async () => {
        auditReport({ 'react-router': routerFinding });
        mocks.readdirSync.mockReturnValue([{ name: 'server.jsx', isDirectory: () => false }]);
        mocks.readFileSync.mockImplementation(path => path.endsWith('package.json')
            ? JSON.stringify({ version: '7.18.2' }) : 'createRequestHandler');
        await expect(runAudit()).rejects.toThrow('audit exited with 1');
    });
});
