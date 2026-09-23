/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, act } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import PmsDirectoryPicker, { PmsDirectoryBrowser } from './PmsDirectoryPicker.jsx';
import { PmsTranslationBoundary } from './pmsI18n.jsx';
const first = { id: 1, name: 'Initial company', active: true };
const distant = { id: 901, name: 'Company beyond first page', referenceCode: 'FK000901', active: true, contacts: [{ id: 'contact-1', name: 'Accounting' }] };
beforeEach(() => vi.clearAllMocks()); afterEach(cleanup);
it('searches the whole directory and lets a remote result be selected with its contact context', async () => {
    api.get.mockResolvedValue({ data: { items: [distant], page: 0, totalElements: 1, hasNext: false } });
    const change = vi.fn(); const resolved = vi.fn();
    render(<PmsDirectoryPicker propertyId={7} label="Firma" value="" onChange={change} onResolve={resolved} initialOptions={[first]} />);
    expect(api.get).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Firma suchen'), { target: { value: 'beyond' } }); fireEvent.click(screen.getByRole('button', { name: 'Suchen' }));
    await screen.findByRole('option', { name: 'FK000901 · Company beyond first page' });
    expect(api.get).toHaveBeenCalledWith('/api/pms/properties/7/directory/organizations', { params: { page: 0, size: 25, query: 'beyond', activeOnly: true, masterOnly: false } });
    fireEvent.change(screen.getByLabelText('Firma'), { target: { value: '901' } });
    expect(change).toHaveBeenCalledWith('901', distant); expect(resolved).toHaveBeenCalledWith(distant);
});
it('hydrates a previously saved selection outside the initial page', async () => {
    api.get.mockResolvedValue({ data: distant });
    render(<PmsDirectoryPicker propertyId={7} label="Firma" value="901" onChange={vi.fn()} initialOptions={[first]} />);
    await screen.findByRole('option', { name: 'FK000901 · Company beyond first page' });
    expect(screen.getByLabelText('Firma')).toHaveValue('901'); expect(api.get).toHaveBeenCalledTimes(1);
});
it('does not replace a new hotel selection with a delayed previous hotel response', async () => {
    let old; api.get.mockImplementation((url) => url.includes('/7/') ? new Promise((resolve) => { old = resolve; }) : Promise.resolve({ data: { id: 902, name: 'New hotel company' } }));
    const view = render(<PmsDirectoryPicker propertyId={7} label="Firma" value="901" onChange={vi.fn()} />);
    view.rerender(<PmsDirectoryPicker propertyId={8} label="Firma" value="902" onChange={vi.fn()} />);
    await screen.findByRole('option', { name: 'New hotel company' });
    await act(async () => old({ data: distant }));
    expect(screen.queryByRole('option', { name: 'FK000901 · Company beyond first page' })).toBeNull(); expect(screen.getByLabelText('Firma')).toHaveValue('902');
});
it('loads the next directory browser page with SQL page parameters', async () => {
    api.get.mockResolvedValueOnce({ data: { items: [first], totalElements: 26, hasNext: true } }).mockResolvedValueOnce({ data: { items: [distant], totalElements: 26, hasNext: false } });
    render(<PmsDirectoryBrowser propertyId={7} kind="organizations">{(entries) => <ul>{entries.map((entry) => <li key={entry.id}>{entry.name}</li>)}</ul>}</PmsDirectoryBrowser>);
    await screen.findByText('Initial company'); fireEvent.click(screen.getByRole('button', { name: 'Nächste Verzeichnisseite' }));
    await screen.findByText('Company beyond first page'); expect(screen.queryByText('Initial company')).toBeNull();
    await waitFor(() => expect(api.get.mock.calls[1][1].params.page).toBe(1));
});
it('keeps the directory renderer callable inside the real PMS translation boundary', async () => {
    api.get.mockResolvedValue({ data: { items: [first], totalElements: 1, hasNext: false } });
    render(<PmsTranslationBoundary><PmsDirectoryBrowser propertyId={7} kind="organizations" renderItems={(entries) => <ul>{entries.map((entry) => <li key={entry.id}>{entry.name}</li>)}</ul>} /></PmsTranslationBoundary>);
    expect(await screen.findByText('Initial company')).toBeTruthy();
});
