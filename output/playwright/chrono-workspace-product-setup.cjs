async page => {
  const names=[['Mirjam','Burkart','Atelier'],['Luca','Meier','Atelier'],['Nina','Keller','Atelier'],['Jonas','Weber','Beratung'],['Lea','Sommer','Beratung'],['Tim','Frei','Beratung']];
  const people=names.map(([firstName,lastName,departmentName],index)=>({id:index+64,username:firstName,firstName,lastName,departmentName,annualVacationDays:25,vacationDaysRemaining:12,workPercentage:100,expectedWorkDays:5,dailyWorkHours:8,weeklyWorkingHours:40,includeInTimeTracking:true,color:'#6756df',companyId:7,company:{id:7,cantonAbbreviation:'SG'},companyCantonAbbreviation:'SG',roles:['ROLE_USER'],entryDate:'2026-01-01'}));
  const admin={id:1,username:'preview-superadmin',firstName:'Super',lastName:'Admin',roles:['ROLE_SUPERADMIN'],isSuperAdmin:true,isAdmin:true,includeInTimeTracking:false,companyId:7,company:{id:7,cantonAbbreviation:'SG'},companyFeatureKeys:[],pagePermissions:{adminDashboard:'MANAGE'}};
  const vacations=[{id:12,username:'Luca',startDate:'2026-09-23',endDate:'2026-09-25',requestDate:'2026-09-12',approved:false,denied:false},{id:13,username:'Lea',startDate:'2026-12-24',endDate:'2026-12-31',requestDate:'2026-09-09',approved:false,denied:false},{id:14,username:'Mirjam',startDate:'2026-09-21',endDate:'2026-09-25',approved:true,denied:false},{id:15,username:'Nina',startDate:'2026-09-28',endDate:'2026-09-30',approved:true,usesOvertime:true,denied:false}];
  const corrections=[{id:21,username:'Mirjam',requestDate:'2026-09-11',reason:'Arbeitsende nachgetragen',originalTimestamp:'2026-09-11T16:30:00',desiredTimestamp:'2026-09-11T16:45:00',originalPunchType:'ENDE',desiredPunchType:'ENDE',approved:false,denied:false},{id:22,username:'Nina',requestDate:'2026-09-10',reason:'Stempelung vergessen',originalTimestamp:null,desiredTimestamp:'2026-09-10T17:00:00',desiredPunchType:'ENDE',approved:false,denied:false}];
  const sick=[{id:31,username:'Jonas',startDate:'2026-09-14',endDate:'2026-09-16',comment:''}];
  const summaries=people.map((person,index)=>({username:person.username,date:'2026-09-14',workedMinutes:index===0?255:480,expectedMinutes:480,breakMinutes:30,needsCorrection:index===0,primaryTimes:{isOpen:index===0,firstStartTime:'08:00',lastEndTime:index===0?null:'17:00'},entries:[{id:index*2+100,timestamp:'2026-09-14T08:00:00',punchType:'START'},...(index===0?[]:[{id:index*2+101,timestamp:'2026-09-14T17:00:00',punchType:'ENDE'}])]}));
  const preferences={};let nextId=1000;const writes=[];
  await page.unroute('**/api/**');
  await page.route('**/api/**',async route=>{
    const req=route.request(),path=req.url().replace(/^https?:\/\/[^/]+/,'').split('?')[0].replace(/^\/api\/api\//,'/api/');
    const params=Object.fromEntries((req.url().split('?')[1]||'').split('&').filter(Boolean).map(pair=>pair.split('=').map(value=>decodeURIComponent(value.replace(/\+/g,' ')))));
    if(req.method()!=='GET')writes.push({method:req.method(),path,body:req.postData()});
    let data=[];
    if(path.endsWith('/auth/me'))data=admin;
    else if(path.endsWith('/admin/users'))data=people;
    else if(path.endsWith('/vacation/all'))data=vacations;
    else if(path.endsWith('/vacation/remaining'))data=12;
    else if(path.endsWith('/correction/all'))data=corrections;
    else if(path.endsWith('/sick-leave/company'))data=sick;
    else if(path.endsWith('/all-summaries'))data=summaries;
    else if(path.endsWith('/tracking-balances'))data=people.map((person,index)=>({username:person.username,trackingBalance:[-465,135,540,-30,120,-438][index]}));
    else if(path.includes('/preferences/')){if(req.method()==='PUT')preferences[path]={...req.postDataJSON(),revision:(preferences[path]?.revision||0)+1};data=preferences[path]||{schemaVersion:1,revision:0,payload:{}};}
    else if(path.endsWith('/holidays/details'))data={};
    else if(path.endsWith('/admin/period-summary'))data=people.map(person=>({username:person.username,workedMinutes:480,expectedMinutes:480,differenceMinutes:0,calculationStatus:'READY',dailySummaries:summaries.filter(day=>day.username===person.username)}));
    else if(path.endsWith('/period-summary'))data={workedMinutes:480,expectedMinutes:480,differenceMinutes:0,calculationStatus:'READY',dailySummaries:summaries.filter(day=>day.username===params.username)};
    else if(path.endsWith('/changelog/latest'))data={id:0,version:'local',content:''};
    else if(path.endsWith('/vacation/adminCreate')){data={...params,id:nextId++,approved:true,denied:false};vacations.push(data);}
    else if(/\/vacation\/\d+$/.test(path)&&req.method()==='PUT'){const row=vacations.find(v=>v.id===Number(path.split('/').pop()));Object.assign(row,req.postDataJSON());data=row;}
    else if(/\/correction\/(approve|deny)\/\d+$/.test(path)){const row=corrections.find(v=>v.id===Number(path.split('/').pop()));row.approved=path.includes('/approve/');row.denied=!row.approved;data=row;}
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  await page.addInitScript(()=>{if(location.hostname!=='127.0.0.1')return;localStorage.setItem('token','local-preview-only');localStorage.setItem('lastActivityAt',String(Date.now()));sessionStorage.setItem('chrono:tabLastActivityAt',String(Date.now()));sessionStorage.removeItem('chrono:tabIdleSignOut');localStorage.setItem('theme','light');localStorage.setItem('language','de');});
  await page.setViewportSize({width:1440,height:1050});
  await page.goto('http://127.0.0.1:5173/admin/dashboard-neu');
  await page.waitForSelector('.admin-workspace .aw-header');
  return {url:page.url(),fixtureEmployees:people.length};
}
