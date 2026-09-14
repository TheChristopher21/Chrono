async(page)=>{
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1000});
 await page.goto('http://127.0.0.1:8765/chrono-admin-07-ruhig-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());await f.waitForSelector('#chrono-ruhig');
 await f.locator('[data-act="plan-next"]').click();
 const next=await f.locator('.cq-calendar-head h2').innerText();
 await f.locator('[data-act="quiet-day"][data-date="2026-09-24"]').click();
 await f.locator('[data-act="vacationdate"]').click();
 await f.locator('[name="person"]').selectOption('1');
 await f.locator('[name="end"]').fill('2026-09-25');
 await f.locator('[data-act="localSubmit"]').click();
 const save=await f.locator('.cp-sheet .cp-notice').innerText();
 await f.locator('[data-act="close"]').first().click();
 const updated=await f.locator('.cq-week').innerText();
 if(!updated.includes('Luca Meier'))throw new Error('Week not refreshed after vacation');
 await f.locator('[data-act="quiet-more"]').first().click();
 const more=await f.locator('.cq-more').innerText();
 await f.locator('[data-act="customize"]').click();
 await f.locator('[data-quiet-setting="quietWeekends"]').uncheck();
 await f.locator('[data-act="customdone"]').click();
 const days=await f.locator('.cq-day').count();
 if(days!==5)throw new Error('Weekend setting not applied');
 await f.locator('[data-act="quiet-workspaces"]').first().click();
 const tabs=await f.locator('.cp-workspace').isVisible();
 await f.locator('[data-act="quiet-workspaces"]').first().click();
 const views=[];for(const name of ['time','requests','calendar','modules','overview']){await f.locator('[data-view="'+name+'"]').first().click();views.push(name)}
 return {next,save,updated:updated.includes('Luca Meier'),more,days,tabs,views,errors};
}
