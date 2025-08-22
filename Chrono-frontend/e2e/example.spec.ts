import { test, expect } from '@playwright/test';

test('Homepage hat den korrekten Titel', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch');
    await expect(page.getByRole('link', { name: 'Chrono' })).toBeVisible(); // Logo/Brand in der Navbar
});

test('Loginseite zeigt Loginformular', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/login');
    await expect(page.getByLabel('Benutzername')).toBeVisible();
    await expect(page.getByLabel('Passwort')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
});

test('Registrierungsseite zeigt Formularfelder', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/register');
    await expect(page.getByPlaceholder('Firmenname')).toBeVisible();
    await expect(page.getByPlaceholder(/Ansprechpartner/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Angebotsanfrage senden/i })).toBeVisible();
});

test('Navbar Links navigieren zu Login und Registrierung', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch');
    await page.getByRole('link', { name: /Login/i }).click();
    await expect(page).toHaveURL('https://chrono-logisch.ch/login');
    await page.getByRole('link', { name: /Registrier|Register/i }).click();
    await expect(page).toHaveURL('https://chrono-logisch.ch/register');
});

test('Registrierungsseite verlinkt zurück zur Loginseite', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/register');
    await page.getByRole('link', { name: /Login|Anmelden/i }).click();
    await expect(page).toHaveURL('https://chrono-logisch.ch/login');
});

test('Theme Toggle wechselt zwischen Light und Dark Mode', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch');
    const html = page.locator('html');
    const initialTheme = await html.getAttribute('data-theme');
    await page.getByRole('button', { name: /Dark Mode|Light Mode/ }).click();
    await expect(html).not.toHaveAttribute('data-theme', initialTheme || '');
});

test('Impressumseite wird korrekt angezeigt', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/impressum');
    await expect(page.getByRole('heading', { name: 'Impressum' })).toBeVisible();
});

test('Datenschutzseite wird korrekt angezeigt', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/datenschutz');
    await expect(page.getByRole('heading', { name: 'Datenschutz' })).toBeVisible();
});

test('AGB-Seite wird korrekt angezeigt', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/agb');
    await expect(page.getByRole('heading', { name: /Allgemeine Geschäftsbedingungen/ })).toBeVisible();
});

test('Unbekannte Route leitet auf die Startseite um', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch/unbekannt');
    await expect(page).toHaveURL('https://chrono-logisch.ch/');
    await expect(page.getByRole('link', { name: 'Chrono' })).toBeVisible();
});

const privateRoutes = [
    '/dashboard',
    '/percentage-punch',
    '/personal-data',
    '/payslips',
    '/admin/dashboard',
    '/admin/users',
    '/admin/change-password',
    '/admin/customers',
    '/admin/projects',
    '/admin/tasks',
    '/admin/project-report',
    '/admin/company',
    '/admin/payslips',
    '/admin/schedule',
    '/admin/print-schedule',
    '/admin/knowledge',
    '/admin/company-settings',
    '/admin/shift-rules',
    '/admin/import-times',
    '/whats-new'
];

for (const route of privateRoutes) {
    test(`Nicht eingeloggte Nutzer werden von ${route} auf die Loginseite umgeleitet`, async ({ page }) => {
        await page.goto(`https://chrono-logisch.ch${route}`);
        await expect(page).toHaveURL(/https:\/\/chrono-logisch\.ch\/login/);
    });
}
