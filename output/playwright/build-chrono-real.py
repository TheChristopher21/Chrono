from pathlib import Path
import base64

root=Path('C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0')
work=Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
source=(root/'chrono-admin-09-arbeitsplatz.html').read_text(encoding='utf-8')
source=source.replace('chrono-arbeitsplatz','chrono-browser-design').replace('Chrono Admin · Arbeitsübersicht','Chrono · Admin-Dashboard – Designvorschau')
logo=Path('C:/Users/siefe/WebstormProjects/Chrono/Chrono-frontend/public/img/komplettesLogo.png').read_bytes()
chrome=(work/'chrono-real-chrome.html').read_text(encoding='utf-8').replace('__CHRONO_LOGO__','data:image/png;base64,'+base64.b64encode(logo).decode())
start=source.index('<header class="cp-chrome">');end=source.index('<nav class="cp-workspace"',start)
source=source[:start]+chrome+source[end:]
def replace_function(text,name,next_marker,replacement):
    begin=text.index('function '+name+'(');finish=text.index(next_marker,begin)
    return text[:begin]+replacement+'\n'+text[finish:]
source=replace_function(source,'workspace','function renderBase()', '')
source=replace_function(source,'account','function decision(', '')
source=source.replace('function render(){renderBase();','function baseDeskRender(){renderBase();',1)
source=source.replace('function open(title,html){','function legacyOpen(title,html){',1).replace('function close(redraw=true){','function legacyClose(redraw=true){',1)
source=source.replace("workspace:[{title:'Admin-Dashboard',pin:true},{title:'Mirjam Burkart',pin:false}]", "workspace:[{title:'Admin-Start',pin:true},{title:'Mirjam Burkart',person:0,pin:false}]")
source=source.replace("theme:'auto',role:'Admin'", "theme:'auto',role:'Super Admin'")
source=source.replace("btn('person','Übersicht'", "btn('real-person','Übersicht'")
source=source.replace("btn('person','Mitarbeiterübersicht'", "btn('real-person','Mitarbeitendenübersicht'")
source=source.replace('function requestDetail(a){','function oldRequestDetail(a){',1)
source=source.replace('requestDetail(sel)','realRequestDetail(sel)')
source=source.replace("const wasDesk=state.view==='overview';", "const wasDesk=state.view==='overview';")
source=source.replace("if(wasDesk){state.deskSelected=null;", "if(wasDesk){state.activeWorkspace=0;state.deskSelected=null;")
# Keep the same functional calendar and save handler; exchange only the form view.
begin=source.index('function renderVacation()');end=source.index('function draftPeriod()',begin)
source=source[:begin]+(work/'chrono-real-vacation.js').read_text(encoding='utf-8')+'\n'+source[end:]
source=source.replace("render();const tweaks=", (work/'chrono-real-shell.js').read_text(encoding='utf-8')+'\n'+(work/'chrono-real-employee.js').read_text(encoding='utf-8')+'\nrender();const tweaks=',1)
css=(work/'chrono-real-shell.css').read_text(encoding='utf-8')+'\n'+(work/'chrono-real-vacation.css').read_text(encoding='utf-8')+'\n'+(work/'chrono-real-employee.css').read_text(encoding='utf-8')
source=source.replace('</style>',css+'\n</style>',1)
source=source.replace('Chris Siefert','Super Admin').replace('Guten Morgen, Chris.','Guten Morgen.').replace('Aare Atelier AG','Chrono')
source=source.replace('Schließen','Schliessen').replace('schließen','schliessen').replace('Beispieldaten · lokal ausprobieren','Designvorschau')
source=source.replace("function workspace(){", "function renderWorkspaceTabs(){",1)
source=source.replace("function render(){baseDeskRender();", "function workspace(){renderWorkspaceTabs();icons()}\nfunction render(){baseDeskRender();q('.cp-page-head').hidden=!q('.cp-sheet').hidden;",1)
source=source.replace("function open(title,html){q('.cp-content')", "function open(title,html){q('.cp-page-head').hidden=true;q('.cp-content')",1)
source=source.replace("function close(redraw=true){q('.cp-sheet')", "function close(redraw=true){q('.cp-page-head').hidden=false;q('.cp-sheet')",1)
source=source.replace('font-weight: 600;', 'font-weight: 500;')
source=source.replace('''#chrono-browser-design .cp-vac-calendar .cp-cal-day:focus-visible {
  position: relative;
  outline: 2px solid var(--cp-accent);
  outline-offset: -3px;
}
''','')
out=root/'chrono-admin-10-browser.html';out.write_text(source,encoding='utf-8')
print(out);print(len(source.encode('utf-8')))
