import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayrollTermsPanel from '../PayrollTermsPanel';
import api from '../../utils/api';
vi.mock('../../utils/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => { vi.resetAllMocks(); api.get.mockResolvedValue({ data: [] }); });
const employees = [{ id: 7, name: 'Testperson' }];
async function fillSwiss() {
  const form = await screen.findByRole('form', { name: 'Lohn- und Steuerregeln' });
  const q = within(form);
  await userEvent.selectOptions(q.getByLabelText('Mitarbeitende für Lohnregeln'), '7');
  for (const [label, value] of [
    ['Lohnregeln gültig ab','2026-01-01'], ['Vertrag / Steuergrundlage','Arbeitsvertrag 2026'],
    ['Vertraglicher Monatslohn (für das tatsächliche Pensum)','5000'], ['Vertragliche Wochenstunden','40'],
    ['Grundansatz pro ausbezahlter Überstunde','30'], ['Vereinbarter Überstundenzuschlag (%)','25'],
    ['Geburtsdatum für Beiträge','1990-01-01'], ['Eintrittsdatum für die Lohnabrechnung','2025-01-01'],
  ]) fireEvent.change(q.getByLabelText(label), { target: { value } });
  await userEvent.selectOptions(q.getByLabelText('Gesetzliche Wochenhöchstzeit'), '45');
  await userEvent.selectOptions(q.getByLabelText('Schweizer Steuerpflicht'), 'CH_ORDINARY');
  await userEvent.selectOptions(q.getByLabelText('Vertragliche Lohnkürzung im Teilmonat'), 'CALENDAR_DAYS');
  await userEvent.selectOptions(q.getByLabelText('BVG im Teilmonat gemäss Pensionskasse'), 'FULL');
  await userEvent.selectOptions(q.getByLabelText('13. Monatslohn: Auszahlung gemäss Vertrag'), 'NONE');
  await userEvent.click(q.getByRole('checkbox'));
  return q;
}
describe('dated personal payroll rules', () => {
  it('requires an explicit tax status and stores percent rates with exact contract amounts', async () => {
    api.post.mockImplementation((_url, input) => Promise.resolve({ data: { id: 9, ...input, parameters: JSON.stringify(input.terms) } }));
    render(<PayrollTermsPanel employees={employees} />);
    const q = await fillSwiss();
    await userEvent.click(q.getByRole('button', { name: 'Lohnregel als neue Version speichern' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/company/payroll-configuration/terms', expect.objectContaining({ userId: 7, effectiveFrom: '2026-01-01', terms: expect.objectContaining({ taxMode: 'CH_ORDINARY', amount: '5000', overtimePremiumRate: '0.250000', weeklyLimit: 45, monthlySingleEmployment: true }) })));
    expect(await screen.findByRole('status')).toHaveTextContent('Bestehende Abrechnungen bleiben unverändert');
  });
  it('keeps inputs after validation failure and does not fabricate a saved version', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Steuergrundlage prüfen' } } });
    render(<PayrollTermsPanel employees={employees} />); const q = await fillSwiss();
    await userEvent.click(q.getByRole('button', { name: 'Lohnregel als neue Version speichern' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Steuergrundlage prüfen');
    expect(q.getByLabelText('Vertrag / Steuergrundlage')).toHaveValue('Arbeitsvertrag 2026');
    expect(screen.getByText('Noch keine persönlichen Lohnregeln hinterlegt.')).toBeInTheDocument();
  });
  it('separates lifetime parent status from children under 25 and uses the full health rate', async () => {
    render(<PayrollTermsPanel employees={employees} />);
    const form = await screen.findByRole('form', { name: 'Lohn- und Steuerregeln' });
    await userEvent.selectOptions(within(form).getByLabelText('Abrechnungsland'), 'DE');
    expect(screen.getByLabelText('Elterneigenschaft in der Pflegeversicherung')).toHaveValue('');
    expect(screen.getByLabelText('Berücksichtigungsfähige Kinder unter 25')).toHaveValue(null);
    expect(screen.getByLabelText('Voller Zusatzbeitrag der Krankenkasse (%)')).toHaveValue(null);
  });
});
