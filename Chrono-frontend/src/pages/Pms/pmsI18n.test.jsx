/** @vitest-environment jsdom */
import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { LanguageProvider } from '../../context/LanguageContext.jsx';
import { PmsLanguageSwitch, PmsTranslationBoundary } from './pmsI18n.jsx';

describe('PMS language support', () => {
    it('switches rendered PMS copy and accessible labels between German and English', async () => {
        render(
            <LanguageProvider>
                <PmsLanguageSwitch />
                <PmsTranslationBoundary>
                    <section aria-label="PMS-Arbeitsbereiche">
                        <h1>Übersicht</h1>
                        <input aria-label="Gast suchen" placeholder="Name, E-Mail oder Telefon" />
                        <button type="button">Neue Reservierung</button>
                    </section>
                </PmsTranslationBoundary>
            </LanguageProvider>,
        );

        expect(screen.getByRole('heading', { name: 'Übersicht' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'EN' }));

        expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'New reservation' })).toBeInTheDocument();
        expect(screen.getByLabelText('Search guest')).toHaveAttribute('placeholder', 'Name, email or phone');
    });
});

