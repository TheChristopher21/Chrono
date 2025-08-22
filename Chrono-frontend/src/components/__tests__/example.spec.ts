import { test, expect } from '@playwright/test';

test('Homepage hat den korrekten Titel', async ({ page }) => {
    await page.goto('https://deine-url.de');
    await expect(page).toHaveTitle(/Beispiel/i);
});
