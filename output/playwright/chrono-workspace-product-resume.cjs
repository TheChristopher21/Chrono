async page => {
  const errors=[],checks=[];page.on('pageerror',error=>errors.push(String(error)));
  const root=()=>page.locator('.admin-workspace:visible');
  const check=(pass,message)=>{if(!pass)throw new Error(message);checks.push(message);};
  const snap=async(name,width,theme='light')=>{
    await page.setViewportSize({width,height:1050});
    await page.evaluate(theme=>{document.documentElement.dataset.theme=theme;document.documentElement.style.colorScheme=theme;},theme);
    check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),`${name} ${width} ${theme} fits`);
    await page.screenshot({path:`output/playwright/chrono-workspace-${name}-${width}-${theme}.png`,fullPage:true});
  };

  const dialog=page.locator('.vacation-entry-modal:visible');
  check(await dialog.isVisible(),'Two periods save without closing dialog');
  check((await dialog.innerText()).includes('Dezember 2026'),'Planning month retained');
  await dialog.getByRole('button',{name:'Schließen',exact:true}).click();
  await root().locator('.aw-primary-nav').getByRole('button',{name:'Mitarbeitende',exact:true}).click();
  await root().locator('.aw-employee-list').waitFor();
  await snap('employees',1440);await snap('employees',390,'dark');
  await page.setViewportSize({width:1440,height:1050});
  await root().locator('.aw-employee-list').getByRole('button',{name:/Mirjam Burkart/}).click();
  await page.getByRole('link',{name:'Zurück zum Dashboard'}).waitFor();
  check(page.url().includes('/admin/dashboard-neu/mitarbeiter/Mirjam'),'Employee route stays new');
  check(await page.getByRole('link',{name:'Zurück zum Dashboard'}).getAttribute('href')==='/admin/dashboard-neu','Employee returns to new dashboard');
  await page.getByRole('link',{name:'Zurück zum Dashboard'}).click();
  await root().locator('.aw-overview[aria-busy="false"]').waitFor();
  await root().getByRole('button',{name:'Details: Jonas Weber · 14.09.2026 – 16.09.2026',exact:true}).click();
  await page.locator('.calendar-day-details-modal:visible').waitFor();
  check((await page.locator('.calendar-day-details-modal:visible').innerText()).includes('Jonas'),'Absence details open in target workspace');
  await page.locator('.calendar-day-details-modal:visible').getByRole('button',{name:'Schließen',exact:true}).click();
  await root().locator('.aw-primary-nav').getByRole('button',{name:/^Anträge/}).click();
  await root().getByRole('heading',{name:'Antragscenter',exact:true}).waitFor();
  await snap('requests',1440,'dark');await snap('requests',390);
  await page.setViewportSize({width:1440,height:1050});
  await root().locator('.aw-primary-nav').getByRole('button',{name:'Zeitprüfung',exact:true}).click();
  await root().locator('.team-overview-content').waitFor();
  await snap('time',1440);await snap('time',390,'dark');
  await page.setViewportSize({width:1440,height:1050});
  await root().locator('.aw-primary-nav').getByRole('button',{name:'Übersicht',exact:true}).click();
  check(errors.length===0,'No runtime errors during product flows');
  return {checks,errors};
}

