async page => {
  const scenes=[],errors=[];page.on('pageerror',e=>errors.push(String(e)));
  const root=()=>page.locator('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');
  const keys={'Kalender':'calendar','Zeitprüfung':'time','Übersicht':'overview','Mitarbeitende':'employees','Anträge 4':'requests'};
  for(const tab of ['Kalender','Zeitprüfung','Übersicht','Mitarbeitende','Anträge 4']){
    await page.setViewportSize({width:1440,height:900});
    await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab='+keys[tab]);
    await page.waitForFunction(key=>{const roots=document.querySelectorAll('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');return roots.length===1&&roots[0].dataset.workspaceTab===key;}, keys[tab]);
    await page.waitForFunction(()=>{const root=document.querySelector('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');return root&&Array.from(root.querySelectorAll('.aw-header button')).some(b=>b.textContent.includes('Urlaub eintragen')&&!b.disabled);});
    for(const [width,height] of [[1366,768],[1440,900],[1920,1080],[390,844]]){
      await page.setViewportSize({width,height});
      const metrics=await root().evaluate(root=>{
        const rect=sel=>{const e=root.querySelector(sel);if(!e)return null;const r=e.getBoundingClientRect();return {left:r.left,width:r.width,top:r.top,bottom:r.bottom,height:r.height,scroll:e.scrollHeight,client:e.clientHeight};};
        return {tab:root.dataset.workspaceTab,width:innerWidth,height:innerHeight,documentHeight:document.documentElement.scrollHeight,documentWidth:document.documentElement.scrollWidth,chrome:rect('.chrono-navbar-shell'),shell:rect('.admin-dashboard-shell'),header:rect('.aw-header'),main:rect('.admin-dashboard-panels'),calendar:rect('.calendar-lg'),days:root.querySelectorAll('.calendar-lg .react-calendar__tile').length,lastDay:rect('.calendar-lg .react-calendar__tile:last-child'),time:rect('.week-section'),table:rect('.table-responsive-wrapper')};
      });
      scenes.push(metrics);
      await page.screenshot({path:`output/playwright/chrono-workspace-v2-${metrics.tab}-${width}.png`,fullPage:true,animations:'disabled'});
    }
  }
  await page.setViewportSize({width:1366,height:768});
  await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab=calendar');
  await page.waitForFunction(()=>{const roots=document.querySelectorAll('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');return roots.length===1&&roots[0].dataset.workspaceTab==='calendar';});
  await root().getByRole('button',{name:'Nächster Monat',exact:true}).click();
  await root().getByRole('button',{name:'Nächster Monat',exact:true}).click();
  scenes.push(await root().evaluate(root=>({tab:'calendar-six-weeks',days:root.querySelectorAll('.calendar-lg .react-calendar__tile').length,documentHeight:document.documentElement.scrollHeight,height:innerHeight,lastDayBottom:root.querySelector('.calendar-lg .react-calendar__tile:last-child')?.getBoundingClientRect().bottom})));
  await page.screenshot({path:'output/playwright/chrono-workspace-v2-calendar-six-weeks.png',fullPage:true});
  return {scenes,errors};
}
