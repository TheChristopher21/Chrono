async(page)=>{
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1500});
 await page.goto('http://127.0.0.1:8765/chrono-admin-06-klar-planung-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());await f.waitForSelector('#chrono-klar-planung');
 const header=await f.locator('.cp-plan-head').innerText();
 await f.locator('[data-plan-team]').selectOption('Atelier');
 await f.locator('[data-act="plan-day"]').click();
 const teamAgenda=await f.locator('.cp-sheet').innerText();
 if(teamAgenda.includes('Jonas Weber'))throw new Error('Teamfilter ignored in day agenda');
 await f.locator('[data-act="close"]').first().click();
 await f.locator('[data-plan-team]').selectOption('');
 await f.locator('[data-act="plan-next"]').click();
 await f.locator('[data-act="plan-next"]').click();
 const boundary=await f.locator('.cp-plan-head').innerText();
 await f.locator('[data-act="plan-detail"][data-p="0"][data-date="2026-10-01"]').click();
 await f.locator('[data-act="plan-vacation"]').click();
 const person=await f.locator('[name="person"]').inputValue(),locked=await f.locator('[name="person"]').isDisabled();
 const date=await f.locator('[name="start"]').inputValue();
 if(person!=='0'||!locked||date!=='2026-10-01')throw new Error('Person/date context missing');
 await f.locator('[name="end"]').fill('2026-10-02');await f.locator('[data-act="addperiod"]').click();
 await f.locator('[name="start"]').fill('2026-12-21');await f.locator('[name="end"]').fill('2026-12-24');
 await f.locator('[data-act="addperiod"]').click();
 await f.locator('[data-act="localSubmit"]').click();
 const saved=await f.locator('.cp-sheet .cp-notice').innerText();
 if(!saved.includes('2 Zeiträume gespeichert'))throw new Error('Multiple periods not saved');
 const remainedOpen=await f.locator('[data-form="vacation"]').isVisible();
 await f.locator('[data-act="close"]').first().click();
 const calendar=await f.locator('[data-act="plan-detail"][data-p="0"][data-date="2026-10-01"]').innerText();
 if(!calendar.includes('Urlaub'))throw new Error('Week planner not refreshed');
 const views=[];
 for(const name of ['time','requests','calendar','modules','overview']){await f.locator('[data-view="'+name+'"]').first().click();views.push(name)}
 await f.locator('[data-act="customize"]').click();
 const customization=await f.locator('.cp-sheet').innerText();
 return {header,boundary,person,locked,date,teamFilterWorks:!teamAgenda.includes('Jonas Weber'),saved,remainedOpen,calendar,views,customization:customization.slice(0,230),errors};
}
