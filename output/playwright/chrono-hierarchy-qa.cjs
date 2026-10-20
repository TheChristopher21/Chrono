async page => {
  const checks = [];
  const check = (value, label) => { checks.push({label, passed:!!value}); if (!value) throw new Error(label); };
  const root = () => page.locator('[data-workspace-pane]:not([aria-hidden]) .admin-workspace');
  const center = () => root().locator('.aw-requests');
  const nav = () => root().getByRole('navigation', {name:'Admin-Dashboard Bereiche'});
  const noOverflow = () => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1);
  const theme = async desired => {
    const button = page.getByRole('navigation', {name:'Hauptnavigation'}).getByRole('button', {name:desired==='light'?'Light Mode':'Dark Mode', exact:true});
    if (await button.isVisible()) await button.click();
  };
  await page.setViewportSize({width:1440,height:1050});
  await nav().getByRole('button', {name:'Übersicht',exact:true}).click();
  await root().locator('.aw-metric').first().waitFor();
  check(await root().locator('.aw-metric').count() === 4, 'Four distinct metric cards');
  await page.screenshot({path:'output/playwright/chrono-hierarchy-overview-dark.png',fullPage:true});
  await root().getByRole('button',{name:'Offene Anträge anzeigen'}).click();
  check(await root().getByRole('button',{name:'Anträge 4',exact:true}).last().getAttribute('aria-pressed') === 'true', 'Metric filters the task list');
  await nav().getByRole('button', {name:'Anträge 4',exact:true}).click();
  await center().getByRole('button',{name:/Nina Keller Zeitkorrektur/}).click();
  check(await center().locator('.awr-change').count()===3,'All three grouped corrections are visible');
  check(await center().locator('.awr-reason').count()===1,'Shared reason appears once');
  await center().getByRole('textbox',{name:'Kommentar zur Entscheidung',exact:true}).fill('Prüfnotiz bleibt erhalten');
  await center().getByRole('button',{name:'Nächster Antrag',exact:true}).click();
  await center().getByRole('button',{name:'Vorheriger Antrag',exact:true}).click();
  check(await center().getByRole('textbox',{name:'Kommentar zur Entscheidung',exact:true}).inputValue()==='Prüfnotiz bleibt erhalten','Request navigation preserves note');
  for(const [width,height] of [[1920,1080],[1440,900],[1366,768],[1280,720],[1024,900]]){
    await page.setViewportSize({width,height});
    check(await noOverflow(),`Requests fit ${width}px width`);
    if(width>=1100){
      check(await center().getByRole('button',{name:'Genehmigen',exact:true}).evaluate(e=>{const r=e.getBoundingClientRect();return r.bottom<=innerHeight&&r.top>=0;}),`Decision visible at ${width}x${height}`);
    }
    if(width===1920||width===1366)await page.screenshot({path:`output/playwright/chrono-hierarchy-requests-${width}-dark.png`});
  }
  await page.setViewportSize({width:1366,height:768});
  await theme('light');
  await page.screenshot({path:'output/playwright/chrono-hierarchy-requests-light.png'});
  await center().getByRole('button',{name:/Weitere Filter/}).click();
  await center().getByLabel('Datum eingrenzen',{exact:true}).fill('2026-10-05');
  check(await center().locator('.awr-row').count()===1,'Date filter keeps entire correction group');
  await center().getByLabel('Datum eingrenzen',{exact:true}).fill('');
  await center().getByRole('button',{name:/Weitere Filter/}).click();
  await center().getByRole('button',{name:/Mirjam Burkart Zeitkorrektur/}).click();
  await page.setViewportSize({width:390,height:844});
  await center().getByRole('button',{name:'Nächster Antrag',exact:true}).click();
  await center().getByRole('button',{name:'Vorheriger Antrag',exact:true}).click();
  check(await noOverflow(),'Mobile detail fits width');
  check(await center().getByRole('button',{name:'Genehmigen',exact:true}).evaluate(e=>e.getBoundingClientRect().bottom<=innerHeight),'Mobile approval stays visible');
  await page.screenshot({path:'output/playwright/chrono-hierarchy-requests-mobile.png'});
  await center().getByRole('button',{name:'Genehmigen',exact:true}).click();
  await center().locator('.awr-feedback').filter({hasText:'Antrag genehmigt'}).waitFor();
  check(await center().locator('.awr-row').filter({hasText:'Mirjam Burkart'}).count()===0,'Approval updates inbox without reload');
  await center().getByRole('button',{name:'Zur Antragsliste',exact:false}).click();
  await nav().getByRole('button', {name:'Übersicht',exact:true}).click();
  for (const width of [390,320,1024,1366]){
    await page.setViewportSize({width,height:width<500?844:900});
    check(await noOverflow(),`Overview fits ${width}px width`);
    if(width===390)await page.screenshot({path:'output/playwright/chrono-hierarchy-overview-mobile.png',fullPage:true});
  }
  await page.setViewportSize({width:1440,height:1050});
  await page.screenshot({path:'output/playwright/chrono-hierarchy-overview-light.png',fullPage:true});
  return checks;
}
