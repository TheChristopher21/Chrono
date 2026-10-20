/** @vitest-environment jsdom */
import { render, screen, fireEvent } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../utils/api', () => ({ default: api }));
import PayrollReview from '../PayrollReview';
const t = (key, fallback) => fallback;
const props = { userId: 1, start: '2025-09-01', end: '2025-09-30', t };
const data = { workedMinutes: 480, expectedMinutes: 450, differenceMinutes: 30, taxCalculationVerified: false,
  issues: [{ code: 'INSURANCE_RATES_MISSING', severity: 'BLOCKER', message: 'Versicherungssätze fehlen.' }],
  days: [{ date: '2025-09-01', workedMinutes: 480, expectedMinutes: 450, differenceMinutes: 30 }] };

describe('PayrollReview', () => {
  beforeEach(() => { api.get.mockReset(); });
  it('shows server time evidence and missing data without a fabricated net salary', async () => {
    api.get.mockResolvedValue({ data }); render(<PayrollReview {...props} />);
    expect(await screen.findByText('Versicherungssätze fehlen.')).toBeInTheDocument();
    expect(screen.getByText('Noch nicht verfügbar')).toBeInTheDocument();
    expect(screen.queryByText('4’300.00')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Tageswerte nachvollziehen'));
    expect(screen.getByText('2025-09-01')).toBeVisible();
    expect(api.get).toHaveBeenCalledWith('/api/payslips/admin/review', { params: { userId: 1, start: props.start, end: props.end } });
  });
  it('invalidates the previous result immediately when the employee changes', async () => {
    api.get.mockResolvedValueOnce({ data }).mockImplementationOnce(() => new Promise(() => {}));
    const { rerender } = render(<PayrollReview {...props} />);
    await screen.findByText('Versicherungssätze fehlen.');
    rerender(<PayrollReview {...props} userId={2} />);
    expect(screen.queryByText('Versicherungssätze fehlen.')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
  it('reports failures instead of showing zero or estimated amounts', async () => {
    api.get.mockRejectedValue(new Error('network')); render(<PayrollReview {...props} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('kein geschätzter Nettolohn');
  });
  it('does not request an incomplete period', () => {
    render(<PayrollReview {...props} end="" />); expect(api.get).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});
