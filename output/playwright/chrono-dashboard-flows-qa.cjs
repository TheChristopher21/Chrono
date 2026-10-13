async page => {
  const errors=[], checks=[];
  page.on('pageerror', error=>errors.push(String(error)));
  const assert=(value,label)=>{if(!value)throw new Error(label);checks.push(label);};
  const sidebar=()=>page.locator('[data-workspace-pane]:not([aria-hidden]) .aw-sidebar:visible');
  const desk=()=>page.locator('.aw-requests:visible');
  const tabCount=async()=>await page.locator('.workspace-mobile-trigger:visible').count()
    ? Number(await page.locator('.workspace-mobile-trigger:visible small').innerText())
    : page.locator('.workspace-tab:visible').count();
  const openSection=async(label,id)=>{
    await sidebar().getByRole('button',{name:label,exact:typeof label==='string'}).click();
    await page.locator(`[data-workspace-pane]:not([aria-hidden]) [data-workspace-tab="${id}"]:visible`).waitFor();
  };
  await page.setViewportSize({width:1366,height:768});
  await openSection('Kalender','calendar');
  await page.locator('.month-navigation:visible').getByRole('button',{name:'Nächster Monat',exact:true}).click();
  const month=await page.locator('.month-navigation:visible').innerText();
  await openSection(/^Anträge/,'requests');
  await desk().getByRole('textbox',{name:'Kommentar zur Entscheidung'}).fill('Entwurf bleibt beim Wechsel erhalten');
  await openSection('Kalender','calendar');
  assert(await page.locator('.month-navigation:visible').innerText()===month,'Kalendermonat bleibt erhalten');
  await openSection(/^Anträge/,'requests');
  assert(await desk().getByRole('textbox',{name:'Kommentar zur Entscheidung'}).inputValue()==='Entwurf bleibt beim Wechsel erhalten','Antragsnotiz bleibt erhalten');
  await desk().getByRole('button',{name:'Mitarbeiterprofil',exact:true}).click();
  await page.getByRole('link',{name:'Zurück zum Dashboard',exact:true}).waitFor();
  await sidebar().waitFor();
  assert(await sidebar().isVisible(),'Mitarbeiterprofil behält die Seitenleiste');
  await page.getByRole('link',{name:'Zurück zum Dashboard',exact:true}).click();
  await page.locator('[data-workspace-tab="requests"]:visible').waitFor();
  assert(page.url().includes('tab=requests'),'Profil kehrt direkt zu den Anträgen zurück');
  assert(await desk().getByRole('textbox',{name:'Kommentar zur Entscheidung'}).inputValue()==='Entwurf bleibt beim Wechsel erhalten','Profilbesuch erhält den Entwurf');
  for(const label of ['Dienstplan','Lohnabrechnungen verwalten','Analytics']) {
    await sidebar().getByRole('link',{name:label,exact:true}).click();
    await page.locator('.aw-module-workspace:visible .aw-module-content').waitFor();
    assert(await tabCount()===1,`${label}: ein Tab`);
    assert(await page.locator('.chrono-navbar-shell:visible').count()===1,`${label}: eine Navbar`);
  }
  await page.reload();
  await sidebar().waitFor();
  assert(await tabCount()===1,'Modul-Neuladen behält einen Dashboard-Arbeitsbereich');
  await openSection('Zeitprüfung','time');
  await openSection('Mitarbeitende','employees');
  await openSection(/^Anträge/,'requests');
  assert(await tabCount()===1,'Alle Hauptbereiche nach Modul-Neuladen im selben Tab');
  const viewportCheck=async label=>{
    const bounds=await desk().getByRole('button',{name:'Genehmigen',exact:true}).boundingBox();
    assert(bounds&&bounds.y>=0&&bounds.y+bounds.height<=page.viewportSize().height,`${label}: Freigabe ohne Seitenscroll sichtbar`);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${label}: kein horizontaler Überlauf`);
  };
  await viewportCheck('Laptop 1366×768');
  await page.screenshot({path:'output/playwright/chrono-dashboard-flow-desktop.png'});
  await page.getByRole('button',{name:'Neuen Tab öffnen',exact:true}).click();
  await page.getByRole('dialog',{name:'Neuen Tab öffnen'}).getByRole('button',{name:'Plan Dienstplan Schichten und Einsatzplanung steuern.',exact:true}).click();
  assert(await tabCount()===2,'Plus öffnet bewusst einen zweiten Tab');
  await page.locator('.workspace-tab:visible').last().locator('.workspace-tab-close').click();
  assert(await tabCount()===1,'Zusatz-Tab lässt sich schließen');
  await page.setViewportSize({width:390,height:844});
  await desk().getByRole('list',{name:'Antragsliste'}).getByRole('button').first().click();
  assert(!(await desk().locator('.awr-list-panel').isVisible()),'Mobil wird direkt der gewählte Antrag gezeigt');
  await viewportCheck('Mobil 390×844');
  await page.screenshot({path:'output/playwright/chrono-dashboard-flow-mobile.png'});
  const person=await desk().locator('.awr-detail-heading h3').innerText();
  await desk().getByRole('button',{name:'Nächster Antrag',exact:true}).click();
  assert(await desk().locator('.awr-detail-heading h3').innerText()!==person,'Nächster Antrag direkt aus den Details');
  await desk().getByRole('button',{name:'Vorheriger Antrag',exact:true}).click();
  await desk().getByRole('button',{name:/Zur Antragsliste/}).click();
  assert(await desk().locator('.awr-list-panel').isVisible(),'Zurück zur Antragsliste ohne Scrollsuche');
  await desk().getByRole('list',{name:'Antragsliste'}).getByRole('button').first().click();
  await desk().getByRole('button',{name:'Genehmigen',exact:true}).click();
  await desk().getByRole('status').waitFor();
  assert(await desk().locator('.awr-detail-heading h3').innerText()!==person,'Nach Freigabe folgt der nächste offene Antrag');
  assert(await tabCount()===1,'Auch Freigabe erzeugt keinen Tab');
  assert(!errors.length,`Keine Browserfehler: ${errors.join('; ')}`);
  return {checks,errors};
}
