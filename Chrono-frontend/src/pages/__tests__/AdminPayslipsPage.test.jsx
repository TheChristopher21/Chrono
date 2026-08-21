/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
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

  it('catches a 409 delete response and explains the required reopen step', async () => {
    const user = userEvent.setup();
    apiMock.delete.mockRejectedValue({ response: { status: 409 } });
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    const row = rowFor('Anna Offen');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));
    await user.click(within(row).getByRole('button', { name: /L(?:ö|oe)schen/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Freigegebene Abrechnungen k(?:ö|oe)nnen nicht gel(?:ö|oe)scht werden.*zuerst zur(?:ü|ue)ckziehen/);
  });

  it('offers reopen instead of delete for an approved payslip', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Bea Freigegeben');
    const row = rowFor('Bea Freigegeben');
    await user.click(within(row).getByRole('button', { name: 'Weitere Aktionen' }));

    expect(within(row).queryByRole('button', { name: /L(?:ö|oe)schen/ })).not.toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: /Zur(?:ü|ue)ckziehen/ }));

    await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/payslips/reopen/13'));
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
    expect(await screen.findByRole('alert')).toHaveTextContent(/Bitte zuerst zur(?:ü|ue)ckziehen/);
  });

  it('creates a new payslip with the entered period and employee', async () => {
    const user = userEvent.setup();
    render(<AdminPayslipsPage />);

    await screen.findByText('Anna Offen');
    await user.click(screen.getByRole('button', { name: '+ Neuer Abrechnungslauf' }));

    const dialog = screen.getByRole('dialog', { name: 'Neuen Abrechnungslauf erstellen' });
    await user.selectOptions(within(dialog).getByLabelText('Mitarbeiter'), '101');
    await user.type(within(dialog).getByLabelText('Startdatum'), '2026-08-01');
    await user.type(within(dialog).getByLabelText('Enddatum'), '2026-08-31');
    await user.type(within(dialog).getByLabelText('Auszahlung'), '2026-09-05');
    await user.click(within(dialog).getByRole('button', { name: 'Abrechnungslauf erstellen' }));

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
});
