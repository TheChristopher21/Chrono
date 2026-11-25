from pathlib import Path

root = Path('C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0')
work = Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
source = (root / 'chrono-admin-01-klar.html').read_text(encoding='utf-8')
source = source.replace('chrono-klar', 'chrono-klar-planung')
source = source.replace('data-concept="klar"', 'data-concept="klar-planung"')
source = source.replace('Chrono Admin · Klar"', 'Chrono Admin · Klar & Planung"')
source = source.replace('Klar · Admin-Dashboard', 'Klar & Planung · Admin-Dashboard')
source = source.replace('const tweaks={density:20,corners:12}', 'const tweaks={density:18,corners:12}')
source = source.replace('Montag, 14. September · Dein Team im Blick', 'Montag, 14. September · Dein Team und die Woche im Blick')
source = source.replace('edit:ev?.id||null,person:', 'contextPerson:p!==null,edit:ev?.id||null,person:')
source = source.replace("p.queue.length||p.edit?'disabled'", "p.contextPerson||p.queue.length||p.edit?'disabled'")
old_widgets = "[{id:'requests',name:'Anträge',visible:true,size:'M'},{id:'people',name:'Kritische Mitarbeitende',visible:true,size:'M'},{id:'agenda',name:'Abwesenheiten & Agenda',visible:true,size:'M'},{id:'modules',name:'Modulzugriff',visible:true,size:'M'}]"
new_widgets = "[{id:'agenda',name:'Teamkalender & Tagesagenda',visible:true,size:'full'},{id:'requests',name:'Anträge',visible:true,size:'M'},{id:'people',name:'Kritische Mitarbeitende',visible:true,size:'M'},{id:'modules',name:'Modulzugriff',visible:true,size:'full'}]"
assert source.count(old_widgets) == 2
source = source.replace(old_widgets, new_widgets)
start = source.index('function overview()')
end = source.index('function requestRows()', start)
source = source[:start] + (work / 'chrono-hybrid-overview.js').read_text(encoding='utf-8') + '\n' + source[end:]
source = source.replace("${index<5?'verfügbar':'im Einsatz'}", "${index<5?'ganztägig verfügbar':'im Einsatz'}")
source = source.replace("if(a==='plan-day'){state.calendarUser='all';return dayDetail(b.dataset.date)}", "if(a==='plan-day')return planDayDetail(b.dataset.date);")
source = source.replace('<span>${slot.text}</span>', '<span class="cp-slot-desktop">${slot.text}</span><span class="cp-slot-mobile">${planCompact(slot)}</span>')
source = source.replace('function planDates()', (work / 'chrono-hybrid-detail.js').read_text(encoding='utf-8') + '\nfunction planDates()', 1)
source = source.replace('</style>', (work / 'chrono-hybrid.css').read_text(encoding='utf-8') + '\n</style>', 1)
output = root / 'chrono-admin-06-klar-planung.html'
output.write_text(source, encoding='utf-8')
print(output)
print(len(source.encode('utf-8')))
