const { chromium } = require('../../Chrono-frontend/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const names = process.argv.slice(2);
  const result = [];
  for (const name of names) {
    const page = await browser.newPage({ viewport: { width: 1056, height: 1100 }, colorScheme: 'light' });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const source = path.join(__dirname, `${name}-preview.html`);
    await page.goto('file:///' + source.replaceAll('\\', '/'));
    const frame = page.frames().find(f => f !== page.mainFrame());
    const root = frame.locator('#' + name);
    await root.waitFor();
    const sizes = [];
    for (const width of [1024, 736, 360]) {
      await page.setViewportSize({ width: width + 32, height: 1100 });
      const size = await root.evaluate(el => ({ width: el.clientWidth, scrollWidth: el.scrollWidth, height: el.scrollHeight, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth }));
      sizes.push({ width, ...size });
      await page.locator('iframe').evaluate((el, height) => el.style.height = `${height + 6}px`, size.height);
      await page.screenshot({ path: path.join(__dirname, `${name}-${width}.png`), fullPage: true });
    }
    await page.setViewportSize({ width: 1056, height: 1100 });
    await page.emulateMedia({ colorScheme: 'dark' });
    const darkColors = await root.evaluate(el => ({ background: getComputedStyle(el).backgroundColor, color: getComputedStyle(el).color }));
    await page.screenshot({ path: path.join(__dirname, `${name}-dark.png`), fullPage: true });
    await page.emulateMedia({ colorScheme: 'light' });
    const interactions = [];
    if (name === 'chrono-focus') {
      await frame.locator('.cf-nav[data-view="inbox"]').click();
      await frame.locator('[data-inbox-list] [data-request="1"]').click();
      await frame.locator('[data-decide="1"][data-result="Genehmigt"]').click();
      interactions.push({ approvalRemaining: await frame.locator('[data-count]').first().textContent() });
      await frame.locator('[data-inbox="done"]').click();
      interactions.push({ history: await frame.locator('[data-inbox-list]').innerText() });
      await frame.locator('.cf-nav[data-view="time"]').click();
      await frame.locator('[data-model]').selectOption('hourly');
      interactions.push({ hourly: await frame.locator('[data-team-table]').innerText() });
      await frame.locator('[data-model]').selectOption('all');
      await frame.locator('[data-team-search]').fill('Anna');
      await frame.locator('[data-person="Anna Weber"]').filter({ visible: true }).click();
      await frame.locator('[data-action="edit-time"]').first().click();
      await frame.locator('[data-action="add-stamp"]').click();
      interactions.push({ timeInputs: await frame.locator('[data-detail] input[type="time"]').count() });
      await frame.locator('[data-local-form] button[type="submit"]').click();
      await frame.locator('.cf-nav[data-view="calendar"]').click();
      await frame.locator('[data-month="1"]').click();
      interactions.push({ nextMonth: await frame.locator('[data-month-name]').textContent() });
      await frame.locator('.cf-nav[data-view="modules"]').click();
      await frame.locator('[data-module-search]').fill('Payroll');
      interactions.push({ modules: await frame.locator('[data-modules]').innerText() });
      for (const view of ['home','inbox','time','calendar','planning','modules']) {
        await page.setViewportSize({ width: 392, height: 1100 });
        await frame.locator(`.cf-nav[data-view="${view}"]`).click();
        const check = await root.evaluate(el => ({ width: el.clientWidth, scrollWidth: el.scrollWidth, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth }));
        interactions.push({ view, mobile: check });
      }
    }
    if (name === 'chrono-nightdesk') {
      await frame.locator('[data-action="approve"]').click();
      interactions.push({ afterApproval: await frame.locator('#nd-queue-count').innerText() });
      await frame.locator('[data-queue="history"]').click();
      interactions.push({ history: await frame.locator('#nd-queue-list').innerText() });
      await frame.locator('.nd-nav [data-view="time"]').click();
      await frame.locator('#nd-period-type').selectOption('month');
      await frame.locator('[data-action="person-report"]').click();
      interactions.push({ monthlyPDF: await frame.locator('#nd-person-preview').innerText() });
      await frame.locator('[data-action="close-drawer"]').click();
      await frame.locator('#nd-period-type').selectOption('week');
      await frame.locator('#nd-employee-search').fill('Sara');
      interactions.push({ personFilter: await frame.locator('#nd-time-body').innerText() });
      await frame.locator('.nd-nav [data-view="calendar"]').click();
      await frame.locator('[data-action="new-absence"]').click();
      await frame.locator('#nd-absence-note').fill('Prüfung lokale Abwesenheit');
      await frame.locator('#nd-absence-save').click();
      interactions.push({ savedAbsence: await frame.locator('#nd-toast').innerText() });
      await frame.locator('[data-month="1"]').click();
      interactions.push({ nextMonth: await frame.locator('#nd-month-label').innerText() });
      await frame.locator('.nd-nav [data-view="modules"]').click();
      await frame.locator('#nd-module-search').fill('PMS');
      interactions.push({ pms: await frame.locator('#nd-module-groups').innerText() });
      for (const view of ['work', 'time', 'calendar', 'modules']) {
        await page.setViewportSize({ width: 392, height: 1100 });
        await frame.locator(`.nd-nav [data-view="${view}"]`).click();
        const check = await root.evaluate(el => ({ width: el.clientWidth, scrollWidth: el.scrollWidth, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth }));
        interactions.push({ view, mobile: check });
      }
    }
    if (name === 'chrono-atelier') {
      await frame.locator('.ca-nav [data-view="requests"]').click();
      await frame.locator('[data-approve]').first().click();
      interactions.push({ afterApproval: await frame.locator('[data-request-count]').first().innerText() });
      await frame.locator('[data-reject]').first().click();
      await frame.locator('#ca-reject-form textarea').fill('Teamabdeckung in dieser Woche noch offen.');
      await frame.locator('#ca-reject-form button[type="submit"]').click();
      interactions.push({ afterRejection: await frame.locator('[data-request-count]').first().innerText() });
      await frame.locator('.ca-nav [data-view="time"]').click();
      await frame.locator('#ca-time-search').fill('Noah');
      interactions.push({ personFilter: await frame.locator('#ca-time-body').innerText() });
      await frame.locator('.ca-nav [data-view="modules"]').click();
      await frame.locator('#ca-module-search').fill('PMS');
      await frame.locator('[data-module="PMS"]').click();
      interactions.push({ pms: await frame.locator('#ca-detail-body').innerText() });
      for (const view of ['home', 'time', 'requests', 'team', 'modules']) {
        await page.setViewportSize({ width: 392, height: 1100 });
        await frame.locator(`.ca-nav [data-view="${view}"]`).click();
        const check = await root.evaluate(el => ({ width: el.clientWidth, scrollWidth: el.scrollWidth, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth }));
        interactions.push({ view, mobile: check });
      }
    }
    result.push({ name, sizes, darkColors, interactions, errors });
    await page.close();
  }
  await browser.close();
  const output = JSON.stringify(result, null, 2);
  fs.writeFileSync(path.join(__dirname, 'admin-mockup-qa-' + names.join('-') + '.json'), output);
  console.log(output);
})().catch(error => { console.error(error); process.exitCode = 1; });
