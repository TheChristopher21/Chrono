async page => {
    const errors = [], checks = [];
    page.on('pageerror', error => errors.push(String(error)));
    const root = () => page.locator('.admin-workspace:visible .aw-overview');
    const check = (pass, label) => { if (!pass) throw new Error(label); checks.push(label); };
    await page.setViewportSize({ width: 1440, height: 900 });
    await root().getByRole('combobox').selectOption('');
    await root().getByText('Jetzt im Fokus · Alle Teams', { exact: true }).waitFor({ state: 'visible' });
    check((await root().locator('.aw-focus').innerText()).includes('Urlaub von Luca Meier: Beginn morgen'), 'Focus identifies tomorrow’s vacation and the actual person');
    await root().getByRole('button', { name: 'Ersten Vorgang prüfen' }).click();
    await root().locator('.aw-task.is-expanded .aw-task-detail').waitFor({ state: 'visible' });
    check((await root().locator('.aw-task.is-expanded').innerText()).includes('Luca Meier'), 'Focus action opens the right vacation decision');
    check(await root().getByRole('button', { name: 'Genehmigen', exact: true }).isEnabled(), 'Existing decision remains available');
    await root().getByRole('button', { name: 'Schließen: Luca Meier · Urlaub', exact: true }).click();
    await root().getByRole('button', { name: 'Alle', exact: true }).click();
    const account = root().locator('.aw-account-negative li').first();
    check((await account.innerText()).includes('Mirjam Burkart'), 'Largest minus balance leads the account watch');
    await root().getByRole('combobox').selectOption('Beratung');
    await root().getByText('Jetzt im Fokus · Beratung', { exact: true }).waitFor({ state: 'visible' });
    check(!(await root().locator('.aw-focus').innerText()).includes('Luca Meier'), 'Focus respects selected team');
    check((await root().locator('.aw-today').innerText()).includes('Jonas Weber'), 'Today’s registered sick leave is visible');
    await root().getByRole('combobox').selectOption('');
    await root().getByText('Jetzt im Fokus · Alle Teams', { exact: true }).waitFor({ state: 'visible' });
    for (const [width, height] of [[1440, 900], [1366, 768], [1024, 768], [390, 844], [320, 740]]) {
        await page.setViewportSize({ width, height });
        for (const theme of ['light', 'dark']) {
            await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
            await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
            check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), `${width} ${theme}: no horizontal overflow`);
            check(await root().locator('.aw-focus').isVisible(), `${width} ${theme}: focus visible`);
            check(await root().locator('.aw-account-watch').isVisible(), `${width} ${theme}: accounts accessible`);
            check(await root().locator('.aw-task-copy').evaluateAll(elements => elements.every(element => element.getBoundingClientRect().width >= 150)), `${width} ${theme}: task text has readable width`);
            await page.screenshot({ path: `output/playwright/chrono-overview-focus-${width}-${theme}.png`, fullPage: true });
        }
    }
    check(!errors.length, `No browser errors: ${errors.join('; ')}`);
    return { checks, errors };
}
