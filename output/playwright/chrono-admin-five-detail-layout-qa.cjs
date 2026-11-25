async(page)=>{
 const results=[],errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 for(const concept of ['01-klar','02-fokus','03-team','04-planung']){
  await page.setViewportSize({width:352,height:2400});
  await page.goto('http://127.0.0.1:8765/chrono-admin-'+concept+'-preview.html');
  const f=page.frames().find(x=>x!==page.mainFrame());
  await f.waitForSelector('[id^="chrono-"]');
  for(const name of ['time','requests','calendar','modules']){
   await f.locator('[data-view="'+name+'"]').first().click();
   const geom=await f.evaluate(()=>{
    const root=document.querySelector('[id^="chrono-"]'),b=root.getBoundingClientRect();
    return {height:root.scrollHeight,overflow:[...root.querySelectorAll('input,button,select,textarea,h1,h2')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&(r.right>b.right+2||r.left<b.left-2)&&!e.closest('[class*="table"],[class*="scroll"]')}).slice(0,8).map(e=>({text:e.innerText.slice(0,70),cls:e.className}))};
   });
   results.push({concept,view:name,...geom});
   if(name==='calendar'){
    await page.locator('iframe').evaluate((el,h)=>el.style.height=h+'px',geom.height+6);
    await page.setViewportSize({width:352,height:geom.height+38});
    await page.screenshot({path:'output/playwright/chrono-admin-'+concept+'-calendar-mobile.png',fullPage:true});
   }
  }
 }
 return {results,errors};
}
