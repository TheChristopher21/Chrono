async (page) => {
  const results = [];
  await page.setViewportSize({width:390,height:844});
  results.push(await page.evaluate(() => ({
    viewport:innerWidth, scrollWidth:document.documentElement.scrollWidth,
    modalWidth:document.querySelector('.vacation-entry-modal').getBoundingClientRect().width,
    mobileNavigationAboveDialog:!!document.elementFromPoint(200,800)?.closest('.mobile-tab-bar')
  })));
  await page.getByRole('dialog').getByRole('button',{name:'Schließen',exact:true}).click();
  for (const width of [390,1920,3794]) {
    await page.setViewportSize({width,height:width===390?844:1080});
    await page.evaluate(() => window.scrollTo(0,0));
    const dimensions = await page.evaluate(() => ({
      viewport:innerWidth, scrollWidth:document.documentElement.scrollWidth,
      mainWidth:document.querySelector('.employee-overview-main').getBoundingClientRect().width,
      mainLeft:document.querySelector('.employee-overview-main').getBoundingClientRect().left
    }));
    if (dimensions.scrollWidth>width || dimensions.mainWidth<width*0.9) throw new Error(JSON.stringify(dimensions));
    results.push(dimensions);
    await page.screenshot({path:`output/playwright/chrono-employee-overview-${width}.png`});
  }
  return results;
}
