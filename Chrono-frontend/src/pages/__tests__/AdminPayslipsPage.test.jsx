/** @vitest-environment jsdom */
import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

const translateMock = vi.hoisted(() => vi.fn((key, fallback, options = {}) => (
  String(fallback ?? key).replace(/{{\s*(\w+)\s*}}/g, (match, token) => (
    Object.prototype.hasOwnProperty.call(options, token) ? String(options[token] ?? '') : match
  ))
)));

vi.mock('../../utils/api', () => ({ default: apiMock }));
vi.mock('../../components/Navbar', () => ({ default: () => <div>Navbar</div> }));
vi.mock('../../components/ScheduleAllModal', () => ({ default: () => null }));
vi.mock('../../context/LanguageContext', async () => {
  const { createContext } = await import('react');
  return {
    LanguageContext: createContext({ language: 'de' }),
    useTranslation: () => ({ t: translateMock }),
  };
});

import AdminPayslipsPage from '../AdminPayslipsPage.jsx';

const pendingSlip = {
  id: 11,
  userId: 101,
  firstName: 'Anna',
  lastName: 'Offen',
  periodStart: '2026-07-01',
  periodEnd: '2026-07-31',
  payoutDate: '2026-08-05',
  grossSalary: 5000,
  deductions: 800,
  netSalary: 4200,
  employerContributions: 500,
  approved: false,
  currency: 'CHF',
};

const warningSlip = {
  ...pendingSlip,
  id: 12,
  userId: 102,
  firstName: 'Reto',
  lastName: 'Pruefung',
  payoutDate: '2026-08-04',
  grossSalary: -200,
  netSalary: -150,
};

const approvedSlip = {
  ...pendingSlip,
  id: 13,
  userId: 103,
  firstName: 'Bea',
  lastName: 'Freigegeben',
  payoutDate: '2026-08-03',
  approved: true,
};

const users = [
  { id: 101, firstName: 'Anna', lastName: 'Offen', monthlySalary: 5000 },
  { id: 102, firstName: 'Reto', lastName: 'Pruefung', monthlySalary: 4800 },
  { id: 103, firstName: 'Bea', lastName: 'Freigegeben', monthlySalary: 5100 },
];

const rowFor = (name) => screen.getByText(name).closest('tr');

