async(page)=>{
 const errors=[],results=[];
 page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1600});
 await page.goto('http://127.0.0.1:8765/chrono-admin-05-studio-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());
 await f.waitForSelector('#chrono-studio');
 for(const view of ['time','requests','calendar','modules','overview']){
  await f.locator('[data-view="'+view+'"]').first().click();
  results.push({view,text:(await f.locator('#st-content').innerText()).slice(0,200)});
 }
 await f.locator('[data-action="absence"]').first().click();
 await f.locator('#st-absence-start').fill('2026-10-12');await f.locator('#st-absence-end').fill('2026-10-16');
 await f.locator('[data-action="queue-period"]').click();
 await f.locator('#st-absence-start').fill('2026-12-21');await f.locator('#st-absence-end').fill('2026-12-24');
 await f.locator('[data-action="queue-period"]').click();
 const queued=await f.locator('#st-queued-list').innerText();
 await f.locator('#st-absence-form .st-primary').click();
 const saved=await f.locator('#st-absence-result').innerText();
 const remains=await f.locator('#st-absence-form').count();
 await page.setViewportSize({width:352,height:2400});
 const layout=await f.evaluate(()=>{const root=document.getElementById('chrono-studio'),b=root.getBoundingClientRect();return {height:root.scrollHeight,overflow:[...root.querySelectorAll('input,select,button,textarea,label')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&(r.right>b.right+2||r.left<b.left-2)&&!e.closest('[class*="table"],[class*="scroll"]')}).map(e=>e.outerHTML.slice(0,100)).slice(0,8)}});
 await page.locator('iframe').evaluate((e,h)=>e.style.height=h+'px',layout.height+6);
 await page.setViewportSize({width:352,height:layout.height+38});
 await page.screenshot({path:'output/playwright/chrono-admin-studio-vacation-mobile.png',fullPage:true});
 return {results,queued,saved,remains,layout,errors};
}
