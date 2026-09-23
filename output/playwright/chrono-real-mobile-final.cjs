async(page)=>{
  await page.setViewportSize({width:320,height:1000});
  await page.emulateMedia({colorScheme:'light'});
  await page.goto('http://127.0.0.1:8765/chrono-admin-browser.html');
  await page.locator('[data-act="real-workspace"][data-i="1"]').click();
  const result=await page.locator('.re-metrics').evaluate(el=>({columns:getComputedStyle(el).gridTemplateColumns,labels:[...el.querySelectorAll('dt')].map(x=>({text:x.textContent,height:x.getBoundingClientRect().height})),width:document.documentElement.scrollWidth}));
  if(result.width>320||result.labels.some(x=>x.height>25))throw new Error(JSON.stringify(result));
  await page.screenshot({path:'output/playwright/chrono-admin-10-employee-320.png',fullPage:true});
  return result;
}
