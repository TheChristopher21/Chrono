import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import EditTimeModal from '../EditTimeModal';

describe('EditTimeModal saving', () => {
    it('blocks repeated writes and closing while the server is still saving', async () => {
        let finish;
        const onSubmit = vi.fn(() => new Promise(resolve => { finish = resolve; }));
        const onClose = vi.fn();
        render(<EditTimeModal t={(_key, fallback) => fallback} isVisible
            targetDate={new Date(2026, 8, 21)} targetUsername="alice" dayEntries={[]} users={[]}
            onSubmit={onSubmit} onClose={onClose} />);
        const form = screen.getByRole('button', { name: 'Speichern' }).closest('form');
        fireEvent.submit(form);
        fireEvent.submit(form);
        expect(onSubmit).toHaveBeenCalledTimes(1);
        expect(screen.getByRole('button', { name: 'Wird gespeichert …' })).toBeDisabled();
        const cancel = screen.getByRole('button', { name: /Abbrechen/ });
        expect(cancel).toBeDisabled();
        fireEvent.click(cancel);
        expect(onClose).not.toHaveBeenCalled();
        await act(async () => finish());
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
    });
});
