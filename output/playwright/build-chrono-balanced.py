from pathlib import Path

root = Path('C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0')
work = Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
source = (root / 'chrono-admin-07-ruhig.html').read_text(encoding='utf-8')
source = source.replace('chrono-ruhig', 'chrono-agenda').replace('Chrono Admin · Ruhige Übersicht', 'Chrono Admin · Agenda und Aufgaben')
start = source.index('function overview()')
end = source.index('function render(){renderBase()', start)
source = source[:start] + (work / 'chrono-balanced-overview.js').read_text(encoding='utf-8') + '\n' + source[end:]
start = source.index("function customization(){open('Ansicht anpassen',`<div class=\"cq-settings\"><label class=\"cp-check\"><input type=\"checkbox\" data-quiet-setting=\"quietTasks\" ${state.quietTasks!==false?'checked':''}>Offene Aufgaben")
end = source.index("r.addEventListener('click'", start)
source = source[:start] + source[end:]
source = source.replace('</style>', (work / 'chrono-balanced.css').read_text(encoding='utf-8') + '\n</style>', 1)
source = source.replace('font-size:10px;flex:none', 'font-size:11px;flex:none')
output = root / 'chrono-admin-08-agenda.html'
output.write_text(source, encoding='utf-8')
print(output)
print(len(source.encode('utf-8')))
