import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayrollConfigurationPanel from '../PayrollConfigurationPanel';
import api from '../../utils/api';

vi.mock('../../utils/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
const endpoint = '/api/admin/company/payroll-configuration';
beforeEach(() => {
    vi.resetAllMocks();
    api.get.mockImplementation(url => Promise.resolve({ data: url.endsWith('/terms') ? [] : { versions: [], employees: [{ id: 7, name: 'Testperson' }] } }));
});
async function insuranceForm() {
    const form = await screen.findByRole('form', { name: 'Firmenversicherungen' });
    await userEvent.type(within(form).getByLabelText('Gültig ab'), '2026-01-01');
    await userEvent.type(within(form).getByLabelText('Grundlage / Begründung'), 'Police 2026');
    return form;
}
describe('company payroll configuration', () => {
    it('stores entered percentages as decimal rates and preserves missing versus zero', async () => {
        api.post.mockImplementation((_url, input) => Promise.resolve({ data: { id: 1, ...input } }));
        render(<PayrollConfigurationPanel />);
        const form = await insuranceForm();
        await userEvent.type(within(form).getByLabelText('BU · Arbeitgeber (%)'), '1.25');
        await userEvent.type(within(form).getByLabelText('NBU · Arbeitnehmer (%)'), '0');
        await userEvent.click(within(form).getByRole('button', { name: 'Versicherungsversion anlegen' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith(`${endpoint}/insurance`, expect.objectContaining({ uvgBuRate: '0.01250000', uvgNbuRate: '0.00000000', ktgRateEmployee: null, effectiveFrom: '2026-01-01' })));
        expect(await screen.findByText(/Neue Version gespeichert/)).toBeInTheDocument();
        expect(screen.getByText('BU · Arbeitgeber: 1.25 %')).toBeInTheDocument();
    });
    it('saves personal monthly pension amounts without inventing an employer match', async () => {
        api.post.mockImplementation((_url, input) => Promise.resolve({ data: { id: 2, ...input } }));
        render(<PayrollConfigurationPanel />);
        const form = await screen.findByRole('form', { name: 'Persönliche BVG-Beiträge' });
        await userEvent.selectOptions(within(form).getByLabelText('Mitarbeitende'), '7');
        await userEvent.type(within(form).getByLabelText('Gültig ab'), '2026-02-01');
        await userEvent.type(within(form).getByLabelText('Grundlage / Begründung'), 'Vorsorgeausweis');
        await userEvent.type(within(form).getByLabelText('BVG Arbeitnehmer (CHF / Monat)'), '240.50');
        await userEvent.type(within(form).getByLabelText('BVG Arbeitgeber (CHF / Monat)'), '300');
        await userEvent.click(within(form).getByRole('button', { name: 'BVG-Version anlegen' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith(`${endpoint}/pension`, expect.objectContaining({ userId: 7, bvgEmployee: '240.5', bvgEmployer: '300' })));
    });
    it('retains entries on save failure and allows retry', async () => {
        api.post.mockRejectedValue({ response: { data: { message: 'Datum prüfen' } } });
        render(<PayrollConfigurationPanel />);
        const form = await insuranceForm();
        await userEvent.click(within(form).getByRole('button', { name: 'Versicherungsversion anlegen' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Datum prüfen');
        expect(within(form).getByLabelText('Grundlage / Begründung')).toHaveValue('Police 2026');
        expect(within(form).getByRole('button')).toBeEnabled();
    });
    it('does not expose empty editable settings when loading fails', async () => {
        api.get.mockRejectedValue(new Error('network'));
        render(<PayrollConfigurationPanel />);
        expect(await screen.findByRole('alert')).toHaveTextContent('konnten nicht geladen');
        expect(screen.queryByRole('form')).not.toBeInTheDocument();
    });
});

