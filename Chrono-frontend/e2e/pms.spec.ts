import { expect, test, type Page } from '@playwright/test';

const password = process.env.PMS_E2E_PASSWORD || 'chrono-pms-e2e-only';

const waitForPmsSeed = async (page: Page) => {
    let token = '';
    await expect.poll(async () => {
        const response = await page.request.post('http://127.0.0.1:18084/api/auth/login', {
            data: { username: 'Christopher', password },
        });
        if (!response.ok()) return response.status();
        token = (await response.json()).token;
        return response.status();
    }, { timeout: 30_000, intervals: [250, 500, 1_000] }).toBe(200);

    await expect.poll(async () => {
        const response = await page.request.get('http://127.0.0.1:18084/api/pms/setup', {
            headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok()) return false;
        const setup = await response.json();
        return setup.properties?.some((property) => property.code === 'DEMO') ?? false;
    }, { timeout: 30_000, intervals: [250, 500, 1_000] }).toBe(true);
};

const login = async (page: Page) => {
    await waitForPmsSeed(page);
    await page.goto('/login');
    await page.getByLabel('Benutzername').fill('Christopher');
    await page.getByLabel('Passwort').fill(password);
    await page.getByRole('button', { name: 'Login' }).click();
    await expect(page).toHaveURL(/\/(dashboard|pms)$/);
    await page.goto('/pms');
    await expect(page).toHaveURL(/\/pms$/);
    await expect(page.getByRole('heading', { name: /Guten Tag/ })).toBeVisible();
};

const expectNoHorizontalOverflow = async (page: Page, selector: string) => {
    await expect.poll(async () => page.locator(selector).evaluate((element) => (
        element.scrollWidth - element.clientWidth
    ))).toBeLessThanOrEqual(2);
};

test.describe.serial('Chrono PMS local end-to-end', () => {
    test('loads the seeded operating dashboard and room plan', async ({ page }) => {
        await login(page);

        await expect(page.getByRole('heading', { name: /Guten Tag/ })).toBeVisible();
        await expect(page.getByText('Aktive Aufenthalte')).toBeVisible();
        await expect(page.getByText(/Betriebsprüfung|Bereit für den Hotelbetrieb/)).toBeVisible();

        await page.getByRole('button', { name: 'Zimmerplan' }).first().click();
        await expect(page.getByRole('heading', { name: /Zimmerplan für/ })).toBeVisible();
        await expect(page.getByText('101', { exact: true })).toBeVisible();
        await expect(page.getByText('Gabriela Tschopp')).toBeVisible();
    });

    test('creates a guest and reservation through the real UI and API', async ({ page }) => {
        await login(page);

        await page.getByRole('button', { name: 'Gäste' }).first().click();
        await expect(page.getByRole('heading', { name: 'Gastprofil anlegen' })).toBeVisible();

        const firstName = `E2E-${Date.now()}`;
        const guestName = `${firstName} Hotelgast`;
        await page.getByLabel('Vorname').fill(firstName);
        await page.getByLabel('Nachname').fill('Hotelgast');
        await page.getByLabel('E-Mail').fill(`e2e.hotelgast.${Date.now()}@example.test`);
        await page.getByRole('button', { name: 'Gast speichern' }).click();
        await expect(page.getByText(guestName, { exact: true })).toBeVisible();

        await page.getByRole('button', { name: 'Reservierungen' }).last().click();
        await expect(page.getByRole('heading', { name: 'Neue Reservierung' })).toBeVisible();

        const arrival = new Date();
        arrival.setDate(arrival.getDate() + 365 + (Date.now() % 2_000));
        const departure = new Date(arrival);
        departure.setDate(departure.getDate() + 2);
        const dateKey = (value: Date) => value.toISOString().slice(0, 10);

        await page.getByLabel('Gast').selectOption({ label: guestName });
        await page.getByLabel('Zimmertyp').selectOption({ label: 'City Einzelzimmer' });
        await page.getByLabel('Anreise').fill(dateKey(arrival));
        await page.getByLabel('Abreise').fill(dateKey(departure));
        await expect(page.getByLabel('Ratenplan')).toHaveValue(/\d+/);
        await page.getByRole('button', { name: 'Reservierung anlegen' }).click();

        await expect(page.getByText('Reservierung angelegt.')).toBeVisible();
    });

    test('keeps the dark PMS and operations dialog inside a mobile viewport', async ({ page }) => {
        await login(page);
        await page.getByRole('button', { name: 'Dark Mode' }).click();
        await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

        await page.setViewportSize({ width: 390, height: 844 });
        await expectNoHorizontalOverflow(page, 'html');

        await page.getByRole('button', { name: 'Reservierungen' }).first().click();
        const dialog = page.getByRole('dialog', { name: 'Hotelbetrieb' });
        await expect(dialog).toBeVisible();
        await expectNoHorizontalOverflow(page, '.pms-workspace-body');

        const bounds = await dialog.boundingBox();
        expect(bounds).not.toBeNull();
        expect(bounds!.x).toBeGreaterThanOrEqual(0);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);

        const background = await dialog.evaluate((element) => (
            getComputedStyle(element).backgroundColor
        ));
        const channels = background.match(/\d+/g)?.map(Number) ?? [];
        expect(channels).toHaveLength(3);
        expect(Math.max(...channels)).toBeLessThan(64);
    });

    test('keeps the light setup dialog readable at tablet width', async ({ page }) => {
        await page.setViewportSize({ width: 768, height: 1024 });
        await login(page);
        await expectNoHorizontalOverflow(page, 'html');

        await page.getByRole('button', { name: 'Einrichtung verwalten' }).click();
        const dialog = page.getByRole('dialog', { name: 'Hotelfundament' });
        await expect(dialog).toBeVisible();
        await expectNoHorizontalOverflow(page, '.pms-setup-workspace-body');

        const bounds = await dialog.boundingBox();
        expect(bounds).not.toBeNull();
        expect(bounds!.x).toBeGreaterThanOrEqual(0);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(768);

        await expect(dialog.getByRole('button', { name: /Zimmertypen/ })).toBeVisible();
        await expect(dialog.getByRole('button', { name: /^\d+ Zimmer$/ })).toBeVisible();
    });
});
