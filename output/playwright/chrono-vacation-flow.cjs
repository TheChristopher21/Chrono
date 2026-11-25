async (page) => {
  const dialog = page.getByRole('dialog');
  const selection = dialog.getByLabel('Benutzer Auswahl:');
  if (await selection.inputValue() !== 'Mirjam') throw new Error('Focused employee missing');
  const before = await page.evaluate(() => {
    window.__vacationDialog = document.querySelector('.vacation-entry-modal');
    window.__vacationCalendar = document.querySelector('.vacation-calendar-admin');
    return {scrollY:window.scrollY, month:document.querySelector('.month-navigation span').textContent};
  });
  await dialog.getByLabel('Startdatum:').fill('2026-09-21');
  await dialog.getByLabel('Enddatum:').fill('2026-09-25');
  await dialog.getByRole('button', {name:'+ Weiteren Zeitraum vormerken'}).click();
  await dialog.getByLabel('Startdatum:').fill('2026-10-12');
  await dialog.getByLabel('Enddatum:').fill('2026-10-16');
  await page.screenshot({path:'output/playwright/chrono-vacation-multiple-periods.png'});
  const beforeSubmit = await page.evaluate(() => ({scrollY:window.scrollY}));
  await dialog.getByRole('button', {name:'Zeiträume speichern',exact:true}).click();
  await dialog.getByRole('status').filter({hasText:'gespeichert'}).waitFor();
  await dialog.getByRole('button', {name:'Schließen',exact:true}).waitFor({state:'visible'});
  await page.waitForFunction(() => !document.querySelector('.vacation-entry-fields').disabled);
  const after = await page.evaluate(() => ({
    scrollY:window.scrollY,
    sameDialog:window.__vacationDialog===document.querySelector('.vacation-entry-modal'),
    sameCalendar:window.__vacationCalendar===document.querySelector('.vacation-calendar-admin'),
    month:document.querySelector('.month-navigation span').textContent,
    savedDaysInDialog:document.querySelectorAll('.vacation-range-calendar .vacation-day-saved').length,
    draftStart:document.querySelector('#vacStartDateInput').value,
    selection:document.querySelector('#vacationUserSelect').value,
    overflow:document.documentElement.scrollWidth>innerWidth
  }));
  if (!after.sameDialog || !after.sameCalendar || after.savedDaysInDialog!==5 || after.draftStart!=='' || after.selection!=='Mirjam' || after.month!==before.month || Math.abs(after.scrollY-beforeSubmit.scrollY)>3) throw new Error(JSON.stringify({before,beforeSubmit,after}));
  await page.screenshot({path:'output/playwright/chrono-vacation-saved-desktop.png'});
  console.log(JSON.stringify({before,beforeSubmit,after}));
}
