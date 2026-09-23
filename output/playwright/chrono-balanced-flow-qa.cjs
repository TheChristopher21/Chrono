async(page)=>{
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1400});
 await page.goto('http://127.0.0.1:8765/chrono-admin-08-agenda-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());await f.waitForSelector('#chrono-agenda');
 const initialRows=await f.locator('.ca-agenda-row').count();
 if(initialRows!==3)throw new Error('Multi-day absences duplicated');
 await f.locator('[data-plan-team]').selectOption('Beratung');
 const filtered=await f.locator('.ca-agenda-list').innerText();
 if(filtered.includes('Mirjam')||!filtered.includes('Jonas'))throw new Error('Team filter not applied');
 await f.locator('[data-plan-team]').selectOption('');
 await f.locator('.ca-action-row[data-id="1"]').click();
 await f.locator('[data-act="approve"]').first().click();
 await f.locator('[data-view="overview"]').first().click();
 const requestCount=await f.locator('.ca-attention-head h2').first().innerText();
 await f.locator('[data-act="plan-new"]').click();
 await f.locator('[name="person"]').selectOption('1');
 await f.locator('[name="start"]').fill('2026-10-05');await f.locator('[name="end"]').fill('2026-10-09');
 await f.locator('[data-act="localSubmit"]').click();
 const saved=await f.locator('.cp-sheet .cp-notice').innerText();
 await f.locator('[data-act="close"]').first().click();
 const updated=await f.locator('.ca-agenda-list').innerText();
 if(!updated.includes('Luca Meier'))throw new Error('New vacation missing in agenda');
 await f.locator('[data-act="quiet-more"]').first().click();await f.locator('[data-act="customize"]').click();
 await f.locator('[data-quiet-setting="quietTasks"]').uncheck();await f.locator('[data-act="customdone"]').click();
 if(await f.locator('.ca-attention').count())throw new Error('View setting not applied');
 return {initialRows,teamFilter:true,requestCount,saved,updatedRows:await f.locator('.ca-agenda-row').count(),settings:true,errors};
}
