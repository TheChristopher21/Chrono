import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayrollCorrectionForm from '../PayrollCorrectionForm';
import api from '../../utils/api';
vi.mock('../../utils/api', () => ({ default: { post: vi.fn() } }));
beforeEach(() => vi.resetAllMocks());
const original = { id: 31, periodStart: '2026-01-01', periodEnd: '2026-01-31' };
describe('payroll corrections', () => {
  it('creates drafts with a reason and explicit zero minutes without changing the original', async () => {
    const created = vi.fn(); api.post.mockResolvedValue({ data: [{ id: 41, correctionOfId: 31 }] });
    render(<PayrollCorrectionForm original={original} onClose={vi.fn()} onCreated={created} />);
    fireEvent.change(screen.getByLabelText('Korrekturgrund'), { target: { value: 'Überstundenauszahlung aufheben' } });
    fireEvent.change(screen.getByLabelText('Auszahlungs-/Verrechnungsdatum der Differenz'), { target: { value: '2026-10-16' } });
    fireEvent.change(screen.getByLabelText(/Neue gesamte Überstundenauszahlung/), { target: { value: '0' } });
    await userEvent.click(screen.getByRole('button', { name: 'Korrekturlauf als Entwurf erstellen' }));
    await waitFor(() => expect(created).toHaveBeenCalledWith([{ id: 41, correctionOfId: 31 }]));
    expect(api.post).toHaveBeenCalledWith('/api/payslips/31/corrections', { reason: 'Überstundenauszahlung aufheben', payoutDate: '2026-10-16', replacementOvertimeHours: 0 });
    expect(original.id).toBe(31);
  });
  it('keeps inputs and explains missing yearly evidence after a rejected calculation', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Jahresgrundlagen fehlen' } } });
    render(<PayrollCorrectionForm original={original} onClose={vi.fn()} onCreated={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Korrekturgrund'), { target: { value: 'Steuertarif korrigieren' } });
    fireEvent.change(screen.getByLabelText('Auszahlungs-/Verrechnungsdatum der Differenz'), { target: { value: '2026-10-16' } });
    await userEvent.click(screen.getByRole('button', { name: 'Korrekturlauf als Entwurf erstellen' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Jahresgrundlagen fehlen');
    expect(screen.getByLabelText('Korrekturgrund')).toHaveValue('Steuertarif korrigieren');
  });
});
