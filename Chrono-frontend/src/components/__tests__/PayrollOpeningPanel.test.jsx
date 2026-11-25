import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayrollOpeningPanel from '../PayrollOpeningPanel';
import api from '../../utils/api';
vi.mock('../../utils/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => { vi.resetAllMocks(); api.get.mockResolvedValue({ data: [] }); });
const employees = [{ id: 7, name: 'Testperson' }];
async function fill() {
  await screen.findByRole('form', { name: 'Eröffnungswerte' });
  await userEvent.selectOptions(screen.getByLabelText('Mitarbeitende für Eröffnungswerte'), '7');
  for (const [label, value] of [
    ['Erste neue Lohnperiode ab', '2026-02-01'], ['Grundlage der Eröffnungswerte', 'Bestätigte Lohnunterlagen Januar'],
    ['Periodischer Grundlohn inkl. bezahlter Abwesenheiten/Zuschläge · Vorperiode 1', '5000'],
    ['Überstundenzuschläge · Vorperiode 1', '0'], ['Überstundenauszahlungen · Vorperiode 1', '0'],
    ['Bereits bezahlter 13. Monatslohn · Vorperiode 1', '0'], ['Anspruchsberechtigte Basis für den 13. Monatslohn · Vorperiode 1', '5000'],
    ['Bereits abgezogene Quellen-/Lohnsteuer (Erstattung negativ) · Vorperiode 1', '400'],
    ['Bereits belastete ALV-/UVG-Basis · Vorperiode 1', '5000'], ['Bereits belastete KTG-Basis · Vorperiode 1', '5000'],
    ['Vertragliche KTG-Jahreslohngrenze (0 = unbeschränkt) · Vorperiode 1', '60000'], ['Versicherungstage · Vorperiode 1', '30'],
  ]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
  await userEvent.click(screen.getByRole('checkbox'));
}
it('submits explicit prior-period amounts separately from payslips and preserves inputs on failure', async () => {
  api.post.mockRejectedValue({ response: { data: { message: 'Vorperiodenbasis prüfen' } } });
  render(<PayrollOpeningPanel employees={employees} />); await fill();
  await userEvent.click(screen.getByRole('button', { name: 'Eröffnungswerte als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/company/payroll-configuration/openings', expect.objectContaining({ userId: 7, payrollStart: '2026-02-01', confirmed: true,
    periods: [expect.objectContaining({ start: '2026-01-01', end: '2026-01-31', base: '5000', thirteenth: '0', withholding: '400', insuranceDays: 30 })] })));
  expect(await screen.findByRole('alert')).toHaveTextContent('Vorperiodenbasis prüfen');
  expect(screen.getByLabelText('Grundlage der Eröffnungswerte')).toHaveValue('Bestätigte Lohnunterlagen Januar');
});
it('never converts an unknown blank amount to zero and computes the next date range correctly', async () => {
  render(<PayrollOpeningPanel employees={employees} />); await fill();
  fireEvent.change(screen.getByLabelText('Überstundenzuschläge · Vorperiode 1'), { target: { value: '' } });
  await userEvent.click(screen.getByRole('button', { name: 'Eröffnungswerte als neue Version speichern' }));
  expect(api.post).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('button', { name: 'Weitere Vorperiode hinzufügen' }));
  expect(screen.getByLabelText('Beginn Vorperiode 2')).toHaveValue('2026-02-01');
  expect(screen.getByLabelText('Ende Vorperiode 2')).toHaveValue('2026-02-28');
});
it('stages a used opening with its correction batch and explains that approval is still required', async () => {
  api.post.mockResolvedValue({ data: { opening: { id: 12, userId: 7, payrollStart: '2026-02-01', source: 'Reconciled January', active: false }, corrections: [{ id: 19 }] } });
  render(<PayrollOpeningPanel employees={employees} />); await fill();
  await userEvent.selectOptions(screen.getByLabelText('Eröffnungsvorgang'), 'CORRECTION');
  fireEvent.change(screen.getByLabelText('Grund der Eröffnungsberichtigung'), { target: { value: 'January tax reconciled' } });
  fireEvent.change(screen.getByLabelText('Verrechnungsdatum der Eröffnungsberichtigung'), { target: { value: '2026-04-30' } });
  await userEvent.click(screen.getByRole('button', { name: 'Eröffnungsberichtigung mit Korrekturlauf erstellen' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/company/payroll-configuration/openings/corrections', expect.objectContaining({
    opening: expect.objectContaining({ userId: 7, payrollStart: '2026-02-01' }), correction: { reason: 'January tax reconciled', payoutDate: '2026-04-30', replacementOvertimeHours: null },
  })));
  expect(await screen.findByRole('status')).toHaveTextContent('erst mit der vollständigen Freigabe');
  expect(screen.getByText(/Wartet auf vollständige Korrekturfreigabe/)).toBeInTheDocument();
});
