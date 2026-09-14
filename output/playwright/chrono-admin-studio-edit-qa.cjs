async(page)=>{
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.setViewportSize({width:1056,height:1500});
 await page.goto('http://127.0.0.1:8765/chrono-admin-05-studio-preview.html');
 const f=page.frames().find(x=>x!==page.mainFrame());
 await f.waitForSelector('#chrono-studio');
 await f.locator('[data-view="calendar"]').first().click();
 await f.locator('[data-day="18"]').click();
 await f.locator('[data-edit-absence="1"]').click();
 await f.locator('#st-absence-note').fill('Geprüfter Beispielkommentar');
 await f.locator('[data-action="save-absence"]').click();
 const saved=await f.locator('#st-absence-result').innerText();
 await f.locator('[data-action="delete-absence"]').click();
 await f.locator('[data-action="confirm-delete-absence"]').click();
 const deletedDayText=await f.locator('[data-day="18"]').innerText();
 const otherDayText=await f.locator('[data-day="21"]').innerText();
 await f.locator('[data-action="customize"]').first().click();
 const customization=(await f.locator('#st-dialog').innerText()).slice(0,380);
 return {saved,deletedDayText,otherDayText,customization,errors};
}