describe('AdminPayslipsPage', () => {
  it('keeps CHF and EUR totals separate without presenting a currency conversion', async () => {
    apiMock.get.mockImplementation(url => Promise.resolve({ data: url === '/api/payslips/admin/pending'
      ? [{ ...pendingSlip, netSalary: 100, employerContributions: 10 }, { ...warningSlip, grossSalary: 300, netSalary: 200, employerContributions: 20, currency: 'EUR' }]
      : [] }));
    render(<AdminPayslipsPage />);
    const total = screen.getByText('Auszahlung gesamt').closest('article');
    await waitFor(() => expect(total).toHaveTextContent('EUR'));
    expect(total).toHaveTextContent(/CHF\s*100[.,]00/);
    expect(total).toHaveTextContent(/EUR\s*200[.,]00/);
    expect(total).not.toHaveTextContent(/CHF\s*300[.,]00/);
  });

  beforeEach(() => {
    apiMock.get.mockReset();
    apiMock.post.mockReset();
    apiMock.put.mockReset();
    apiMock.delete.mockReset();
    translateMock.mockClear();

    apiMock.get.mockImplementation((url) => {
      if (url === '/api/payslips/admin/pending') {
        return Promise.resolve({ data: [pendingSlip, warningSlip] });
      }
      if (url === '/api/payslips/admin/approved') {
        return Promise.resolve({ data: [approvedSlip] });
      }
      if (url === '/api/admin/users') {
        return Promise.resolve({ data: users });
      }
      if (url === '/api/payslips/admin/time-summary') {
        return Promise.resolve({ data: { currentBalanceMinutes: 1001, workedMinutes: 10770, expectedMinutes: 9600, differenceMinutes: 1170 } });
      }
      return Promise.resolve({ data: [] });
    });
    apiMock.post.mockResolvedValue({ data: {} });
    apiMock.delete.mockResolvedValue({ data: {} });
    vi.spyOn(window, 'confirm').mockClear().mockReturnValue(true);
  });

  it('loads pending and approved payslips, marks warnings, and filters by employee', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    expect(screen.getByText('Reto Pruefung')).toBeInTheDocument();
    expect(screen.getByText('Bea Freigegeben')).toBeInTheDocument();
    expect(screen.getByText('Negativer Nettolohn')).toBeInTheDocument();
    expect(apiMock.get).toHaveBeenCalledWith('/api/payslips/admin/pending');
    expect(apiMock.get).toHaveBeenCalledWith('/api/payslips/admin/approved');
    expect(apiMock.get).toHaveBeenCalledWith('/api/admin/users');

    await user.type(screen.getByPlaceholderText('Name'), 'Anna');

    expect(screen.getByText('Anna Offen')).toBeInTheDocument();
    expect(screen.queryByText('Reto Pruefung')).not.toBeInTheDocument();
    expect(screen.queryByText('Bea Freigegeben')).not.toBeInTheDocument();
  });

  it('reviews signed corrections and approves the entire linked run', async () => {
    const user = userEvent.setup();
    const corrections = [1, 2, 3].map(month => ({ ...pendingSlip, id: 20 + month,
      periodStart: `2026-0${month}-01`, correctionOfId: month, correctionBatch: 'batch-1',
      correctionReason: 'Vertrag berichtigt', grossSalary: -500, netSalary: -400,
      correctedGrossSalary: 4500, correctedNetSalary: 3800, payoutOvertime: true, overtimeHours: -1.5 }));
    apiMock.get.mockImplementation(url => Promise.resolve({ data: url === '/api/payslips/admin/pending' ? corrections : [] }));
    render(<AdminPayslipsPage />);
    await screen.findByText('Differenzbeleg zu #1');
    expect(screen.queryByText('Negativer Nettolohn')).not.toBeInTheDocument();
    await user.click(screen.getByText('Differenzbeleg zu #1').closest('button'));
    const drawer = within(screen.getByRole('dialog'));
    expect(drawer.getByText('Vertrag berichtigt')).toBeInTheDocument();
    expect(drawer.getByText('-1:30 Std:Min.')).toBeInTheDocument();
    await user.click(drawer.getByRole('button', { name: 'Korrekturlauf freigeben (3 Belege)' }));
    await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/payslips/approve-batch', expect.objectContaining({ ids: [21, 22, 23] })));
    expect(apiMock.post.mock.calls.some(([url]) => /approve\/\d/.test(url))).toBe(false);
  });

  it('discards a selected correction run once and never deletes its original', async () => {
    const user = userEvent.setup();
    const corrections = [21, 22].map(id => ({ ...pendingSlip, id, correctionOfId: id - 20, correctionBatch: 'batch-1' }));
    apiMock.get.mockImplementation(url => Promise.resolve({ data: url === '/api/payslips/admin/pending' ? corrections : [] }));
    render(<AdminPayslipsPage />);
    await screen.findByText('Differenzbeleg zu #1');
    for (const checkbox of screen.getAllByRole('checkbox', { name: 'Anna Offen auswaehlen' })) await user.click(checkbox);
    await user.click(screen.getByRole('button', { name: 'Loeschen' }));
    await waitFor(() => expect(apiMock.delete).toHaveBeenCalledExactlyOnceWith('/api/payslips/21/corrections'));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('vollständig verworfen'));
  });

  it('deletes an open payslip after confirmation and refreshes the lists', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    const row = rowFor('Anna Offen');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));
    await user.click(within(row).getByRole('button', { name: /L(?:ö|oe)schen/ }));

    await waitFor(() => expect(apiMock.delete).toHaveBeenCalledWith('/api/payslips/11'));
    await waitFor(() => {
      expect(apiMock.get.mock.calls.filter(([url]) => url === '/api/payslips/admin/pending')).toHaveLength(2);
      expect(apiMock.get.mock.calls.filter(([url]) => url === '/api/payslips/admin/approved')).toHaveLength(2);
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('does not delete when the confirmation is cancelled', async () => {
    const user = userEvent.setup();
    window.confirm.mockReturnValue(false);
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    const row = rowFor('Anna Offen');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));
    await user.click(within(row).getByRole('button', { name: /L(?:ö|oe)schen/ }));

    expect(apiMock.delete).not.toHaveBeenCalled();
  });

  it('catches a 409 delete response and preserves approved documents', async () => {
    const user = userEvent.setup();
    apiMock.delete.mockRejectedValue({ response: { status: 409 } });
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    const row = rowFor('Anna Offen');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));
    await user.click(within(row).getByRole('button', { name: /L(?:ö|oe)schen/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Freigegebene Abrechnungen bleiben erhalten.*Korrekturbeleg/);
  });

  it('offers no reopening deletion or payment-date change for an approved payslip', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Bea Freigegeben');
    const row = rowFor('Bea Freigegeben');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));

    expect(within(row).queryByRole('button', { name: /L(?:ö|oe)schen/ })).not.toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /Zur(?:ü|ue)ckziehen/ })).not.toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: /Auszahlung aendern/ })).not.toBeInTheDocument();
    expect(apiMock.post).not.toHaveBeenCalled();
    expect(apiMock.delete).not.toHaveBeenCalled();
  });

  it('blocks bulk deletion when the selection contains an approved payslip', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(within(rowFor('Anna Offen')).getByRole('checkbox'));
    await user.click(within(rowFor('Bea Freigegeben')).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /L(?:ö|oe)schen/ }));

    expect(apiMock.delete).not.toHaveBeenCalled();
    expect(window.confirm).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toHaveTextContent(/separaten Korrekturbeleg/);
  });

  it('creates a new payslip with the entered period and employee', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));

    const dialog = screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.selectOptions(within(dialog).getByLabelText('Mitarbeiter'), '101');
    await user.type(within(dialog).getByLabelText('Startdatum'), '2026-08-01');
    await user.type(within(dialog).getByLabelText('Enddatum'), '2026-08-31');
    await user.type(within(dialog).getByLabelText('Auszahlung'), '2026-09-05');
    await user.click(within(dialog).getByRole('button', { name: 'Abrechnung erstellen' }));

    await waitFor(() => {
      expect(apiMock.post).toHaveBeenCalledWith('/api/payslips/generate', null, {
        params: {
          userId: '101',
          start: '2026-08-01',
          end: '2026-08-31',
          payoutDate: '2026-09-05',
          payoutOvertime: false,
          overtimeHours: null,
        },
      });
    });
  });

  it('converts overtime entered as hours and minutes before creating the payslip', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));

    const dialog = screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' });
    await user.selectOptions(within(dialog).getByLabelText('Mitarbeiter'), '101');
    await user.type(within(dialog).getByLabelText('Startdatum'), '2026-08-01');
    await user.type(within(dialog).getByLabelText('Enddatum'), '2026-08-31');
    await user.click(within(dialog).getByLabelText('Ueberstunden auszahlen'));
    await user.type(within(dialog).getByLabelText('Ueberstunden (Std:Min.)'), '16:41');
    await user.click(within(dialog).getByRole('button', { name: 'Abrechnung erstellen' }));

    await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(
      '/api/payslips/generate',
      null,
      expect.objectContaining({
        params: expect.objectContaining({
          payoutOvertime: true,
          overtimeHours: 1001 / 60,
        }),
      })
    ));
  });

  it('rejects an overtime entry whose minute part is invalid', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));

    const dialog = screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' });
    await user.selectOptions(within(dialog).getByLabelText('Mitarbeiter'), '101');
    await user.type(within(dialog).getByLabelText('Startdatum'), '2026-08-01');
    await user.type(within(dialog).getByLabelText('Enddatum'), '2026-08-31');
    await user.click(within(dialog).getByLabelText('Ueberstunden auszahlen'));
    await user.type(within(dialog).getByLabelText('Ueberstunden (Std:Min.)'), '16:75');
    await user.click(within(dialog).getByRole('button', { name: 'Abrechnung erstellen' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Stunden:Minuten/);
    expect(apiMock.post).not.toHaveBeenCalledWith('/api/payslips/generate', null, expect.anything());
  });

  it('shows the backend validation when an hourly rate is missing', async () => {
    const user = userEvent.setup();
    apiMock.post.mockRejectedValueOnce({
      response: {
        data: {
          message: 'Für Stundenlohn-Mitarbeitende muss ein positiver Stundenansatz hinterlegt sein.',
        },
      },
    });
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));

    const dialog = screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' });
    await user.selectOptions(within(dialog).getByLabelText('Mitarbeiter'), '101');
    await user.type(within(dialog).getByLabelText('Startdatum'), '2026-08-01');
    await user.type(within(dialog).getByLabelText('Enddatum'), '2026-08-31');
    await user.click(within(dialog).getByRole('button', { name: 'Abrechnung erstellen' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/positiver Stundenansatz/);
  });

  it('shows current and period hours inline and preserves manual edits when dates change', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);
    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));
    const section = within(screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' }));
    await user.selectOptions(section.getByLabelText('Mitarbeiter'), '101');
    await user.click(section.getByLabelText('Ueberstunden auszahlen'));
    expect(await section.findByText('16:41 Std:Min.')).toBeInTheDocument();
    expect(apiMock.get).toHaveBeenCalledWith('/api/payslips/admin/time-summary', { params: { userId: '101' } });
    await user.click(section.getByRole('button', { name: 'Aktuellen Saldo übernehmen' }));
    const input = section.getByLabelText('Ueberstunden (Std:Min.)');
    expect(input).toHaveValue('16:41');
    await user.clear(input);
    await user.type(input, '5:30');
    fireEvent.change(section.getByLabelText('Startdatum'), { target: { value: '2026-08-01' } });
    fireEvent.change(section.getByLabelText('Enddatum'), { target: { value: '2026-08-31' } });
    expect(await section.findByText('19:30 Std:Min.')).toBeInTheDocument();
    expect(section.getByText('179:30 Std:Min.')).toBeInTheDocument();
    expect(section.getByText('160:00 Std:Min.')).toBeInTheDocument();
    expect(apiMock.get).toHaveBeenCalledWith('/api/payslips/admin/time-summary', {
      params: { userId: '101', start: '2026-08-01', end: '2026-08-31' },
    });
    expect(input).toHaveValue('5:30');
    await user.click(section.getByRole('button', { name: 'Zeitraumwert übernehmen' }));
    expect(input).toHaveValue('19:30');
    await user.selectOptions(section.getByLabelText('Mitarbeiter'), '102');
    expect(input).toHaveValue('');
    await user.click(section.getByRole('button', { name: 'Abbrechen' }));
    expect(screen.queryByRole('region', { name: 'Neue Lohnabrechnung erstellen' })).not.toBeInTheDocument();
  });

  it('keeps manual entry available when loading hours fails', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);
    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));
    const section = within(screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' }));
    await user.selectOptions(section.getByLabelText('Mitarbeiter'), '101');
    apiMock.get.mockRejectedValueOnce(new Error('offline'));
    await user.click(section.getByLabelText('Ueberstunden auszahlen'));
    expect(await section.findByRole('alert')).toHaveTextContent(/manuelle Eingabe/);
    await user.type(section.getByLabelText('Ueberstunden (Std:Min.)'), '3:15');
    expect(section.getByLabelText('Ueberstunden (Std:Min.)')).toHaveValue('3:15');
  });

  it('ignores stale requests after changing employees and shows zero and negative balances', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);
    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neue Abrechnung' }));
    const section = within(screen.getByRole('region', { name: 'Neue Lohnabrechnung erstellen' }));
    await user.selectOptions(section.getByLabelText('Mitarbeiter'), '101');
    fireEvent.change(section.getByLabelText('Startdatum'), { target: { value: '2026-08-01' } });
    fireEvent.change(section.getByLabelText('Enddatum'), { target: { value: '2026-08-31' } });
    let resolveOld;
    apiMock.get.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
    await user.click(section.getByLabelText('Ueberstunden auszahlen'));
    apiMock.get.mockResolvedValueOnce({ data: { currentBalanceMinutes: -90, differenceMinutes: 0, workedMinutes: 9600, expectedMinutes: 9600 } });
    await user.selectOptions(section.getByLabelText('Mitarbeiter'), '102');
    expect(await section.findByText('-1:30 Std:Min.')).toBeInTheDocument();
    expect(section.getByText('0:00 Std:Min.')).toBeInTheDocument();
    await act(async () => resolveOld({ data: { currentBalanceMinutes: 9999 } }));
    expect(section.getByText('-1:30 Std:Min.')).toBeInTheDocument();
    expect(section.queryByText('166:39 Std:Min.')).not.toBeInTheDocument();
    expect(section.getByRole('button', { name: 'Aktuellen Saldo übernehmen' })).toBeDisabled();
    expect(section.getByRole('button', { name: 'Zeitraumwert übernehmen' })).toBeDisabled();
  });
});
