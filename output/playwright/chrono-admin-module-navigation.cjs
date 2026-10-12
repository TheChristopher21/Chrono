async page => {
    const errors = [];
    const checks = [];
    page.on('pageerror', error => errors.push(String(error)));
    const sidebar = () => page.locator('.admin-workspace:visible .aw-sidebar');
    const assert = (pass, message) => { if (!pass) throw new Error(message); checks.push(message); };
    const initialLinks = await sidebar().locator('a').evaluateAll(links => links.map(link => link.getAttribute('href')));
    assert(initialLinks.includes('/admin/users') && initialLinks.includes('/personal-data') && initialLinks.includes('/admin/company') && initialLinks.includes('/pms'), 'All available areas included');
    for (const [label, path] of [['Dienstplan', '/admin/schedule'], ['Lohnabrechnungen verwalten', '/admin/payslips'], ['Analytics', '/admin/analytics'], ['Benutzerverwaltung', '/admin/users'], ['Meine Daten', '/personal-data']]) {
        await sidebar().getByRole('link', { name: label, exact: true }).click();
        await page.waitForURL(url => url.pathname === path);
        await page.locator('.aw-module-workspace:visible .aw-module-content').waitFor({ state: 'visible' });
        assert(await sidebar().isVisible(), `${label}: sidebar retained`);
        assert(await page.locator('.chrono-navbar-shell:visible').count() === 1, `${label}: exactly one app navbar`);
        assert(await sidebar().getByRole('link', { name: label, exact: true }).getAttribute('aria-current') === 'page', `${label}: active item highlighted`);
        assert(JSON.stringify(await sidebar().locator('a').evaluateAll(links => links.map(link => link.getAttribute('href')))) === JSON.stringify(initialLinks), `${label}: same available menu`);
    }
    await sidebar().getByRole('button', { name: 'Kalender', exact: true }).click();
    await page.locator('.admin-workspace:visible .aw-header').waitFor({ state: 'visible' });
    assert(page.url().includes('/admin/dashboard-neu?tab=calendar'), 'Return to dashboard calendar');
    await sidebar().getByRole('link', { name: 'Dienstplan', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/admin/schedule');
    await page.reload();
    await sidebar().waitFor({ state: 'visible' });
    assert(await page.locator('.chrono-navbar-shell:visible').count() === 1, 'Navigation retained after reload');
    for (const [width, height] of [[1440, 900], [1024, 768], [390, 844]]) {
        await page.setViewportSize({ width, height });
        for (const theme of ['light', 'dark']) {
            await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
            if (width < 761) {
                const more = sidebar().getByRole('button', { name: 'Schnellzugriff & mehr' });
                if (await more.getAttribute('aria-expanded') !== 'true') await more.click();
            }
            assert(await sidebar().getByRole('link', { name: 'Dienstplan', exact: true }).isVisible(), `${width} ${theme}: menu available`);
            assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), `${width} ${theme}: no horizontal overflow`);
        }
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.evaluate(() => { document.documentElement.dataset.theme = 'light'; });
    await page.screenshot({ path: 'output/playwright/chrono-admin-module-navigation.png' });
    assert(errors.length === 0, `No browser errors: ${errors.join('; ')}`);
    return { checks, errors };
}
