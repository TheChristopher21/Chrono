import { test, expect } from '@playwright/test';

test('Homepage hat den korrekten Titel', async ({ page }) => {
    await page.goto('https://chrono-logisch.ch');
    await expect(page.getByRole('link', { name: 'Chrono' })).toBeVisible(); // Logo/Brand in der Navbar
});
