from pathlib import Path
import re

root = Path('C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0')
work = Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
source = (root / 'chrono-admin-08-agenda.html').read_text(encoding='utf-8')
source = source.replace('chrono-agenda', 'chrono-arbeitsplatz').replace('Chrono Admin · Agenda und Aufgaben', 'Chrono Admin · Arbeitsübersicht')
start = source.index('<header class="cp-chrome">')
end = source.index('<nav class="cp-workspace"', start)
source = source[:start] + (work / 'chrono-workdesk-chrome.html').read_text(encoding='utf-8') + source[end:]
start = source.index('function overview()')
end = source.index('function quietMore()', start)
source = source[:start] + (work / 'chrono-workdesk-overview.js').read_text(encoding='utf-8') + '\n' + (work / 'chrono-review-detail.js').read_text(encoding='utf-8') + '\n' + source[end:]
source = source.replace('</style>', (work / 'chrono-workdesk.css').read_text(encoding='utf-8') + '\n' + (work / 'chrono-review-detail.css').read_text(encoding='utf-8') + '\n</style>', 1)
source = source.replace('function decision(id,status){', 'function decision(id,status){const wasDesk=state.view===\'overview\';')
source = source.replace("render();toast(`${a.type} von ${people[a.p].name} ${status.toLowerCase()}.`)", "if(wasDesk){state.deskSelected=null;state.deskFeedback=`${a.type} von ${people[a.p].name} ${status.toLowerCase()}.`;q('.cp-sheet').hidden=true;q('.cp-content').hidden=false}render();if(!wasDesk)toast(`${a.type} von ${people[a.p].name} ${status.toLowerCase()}.`)")
source = source.replace("const modules=", "people[1].leaveRemaining=12;people[1].leaveBalanceAsOf='2026-09-14';\nconst modules=", 1)
source = source.replace("const state={", """// Explicit example metadata for the redesigned work queue.
people[0].issueDate='2026-09-14';people[0].entries.pop();
people[1].issueDate='2026-09-11';people[1].entries=[];
people[2].issueDate='2026-09-09';people[3].issueDate='2026-09-10';
people[4].issueStart='2026-09-07';people[4].issueEnd='2026-09-13';
people.forEach((p,i)=>p.updatedAt=['2026-09-14T09:42','2026-09-14T09:05','2026-09-12T15:00','2026-09-13T17:00','2026-09-11T16:00','2026-09-10T09:00'][i]);
requests.forEach((a,i)=>a.updatedAt=['2026-09-14T09:20','2026-09-14T09:10','2026-09-14T08:45','2026-09-13T11:00','2026-09-09T10:00'][i]);
const state={""", 1)
source = source.replace("render();const tweaks={density:18,corners:12};", "render();const tweaks={density:18,corners:12};")
source = source.replace("--cp-gap',tweaks.density", "--cw-gap',tweaks.density")
source = source.replace("--cp-radius',tweaks.corners", "--cw-radius',tweaks.corners")
# Make the example request meaningfully overlap a colleague's existing vacation.
source = source.replace("id:2,p:1,type:'Urlaub',date:'2026-10-05',end:'2026-10-09'", "id:2,p:1,type:'Urlaub',date:'2026-09-23',end:'2026-09-25'")
source = source.replace("const order=['p0','r1','r2','p1','r3','p3','r4','p2','p4'];return [...rs,...ps].sort((a,b)=>(order.indexOf(a.key)<0?99:order.indexOf(a.key))-(order.indexOf(b.key)<0?99:order.indexOf(b.key)))", "return [...rs,...ps].sort((a,b)=>(b.a.updatedAt||'').localeCompare(a.a.updatedAt||''))")
source = source.replace(":a.team+' · Zeitprüfung';return", ":(a.issueDate?deskShortDate(a.issueDate):a.issueStart?deskDateRange(a.issueStart,a.issueEnd):a.team)+' · Zeitprüfung';return")
source = source.replace('Offene Anträge und ungeklärte Zeiten</p>', 'Anträge und Zeitprüfung · Neueste zuerst</p>')
source = source.replace('class="cw-absence" data-act="eventedit"', 'class="cw-absence" data-act="desk-absence"')
source = source.replace("if(a==='desk-review'){", "if(a==='desk-absence'){deskAbsenceDetails(Number(b.dataset.id))}else if(a==='desk-review'){")
source = source.replace('<div class="cw-plan-actions">', '${coming.length>3?`<p class="cw-more-absences">3 von ${coming.length} kommenden Abwesenheiten</p>`:\'\'}<div class="cw-plan-actions">')
source = source.replace("{month:'short'})}</small>`:avatar", "{month:'short'})}</small></span>`:avatar")
source = source.replace('data-act="quiet-balances"><small>Ø Team-Saldo', 'data-act="desk-balances"><small>Ø Team-Saldo')
source = source.replace('data-act="alltime"><small>Negative Zeitkonten', 'data-act="desk-negative"><small>Negative Zeitkonten')
source = source.replace("if(a==='desk-absence'){", "if(a==='desk-balances'||a==='desk-negative'){deskBalances(a==='desk-negative')}else if(a==='desk-absence'){")
output = root / 'chrono-admin-09-arbeitsplatz.html'
output.write_text(source, encoding='utf-8')
print(output)
print(len(source.encode('utf-8')))
