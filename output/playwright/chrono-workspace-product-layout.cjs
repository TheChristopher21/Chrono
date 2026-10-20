async page => {
  const failures=[],errors=[];page.on('pageerror',error=>errors.push(String(error)));
  const root=page.locator('.admin-workspace:visible');
  await root.locator('.aw-header').waitFor();
  for(const theme of ['light','dark']){
    await page.evaluate(theme=>{document.documentElement.dataset.theme=theme;document.documentElement.style.colorScheme=theme;localStorage.setItem('theme',theme);},theme);
    for(const width of [1440,1024,736,390,320]){
      await page.setViewportSize({width,height:1050});
      const result=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,viewport:innerWidth}));
      if(result.scroll>width+2)failures.push({theme,width,...result});
      await page.screenshot({path:`output/playwright/chrono-workspace-product-${width}-${theme}.png`,fullPage:true});
    }
  }
  await page.setViewportSize({width:1440,height:1050});
  await page.evaluate(()=>{document.documentElement.dataset.theme='light';document.documentElement.style.colorScheme='light';});
  return{failures,errors};
}
