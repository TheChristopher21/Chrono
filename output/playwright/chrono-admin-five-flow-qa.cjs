async (page) => {
 const errors=[],results=[];
 page.on('pageerror', e=>errors.push(String(e)));
 for(const concept of ['01-klar','02-fokus','03-team','04-planung']){
  await page.setViewportSize({width:1056,height:1100});
  await page.goto('http://127.0.0.1:8765/chrono-admin-'+concept+'-preview.html');
  const f=page.frames().find(x=>x!==page.mainFrame());
  await f.waitForSelector('[id^="chrono-"]');
  const views=[];
  for(const name of ['time','requests','calendar','modules','overview']){
   const target=f.locator('[data-view="'+name+'"]').first();
   await target.click();
   views.push({name,text:(await f.locator('main').innerText()).slice(0,240)});
  }
  await f.locator('[data-view="calendar"]').first().click();
  await f.locator('[data-act="vacation"], [data-action="vacation"]').first().click();
  const dialog=await f.evaluate(()=>({fields:[...document.querySelectorAll('input, select, textarea')].map(e=>({name:e.name,field:e.dataset.field,value:e.value})),buttons:[...document.querySelectorAll('button')].map(e=>({text:e.innerText,act:e.dataset.act||e.dataset.action})).filter(e=>/speichern|vormerken|schliessen|fertig|hinzufügen/i.test(e.text))}));
  results.push({concept,views,dialog});
 }
 return {results,errors};
}
