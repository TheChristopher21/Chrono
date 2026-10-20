async(page)=>{
 const f=page.frames().find(x=>x!==page.mainFrame());
 const data=await f.evaluate(()=>['html','body','[id^="chrono-"]','main'].map(s=>{const e=document.querySelector(s),c=getComputedStyle(e),r=e.getBoundingClientRect();return {s,r:r.toJSON(),scrollHeight:e.scrollHeight,overflow:c.overflow,contentVisibility:c.contentVisibility,position:c.position,height:c.height,maxHeight:c.maxHeight}}));
 await page.setViewportSize({width:352,height:2450});
 await page.locator('iframe').evaluate(e=>e.style.height='2400px');
 await page.screenshot({path:'output/playwright/chrono-admin-mobile-full.png',fullPage:true});
 return data;
}
