async (page) => {
  const employee = {id:64, username:'Mirjam', firstName:'Mirjam', lastName:'Burkart', annualVacationDays:25, workPercentage:80, isPercentage:true, expectedWorkDays:5, dailyWorkHours:8, color:'#6d5ce7', companyId:7, company:{id:7,cantonAbbreviation:'SG'}, roles:['ROLE_PERCENTAGE'], entryDate:'2026-04-01'};
  const admin = {id:1, username:'preview-admin', firstName:'Vorschau', lastName:'Admin', roles:['ROLE_SUPERADMIN'], isSuperAdmin:true, isAdmin:true, companyId:7, company:{id:7,cantonAbbreviation:'SG'}, companyFeatureKeys:[], pagePermissions:{adminDashboard:'MANAGE'}};
  const vacations = [];
  let preference = {revision:0, tabs:[], activeTabId:null};
  let nextId = 9000;
  await page.unroute('**/api/**');
  await page.route('**/api/**', async route => {
    const req = route.request();
    const path = req.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0].replace(/^\/api\/api\//, '/api/');
    let data = [];
    if (path.endsWith('/auth/me')) data = admin;
    else if (path.endsWith('/admin/users')) data = [employee];
    else if (path.endsWith('/vacation/all')) { await page.waitForTimeout(300); data = vacations; }
    else if (path.endsWith('/vacation/adminCreate')) {
      const p = Object.fromEntries((req.url().split('?')[1] || '').split('&').filter(Boolean).map(pair=>pair.split('=').map(decodeURIComponent)));
      data = {...p, id:nextId++, approved:true, denied:false, halfDay:p.halfDay==='true', usesOvertime:p.usesOvertime==='true', overtimeDeductionMinutes:p.overtimeDeductionMinutes ? Number(p.overtimeDeductionMinutes) : null, color:employee.color};
      vacations.push(data);
    }
    else if (path.includes('/preferences/')) {
      if (req.method()==='PUT') preference = {...req.postDataJSON(), revision:preference.revision+1};
      data = preference;
    }
    else if (path.endsWith('/holidays/details')) data = {};
    else if (path.endsWith('/period-summary')) data = {workedMinutes:0, expectedMinutes:1920, overtimeMinutes:0, calculationStatus:'READY'};
    else if (path.endsWith('/changelog/latest')) data = {id:0, version:'preview', content:''};
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  await page.addInitScript(() => {
    if (location.hostname !== '127.0.0.1') return;
    localStorage.setItem('token','local-preview-only');
    localStorage.setItem('lastActivityAt',String(Date.now()));
    sessionStorage.setItem('chrono:tabLastActivityAt',String(Date.now()));
    localStorage.setItem('theme','dark');
    localStorage.setItem('language','de');
  });
  await page.setViewportSize({width:1920,height:1080});
  await page.goto('http://127.0.0.1:5173/admin/dashboard/mitarbeiter/Mirjam');
}
