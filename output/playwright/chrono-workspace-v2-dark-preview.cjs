async page => {
  const views=[];
  await page.setViewportSize({width:1366,height:768});
  for(const tab of ['overview','employees','calendar','time']){
    await page.goto('http://127.0.0.1:5173/admin/dashboard-neu?tab='+tab);
    await page.waitForFunction(()=>{const root=document.querySelector('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');return root&&Array.from(root.querySelectorAll('.aw-header button')).some(b=>b.textContent.includes('Urlaub eintragen')&&!b.disabled);});
    await page.locator('nav[aria-label="Hauptnavigation"]:visible').getByRole('button',{name:'Dark Mode',exact:true}).click();
    await page.screenshot({path:`output/playwright/chrono-workspace-v2-${tab}-dark.png`,fullPage:true,animations:'disabled'});
    views.push(await page.evaluate(tab=>({tab,height:document.documentElement.scrollHeight,width:document.documentElement.scrollWidth,theme:document.documentElement.dataset.theme}),tab));
  }
  return views;
}
