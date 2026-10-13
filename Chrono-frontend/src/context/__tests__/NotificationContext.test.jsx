/** @vitest-environment jsdom */
import React, { useEffect } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

vi.mock('../LanguageContext', () => ({
    useTranslation: () => ({ t: (_key, fallback) => fallback }),
}));

import { NotificationProvider, useNotification } from '../NotificationContext.jsx';

const Consumer = ({ onNotifyIdentity }) => {
    const { notify } = useNotification();
    useEffect(() => {
        onNotifyIdentity(notify);
    }, [notify, onNotifyIdentity]);
    return <button type="button" onClick={() => notify('Netzwerkfehler', 'error')}>Melden</button>;
};

describe('NotificationProvider', () => {
    it('keeps notify stable while toast state changes', async () => {
        const identities = [];
        const rememberIdentity = (notify) => identities.push(notify);
        render(
            <NotificationProvider>
                <Consumer onNotifyIdentity={rememberIdentity} />
            </NotificationProvider>
        );

        await userEvent.click(screen.getByRole('button', { name: 'Melden' }));

        expect(screen.getByText('Netzwerkfehler')).toBeInTheDocument();
        expect(screen.getByText('Netzwerkfehler').closest('.notification-toast')).toHaveClass('error');
        expect(identities).toHaveLength(1);
    });
});
