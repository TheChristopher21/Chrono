async page => {
  const companies=[{id:20,name:'Atelier Zürich',city:'Zürich',userCount:1,active:true,enabledFeatures:['roster','payroll','analytics']},{id:30,name:'Beratung Bern',city:'Bern',userCount:1,active:true,enabledFeatures:['roster','payroll','analytics']}];
  const base={id:1,username:'root-preview',firstName:'Super',lastName:'Admin',roles:['ROLE_SUPERADMIN'],includeInTimeTracking:false,companyId:null,companyFeatureKeys:[],pagePermissions:{}};
  const employees={20:{id:201,username:'Alice',firstName:'Alice',lastName:'Alpha',departmentName:'Atelier',companyId:20},30:{id:301,username:'Bob',firstName:'Bob',lastName:'Beta',departmentName:'Beratung',companyId:30}};
  const prefs={};
  const employee=id=>({...employees[id],roles:['ROLE_USER'],annualVacationDays:25,vacationDaysRemaining:12,workPercentage:100,expectedWorkDays:5,dailyWorkHours:8,weeklyWorkingHours:40,includeInTimeTracking:true,companyCantonAbbreviation:'ZH',entryDate:'2026-01-01'});
  await page.route('**/api/**',async route=>{
    const req=route.request(), path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0];
    const company=companies.find(company=>String(company.id)===req.headers()['x-chrono-company']);
    let data=[];
    if(path==='/api/auth/me')data={...base,...(company?{companyId:company.id,selectedCompanyId:company.id,selectedCompanyName:company.name,companyFeatureKeys:company.enabledFeatures}:{})};
    else if(path==='/api/superadmin/companies')data=companies;
    else if(path.includes('/preferences/')){const key=`${company?.id}:${path}`;if(req.method()==='PUT')prefs[key]={...req.postDataJSON(),revision:(prefs[key]?.revision||0)+1};data=prefs[key]||{schemaVersion:1,revision:0,payload:{}};}
    else if(path==='/api/admin/users') { if(!company)return route.fulfill({status:403,json:{message:'Firma fehlt'}}); data=[employee(company.id)]; }
    else if(path.startsWith('/api/users/')) { const person=employee(company?.id); if(path.split('/').pop()!==person.username)return route.fulfill({status:403,json:{message:'Andere Firma'}}); data=person; }
    else if(path==='/api/correction/all')data=company?[{id:company.id,username:employee(company.id).username,requestDate:'2026-10-08',desiredTimestamp:'2026-10-08T17:15:00',desiredPunchType:'ENDE',originalTimestamp:'2026-10-08T17:00:00',originalPunchType:'ENDE',reason:`Korrektur ${company.name}`,approved:false,denied:false}]:[];
    else if(path.endsWith('/tracking-balances'))data=company?[{username:employee(company.id).username,trackingBalance:-60}]:[];
    else if(path==='/api/vacation/remaining')data=12;
    else if(path==='/api/holidays/details')data={};
    else if(path==='/api/changelog/latest')data={id:0,version:'test',content:''};
    else if(path.endsWith('/period-summary')&&!path.includes('/admin/period-summary'))data={workedMinutes:480,expectedMinutes:480,differenceMinutes:0,calculationStatus:'READY',dailySummaries:[]};
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  const token=await page.evaluate(()=>'header.'+btoa(JSON.stringify({sub:'root-preview',exp:Math.floor(Date.now()/1000)+3600}))+'.signature');
  await page.addInitScript(token=>{localStorage.setItem('token',token);localStorage.setItem('lastActivityAt',String(Date.now()));sessionStorage.setItem('chrono:tabLastActivityAt',String(Date.now()));sessionStorage.removeItem('chrono:tabIdleSignOut');localStorage.setItem('language','de');localStorage.setItem('theme','light');},token);
  await page.setViewportSize({width:1366,height:900});
  await page.goto('http://127.0.0.1:5173/admin/dashboard-neu');
  await page.getByRole('heading',{name:'Firma auswählen',exact:true}).waitFor();
  return {url:page.url(),companies:companies.map(company=>company.name)};
}
