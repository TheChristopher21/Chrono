async page => {
  const errors=[],checks=[];page.on('pageerror',e=>errors.push(String(e)));page.on('console',msg=>{if(msg.type()==='error')errors.push(msg.text());});
  const root=()=>page.locator('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');
  const check=(ok,message)=>{if(!ok)throw new Error(message);checks.push(message);};
  const ready=async()=>page.waitForFunction(()=>{const roots=document.querySelectorAll('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');return roots.length===1&&Array.from(roots[0].querySelectorAll('.aw-header button')).some(b=>b.textContent.includes('Urlaub eintragen')&&!b.disabled);});
  const destination=async(source,key)=>page.waitForFunction(({source,key})=>{const panes=document.querySelectorAll('[data-workspace-pane]:not([aria-hidden])');return panes.length===1&&panes[0].dataset.workspacePane!==source&&panes[0].querySelector('.admin-workspace')?.dataset.workspaceTab===key;},{source,key});
  await page.setViewportSize({width:1366,height:768});
  await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab=time');await ready();
  await root().getByRole('button',{name:'Monatsansicht',exact:true}).click();
  await root().getByRole('button',{name:'Wochenansicht',exact:true}).waitFor();
  check(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1),'Monthly time review fits laptop viewport');
  await page.screenshot({path:'output/playwright/chrono-workspace-v2-time-month.png',fullPage:true});
  await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab=employees');await ready();
  check(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1),'Employee list fits laptop viewport');
  const source=await root().evaluate(e=>e.closest('[data-workspace-pane]').dataset.workspacePane);
  await root().locator('[data-employee="Mirjam"]').getByRole('button',{name:'+ Urlaub',exact:true}).click();
  await destination(source,'calendar');await ready();
  const dialog=page.locator('.vacation-entry-modal:visible');await dialog.waitFor();
  check(await dialog.getByRole('combobox',{name:/Benutzer Auswahl/}).inputValue()==='Mirjam','Employee action preselects Mirjam in destination calendar');
  await dialog.getByLabel(/Startdatum/).fill('2026-10-05');await dialog.getByLabel(/Enddatum/).fill('2026-10-09');
  await dialog.getByRole('button',{name:/Weiteren Zeitraum vormerken/}).click();
  await dialog.getByLabel(/Startdatum/).fill('2026-12-01');await dialog.getByLabel(/Enddatum/).fill('2026-12-03');
  await dialog.locator('button[type="submit"]').click();await dialog.getByRole('status').filter({hasText:/gespeichert/i}).waitFor();
  check(await dialog.isVisible(),'Two periods save while calendar dialog stays open');
  check((await dialog.innerText()).includes('Dezember 2026'),'Current planning month retained after save');
  await dialog.getByRole('button',{name:'Schließen',exact:true}).click();
  for(const key of ['overview','employees','calendar','time']){
    await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab='+key);await ready();
    await page.locator('nav[aria-label="Hauptnavigation"]:visible').getByRole('button',{name:'Dark Mode',exact:true}).click();
    await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');
    await page.screenshot({path:`output/playwright/chrono-workspace-v2-${key}-dark.png`,fullPage:true});
    check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),key+' dark mode has no horizontal overflow');
  }
  return {checks,errors};
}
