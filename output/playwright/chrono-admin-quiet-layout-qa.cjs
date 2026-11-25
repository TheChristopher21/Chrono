async (page) => {
  const results = [];
  const errors = [];
  page.on('pageerror', err => errors.push(String(err)));
  const concepts = ['07-ruhig'];
  for (const concept of concepts) {
    await page.goto('http://127.0.0.1:8765/chrono-admin-' + concept + '-preview.html');
    const frame = page.frames().find(f => f !== page.mainFrame());
    await frame.waitForSelector('[id^="chrono-"]');
    for (const width of [1056, 768, 352]) {
      await page.setViewportSize({ width, height: 900 });
      await page.emulateMedia({ colorScheme: 'light' });
      await frame.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      const geometry = await frame.evaluate(() => {
        const root = document.querySelector('[id^="chrono-"]');
        const bounds = root.getBoundingClientRect();
        const overflow = [...root.querySelectorAll('*')].filter(el => {
          const s = getComputedStyle(el), r = el.getBoundingClientRect();
          return s.display !== 'none' && r.width > 0 && (r.right > bounds.right + 2 || r.left < bounds.left - 2) && !el.closest('[class*="table-wrap"], [class*="scroll"]');
        }).slice(0, 8).map(el => ({tag:el.tagName, cls:el.className, text:el.textContent.trim().slice(0,70)}));
        return { width: bounds.width, height: root.scrollHeight, overflow, labels: root.querySelectorAll('label label').length };
      });
      await page.locator('iframe').evaluate((el, height) => el.style.height = height + 'px', geometry.height + 6);
      await page.setViewportSize({width,height:geometry.height+38});
      await frame.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      await page.screenshot({ path:'output/playwright/chrono-admin-' + concept + '-' + (width-32) + '-light.png', fullPage:true });
      results.push({concept, viewport:width-32, ...geometry});
      if (width === 1056) {
        await page.emulateMedia({colorScheme:'dark'});
        await frame.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        await page.screenshot({path:'output/playwright/chrono-admin-' + concept + '-1024-dark.png',fullPage:true});
      }
    }
  }
  return { results, errors };
}

