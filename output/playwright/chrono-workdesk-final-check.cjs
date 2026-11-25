async(page)=>{
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1500});await page.emulateMedia({colorScheme:'dark'});
 await page.goto('http://127.0.0.1:8765/chrono-admin-09-arbeitsplatz-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());await f.waitForSelector('#chrono-arbeitsplatz');
 const check=(v,s)=>{if(!v)throw new Error(s)};
 await f.locator('[data-plan-team]').selectOption('Beratung');
 await f.locator('[data-act="desk-negative"]').click();
 const balances=await f.locator('.cp-sheet').innerText();
 check(balances.includes('Jonas Weber')&&balances.includes('Lea Baumann')&&!balances.includes('Mirjam'),'Scoped negative accounts');
 await f.locator('.cp-sheet-head [data-act="close"]').click();
 await f.locator('[data-act="desk-balances"]').click();
 const allBalances=await f.locator('.cp-sheet').innerText();
 check(allBalances.includes('Tim Schneider')&&!allBalances.includes('Nina Keller'),'Scoped all accounts');
 await f.locator('.cp-sheet-head [data-act="close"]').click();await f.locator('[data-plan-team]').selectOption('');
 await f.locator('.cp-chrome [data-act="account"]').click();
 await f.locator('[data-bind="role"]').selectOption('Nur Ansicht');
 await f.locator('.cp-sheet-head [data-act="close"]').click();
 await f.locator('.cw-today [data-act="desk-absence"]').click();
 check((await f.locator('.cp-sheet').innerText()).includes('Jonas Weber'),'Read-only details visible');
 check(await f.locator('.cp-sheet [data-act="eventedit"]').isDisabled(),'Read-only editing disabled');
 await f.locator('.cp-sheet-head [data-act="close"]').click();await f.locator('[data-key="r2"]').click();
 check(await f.locator('#desk-detail-r2 [data-act="approve"]').isDisabled(),'Read-only approval disabled');
 const geometry=await f.evaluate(()=>{const root=document.querySelector('#chrono-arbeitsplatz'),b=root.getBoundingClientRect();const overflow=[...root.querySelectorAll('*')].filter(el=>{const r=el.getBoundingClientRect();return r.width>0&&(r.right>b.right+2||r.left<b.left-2)}).map(el=>el.className);return{height:root.scrollHeight,overflow}});
 await page.locator('iframe').evaluate((el,h)=>el.style.height=h+'px',geometry.height+6);await page.setViewportSize({width:1056,height:geometry.height+38});
 await page.screenshot({path:'output/playwright/chrono-admin-09-review-dark.png',fullPage:true});
 check(geometry.overflow.length===0,'Expanded dark layout');
 return {scopedNegativeBalances:true,scopedBalances:true,readOnlyDetails:true,readOnlyWritesBlocked:true,expandedDarkLayout:true,errors};
}
