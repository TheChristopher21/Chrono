import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminWorkspaceVacationBalance from '../AdminWorkspaceVacationBalance';

const apiMock = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../utils/api', () => ({ default: apiMock }));
const t = (_key, fallback) => fallback;
const props = { username: 'Mirjam', startDate: '2026-09-21', endDate: '2026-09-25', t };
const deferred = () => {
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    return { promise, resolve };
};
beforeEach(() => apiMock.get.mockReset());
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe('server-backed workspace vacation balance', () => {
    it('loads each affected year and preserves fractional balances and zero', async () => {
        apiMock.get.mockImplementation((_url, config) => Promise.resolve({ data: config.params.year === 2026 ? 12.5 : 0 }));
        render(<AdminWorkspaceVacationBalance {...props} startDate="2026-12-28" endDate="2027-01-05" />);
        expect(await screen.findByText('12.5 Tage verfügbar')).toBeInTheDocument();
        expect(screen.getByText('0 Tage verfügbar')).toBeInTheDocument();
        expect(screen.getByText('2026')).toBeInTheDocument();
        expect(screen.getByText('2027')).toBeInTheDocument();
        expect(screen.getByText(/Offene Anträge sind noch nicht abgezogen/)).toBeInTheDocument();
        expect(apiMock.get.mock.calls.map(([, config]) => config.params.year)).toEqual([2026, 2027]);
    });

    it('shows an API error and offers retry without converting it to zero', async () => {
        apiMock.get.mockRejectedValueOnce(new Error('Network failed')).mockResolvedValueOnce({ data: 8 });
        render(<AdminWorkspaceVacationBalance {...props} />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Urlaubsstand konnte nicht geladen werden.');
        expect(screen.queryByText('0 Tage verfügbar')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Urlaubsstand erneut laden' }));
        expect(await screen.findByText('8 Tage verfügbar')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it.each([null, undefined, '12', Number.NaN, Number.POSITIVE_INFINITY])('does not show nonnumeric or absent API value %s as a balance', async value => {
        apiMock.get.mockResolvedValue({ data: value });
        render(<AdminWorkspaceVacationBalance {...props} />);
        expect(await screen.findByText('Kein gültiger Urlaubsstand verfügbar.')).toBeInTheDocument();
        expect(screen.queryByText(/Tage verfügbar/)).not.toBeInTheDocument();
    });

    it('aborts a superseded employee request and ignores a late response even if the transport resolves it', async () => {
        const first = deferred(), second = deferred();
        apiMock.get.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
        const { rerender } = render(<AdminWorkspaceVacationBalance {...props} />);
        const oldSignal = apiMock.get.mock.calls[0][1].signal;
        rerender(<AdminWorkspaceVacationBalance {...props} username="Luca" />);
        expect(oldSignal.aborted).toBe(true);
        await act(async () => second.resolve({ data: 7 }));
        expect(screen.getByText('7 Tage verfügbar')).toBeInTheDocument();
        await act(async () => first.resolve({ data: 99 }));
        expect(screen.queryByText('99 Tage verfügbar')).not.toBeInTheDocument();
        expect(screen.getByText('7 Tage verfügbar')).toBeInTheDocument();
    });

    it('reloads when absence data refreshes and aborts in-flight work when the detail closes', async () => {
        const pending = deferred();
        apiMock.get.mockResolvedValueOnce({ data: 12 }).mockReturnValueOnce(pending.promise);
        const { rerender, unmount } = render(<AdminWorkspaceVacationBalance {...props} revision={[]} />);
        expect(await screen.findByText('12 Tage verfügbar')).toBeInTheDocument();
        rerender(<AdminWorkspaceVacationBalance {...props} revision={[{ approved: true }]} />);
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(2));
        expect(screen.getByRole('status')).toHaveTextContent('Urlaubsstand wird geladen…');
        const signal = apiMock.get.mock.calls[1][1].signal;
        unmount();
        expect(signal.aborted).toBe(true);
        await act(async () => pending.resolve({ data: 9 }));
    });

    it('does not request an account without valid employee and request dates', () => {
        render(<AdminWorkspaceVacationBalance {...props} startDate="2026-02-30" />);
        expect(screen.getByText(/Ohne Mitarbeiter und gültigen Zeitraum/)).toBeInTheDocument();
        expect(apiMock.get).not.toHaveBeenCalled();
    });
});
