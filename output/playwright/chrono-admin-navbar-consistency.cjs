async page => {
    const results = [];
    const errors = [];
    page.on('pageerror', error => errors.push(String(error)));
    const root = page.locator('.admin-workspace:visible');
    for (const [width, height] of [[1440, 900], [1366, 768], [1024, 768], [1440, 650], [390, 844]]) {
        await page.setViewportSize({ width, height });
        for (const theme of ['light', 'dark']) {
            await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
            let baseline;
            for (const label of ['Mitarbeitende', 'Kalender', 'Zeitprüfung', 'Anträge', 'Übersicht', 'Mitarbeitende']) {
                await root.locator('.aw-primary-nav').getByRole('button', { name: label }).click();
                await root.locator('.aw-sidebar').waitFor({ state: 'visible' });
                await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
                const geometry = await root.locator('.aw-sidebar').evaluate(sidebar => {
                    const rect = element => {
                        const { x, y, width, height } = element.getBoundingClientRect();
                        const style = getComputedStyle(element);
                        return { x, y, width, height, fontSize: style.fontSize, padding: style.padding, gap: style.gap };
                    };
                    return [rect(sidebar), ...[...sidebar.querySelectorAll('.aw-nav-item, .aw-sidebar-label, .aw-legacy-link')].map(rect)];
                });
                if (!baseline) baseline = geometry;
                if (JSON.stringify(geometry) !== JSON.stringify(baseline)) throw new Error(`Navigation changed at ${width}x${height} ${theme} ${label}: ${JSON.stringify({ baseline, geometry })}`);
                if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2)) throw new Error(`Horizontal overflow at ${width} ${label}`);
            }
            results.push({ width, height, theme, consistent: true });
        }
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.evaluate(() => { document.documentElement.dataset.theme = 'light'; });
    await root.locator('.aw-primary-nav').getByRole('button', { name: 'Kalender' }).click();
    await page.screenshot({ path: 'output/playwright/chrono-admin-navbar-calendar.png' });
    if (errors.length) throw new Error(errors.join('\n'));
    return { results, errors };
}
