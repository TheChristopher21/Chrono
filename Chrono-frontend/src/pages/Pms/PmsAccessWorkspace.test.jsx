/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import PmsAccessWorkspace from './PmsAccessWorkspace.jsx';

const worker = { userId: 7, username: 'ada', displayName: 'Ada Lovelace', master: false,
    grants: [{ propertyId: 1, permissions: { FRONT_DESK: 'VIEW' } }] };
const data = { users: [worker], properties: [{ propertyId: 1, propertyName: 'Hotel Alpha' }],
    permissionKeys: ['FRONT_DESK'], page: 0, size: 50, totalElements: 120, hasNext: true };
beforeEach(() => { vi.clearAllMocks(); api.get.mockResolvedValue({ data }); });
afterEach(cleanup);

it('requests bounded server pages and resets the page when searching', async () => {
    render(<PmsAccessWorkspace />);
    await screen.findByRole('option', { name: 'Ada Lovelace' });
    expect(api.get).toHaveBeenLastCalledWith('/api/pms/access/users', expect.objectContaining({ params: { page: 0, size: 50, query: '' } }));
    fireEvent.click(screen.getByRole('button', { name: 'Mitarbeiter weiter' }));
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/api/pms/access/users', expect.objectContaining({ params: { page: 1, size: 50, query: '' } })));
    fireEvent.change(screen.getByLabelText('Mitarbeiter suchen'), { target: { value: 'Ada' } });
    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith('/api/pms/access/users', expect.objectContaining({ params: { page: 0, size: 50, query: 'Ada' } })));
});

it('keeps an unsaved permission draft on rejected save and allows deliberate discard', async () => {
    api.put.mockRejectedValue({ response: { data: { detail: 'Speichern fehlgeschlagen.' } } });
    render(<PmsAccessWorkspace />);
    await screen.findByRole('option', { name: 'Ada Lovelace' });
    fireEvent.change(screen.getByLabelText('Mitarbeiter'), { target: { value: '7' } });
    fireEvent.change(screen.getByLabelText('Hotel Alpha: Rezeption'), { target: { value: 'MANAGE' } });
    expect(screen.getByLabelText('Mitarbeiter suchen').disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Mitarbeiter weiter' }).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Hotelrechte speichern' }));
    await screen.findByText('Speichern fehlgeschlagen.');
    expect(screen.getByLabelText('Hotel Alpha: Rezeption').value).toBe('MANAGE');
    fireEvent.click(screen.getByRole('button', { name: 'Änderungen verwerfen' }));
    expect(screen.getByLabelText('Hotel Alpha: Rezeption').value).toBe('VIEW');
    expect(screen.getByLabelText('Mitarbeiter suchen').disabled).toBe(false);
});

it('replaces the saved draft with the acknowledged grants before changing pages', async () => {
    api.put.mockResolvedValue({ data: { ...worker, grants: [{ propertyId: 1, permissions: { FRONT_DESK: 'MANAGE' } }] } });
    render(<PmsAccessWorkspace />);
    await screen.findByRole('option', { name: 'Ada Lovelace' });
    fireEvent.change(screen.getByLabelText('Mitarbeiter'), { target: { value: '7' } });
    fireEvent.change(screen.getByLabelText('Hotel Alpha: Rezeption'), { target: { value: 'MANAGE' } });
    fireEvent.click(screen.getByRole('button', { name: 'Hotelrechte speichern' }));
    await screen.findByText(/Hotelrechte gespeichert/);
    expect(api.put).toHaveBeenCalledWith('/api/pms/access/users/7', { grants: [{ propertyId: 1, permissions: { FRONT_DESK: 'MANAGE' } }] });
    expect(screen.getByRole('button', { name: 'Hotelrechte speichern' }).disabled).toBe(true);
    expect(screen.getByLabelText('Mitarbeiter suchen').disabled).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: 'Mitarbeiter weiter' }));
    expect(screen.queryByLabelText('Hotel Alpha: Rezeption')).toBeNull();
});
