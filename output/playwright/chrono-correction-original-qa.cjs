async page => {
  const previous = page.getByRole('region', {name:'Zu erledigen'}).getByText('08:00 Start · 12:00 Ende · 13:00 Start · 16:30 Ende', {exact:true});
  if (!(await previous.isVisible())) await page.getByRole('button', {name:'Prüfen: Nina Keller · Zeitkorrektur'}).click();
  await previous.waitFor({state:'visible'});
  await page.screenshot({path:'output/playwright/chrono-correction-original-desktop.png',fullPage:true});
  const checks=[];
  for (const width of [1440,390]) {
    await page.setViewportSize({width,height:900});
    await previous.scrollIntoViewIfNeeded();
    checks.push(await page.evaluate(() => ({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,original:document.querySelector('.aw-task.is-expanded .aw-punch-value')?.textContent})));
    if(width===390) await page.screenshot({path:'output/playwright/chrono-correction-original-mobile.png',fullPage:true});
  }
  await page.setViewportSize({width:1440,height:1050});
  return checks;
}
