/** @vitest-environment jsdom */
import React, { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import PmsGuestProfileDetails, { profileDetails, profilePayload, preferredProfileEmail } from './PmsGuestProfileDetails.jsx';
import { OrganizationProfileDetails } from './PmsOrganizationDetails.jsx';

describe('PMS linked profile details', () => {
    it('uses an explicitly preferred email before the business, private or additional fallback', () => {
        const profile = { email: 'preferred@example.test', businessEmail: 'work@example.test', privateEmail: 'home@example.test', additionalEmails: ['extra@example.test'] };
        expect(preferredProfileEmail(profile)).toBe('preferred@example.test');
        expect(preferredProfileEmail({ ...profile, email: '' })).toBe('work@example.test');
        expect(preferredProfileEmail({ ...profile, email: '', businessEmail: '' })).toBe('home@example.test');
        expect(profilePayload({ additionalEmails: ['extra@example.test'] }).email).toBe('extra@example.test');
    });
    it('selects only contacts in the linked company and preserves employee billing overrides', async () => {
        const user = userEvent.setup();
        function Editor() {
            const [value, setValue] = useState({ ...profileDetails(), organizationId: 7 });
            return <><PmsGuestProfileDetails value={value} onChange={setValue} organizations={[
                { id: 7, contacts: [{ id: 'own', name: 'Accounts payable' }] },
                { id: 9, contacts: [{ id: 'foreign', name: 'Other company' }] },
            ]} /><output data-testid="profile">{JSON.stringify(profilePayload(value))}</output></>;
        }
        render(<Editor />);
        expect(screen.queryByRole('option', { name: 'Other company' })).toBeNull();
        await user.selectOptions(screen.getByLabelText('Firmen-Ansprechpartner'), 'own');
        await user.type(screen.getByLabelText('Ernährung, Allergien und Unverträglichkeiten'), 'Laktosefrei');
        await user.click(screen.getByLabelText('Individuelle Rechnungsangaben für diesen Gast / Firmenmitarbeiter'));
        await user.type(screen.getByLabelText('Kostenstelle'), 'SALES-42');
        const result = JSON.parse(screen.getByTestId('profile').textContent);
        expect(result.organizationContactId).toBe('own');
        expect(result.dietaryNotes).toBe('Laktosefrei');
        expect(result.billingOverride).toBe(true);
        expect(result.billingProfile.costCenter).toBe('SALES-42');
    });

    it('links an existing guest as a company contact and fills business email', async () => {
        const user = userEvent.setup();
        function Editor() {
            const [value, setValue] = useState({ id: 7, contacts: [] });
            return <><OrganizationProfileDetails value={value} onChange={setValue} guests={[
                { id: 3, firstName: 'Nina', lastName: 'Meier', businessEmail: 'nina@company.test', organizationId: 7 },
                { id: 4, firstName: 'Other', lastName: 'Employee', organizationId: 99 },
            ]} /><output data-testid="organization">{JSON.stringify(value)}</output></>;
        }
        render(<Editor />);
        await user.click(screen.getByRole('button', { name: 'Ansprechpartner hinzufügen' }));
        expect(screen.queryByRole('option', { name: 'Other Employee' })).toBeNull();
        await user.selectOptions(screen.getByLabelText('Mit Gästekartei verknüpfen'), '3');
        const result = JSON.parse(screen.getByTestId('organization').textContent);
        expect(result.contacts[0]).toMatchObject({ linkedGuestId: 3, name: 'Nina Meier', email: 'nina@company.test', primaryContact: true });
    });
});
