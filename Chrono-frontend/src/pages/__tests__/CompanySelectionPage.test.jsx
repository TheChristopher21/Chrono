// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ user: {}, get: vi.fn(), open: vi.fn(), leave: vi.fn() }));
vi.mock('../../context/AuthContext.jsx', () => ({ useAuth: () => ({ currentUser: mocks.user }) }));
vi.mock('../../context/LanguageContext.jsx', () => ({ useTranslation: () => ({ t: (_key, fallback) => fallback }) }));
vi.mock('../../components/Navbar.jsx', () => ({ default: () => null }));
vi.mock('../../utils/api.js', () => ({ default: { get: mocks.get } }));
vi.mock('../../utils/companyScope.js', () => ({ openCompanyScope: mocks.open, leaveCompanyScope: mocks.leave }));
import CompanySelectionPage, { CompanyScopeGate } from '../CompanySelectionPage.jsx';
beforeEach(() => {
    mocks.user = { username: 'root', roles: ['ROLE_SUPERADMIN'] };
    mocks.get.mockReset().mockResolvedValue({ data: [{ id: 20, name: 'Atelier', city: 'Zürich', userCount: 2 }, { id: 30, name: 'Beratung', city: 'Bern', userCount: 4 }] });
    mocks.open.mockClear();
});
describe('company selection', () => {
    it('finds a company and opens exactly the selected one', async () => {
        render(<MemoryRouter><CompanySelectionPage /></MemoryRouter>);
        await screen.findByRole('button', { name: /Atelier/ });
        fireEvent.change(screen.getByRole('searchbox', { name: 'Firma suchen' }), { target: { value: 'Bern' } });
        expect(screen.queryByRole('button', { name: /Atelier/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Beratung/ }));
        expect(mocks.open).toHaveBeenCalledWith(expect.objectContaining({ id: 30 }), mocks.user);
    });
    it('shows a retry after an error and never renders an unscoped dashboard', async () => {
        mocks.get.mockRejectedValueOnce(new Error('offline'));
        render(<MemoryRouter initialEntries={['/dashboard']}><Routes>
            <Route path="/dashboard" element={<CompanyScopeGate><p>Company data</p></CompanyScopeGate>} />
            <Route path="/admin/firmenauswahl" element={<CompanySelectionPage />} />
        </Routes></MemoryRouter>);
        await screen.findByRole('alert');
        expect(screen.queryByText('Company data')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        await screen.findByRole('button', { name: /Atelier/ });
        await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2));
    });
});
