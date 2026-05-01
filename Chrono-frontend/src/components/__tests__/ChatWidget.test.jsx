/** @vitest-environment jsdom */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../utils/api', () => ({
    default: {
        post: vi.fn(),
    },
}));

vi.mock('../../context/AuthContext', () => ({
    useAuth: () => ({
        currentUser: { username: 'Alice' },
    }),
}));

import ChatWidget from '../../components/ChatWidget.jsx';

describe('ChatWidget', () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    afterEach(() => {
        sessionStorage.clear();
        vi.clearAllMocks();
    });

    it('renders links without injecting raw html', () => {
        sessionStorage.setItem(
            'chatMessages',
            JSON.stringify([
                { sender: 'user', text: 'Hello' },
                { sender: 'bot', text: 'Hi <img src=x onerror=alert(1)> [Docs](https://example.com)' },
            ])
        );

        render(<ChatWidget />);

        expect(document.querySelector('.msg-bubble img')).toBeNull();
        expect(document.body.textContent).toContain('<img src=x onerror=alert(1)>');

        const link = screen.getByRole('link', { name: 'Docs' });
        expect(link).toHaveAttribute('href', 'https://example.com');
    });

    it('keeps unsafe javascript links as plain text', () => {
        sessionStorage.setItem(
            'chatMessages',
            JSON.stringify([
                { sender: 'user', text: 'Hello' },
                { sender: 'bot', text: '[Click](javascript:alert(1))' },
            ])
        );

        render(<ChatWidget />);

        expect(screen.queryByRole('link', { name: 'Click' })).toBeNull();
        expect(document.body.textContent).toContain('[Click](javascript:alert(1))');
    });
});
