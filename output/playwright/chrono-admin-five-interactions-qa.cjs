async (page) => {
 const errors=[],results=[];
 page.on('pageerror', e=>errors.push(String(e)));
 for(const concept of ['01-klar','02-fokus','03-team','04-planung']){
  await page.setViewportSize({width:1056,height:1100});
  await page.goto('http://127.0.0.1:8765/chrono-admin-'+concept+'-preview.html');
  const f=page.frames().find(x=>x!==page.mainFrame());
  await f.waitForSelector('[id^="chrono-"]');
  const first=concept.startsWith('01')||concept.startsWith('02');
  const row={concept};
  await f.locator('[data-view="requests"]').first().click();
  row.requestBefore=(await f.locator('main').innerText()).slice(-400);
  await f.getByRole('button',{name:/^Genehmigen/}).first().click();
  row.decision=(await f.locator('body').innerText()).slice(-550);
  await f.locator('[data-view="calendar"]').first().click();
  await f.locator('[data-act="vacation"], [data-action="vacation"]').first().click();
  const start=f.locator(first?'[name="start"]':'[data-field="vac-start"]');
  const end=f.locator(first?'[name="end"]':'[data-field="vac-end"]');
  await start.fill('2026-09-21');await end.fill('2026-09-23');
  await f.locator(first?'[data-act="addperiod"]':'[data-action="queue-period"]').click();
  await start.fill('2026-10-12');await end.fill('2026-10-16');
  await f.locator(first?'[data-act="addperiod"]':'[data-action="queue-period"]').click();
  row.queue=(await f.locator('body').innerText()).match(/(?:21.9.2026|21.09.2026|12.10.2026|12.10.)/g);
  await f.getByRole('button',{name:'Zeiträume speichern',exact:true}).click();
  row.saveVisible=(await f.locator('body').innerText()).includes('gespeichert');
  row.dialogRemains=await start.count()>0;
  row.calendarMarkers=await f.locator(first?'.cp-event':'.ct-calendar-event').count();
  row.afterSave=(await f.locator('body').innerText()).slice(-450);
  await page.setViewportSize({width:352,height:1100});
  row.mobileDialog=await f.evaluate(()=>{
   const root=document.querySelector('[id^="chrono-"]'),b=root.getBoundingClientRect();
   return [...root.querySelectorAll('input,select,textarea,button,label')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&(r.right>b.right+2||r.left<b.left-2)&&!e.closest('[class*="table-wrap"],[class*="scroll"]')}).map(e=>e.outerHTML.slice(0,120)).slice(0,8);
  });
  results.push(row);
 }
 return {results,errors};
}
