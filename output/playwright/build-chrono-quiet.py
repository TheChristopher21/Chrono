from pathlib import Path

root = Path('C:/Users/siefe/.codex/visualizations/2026/09/14/01a09f5e-a192-7013-b8e6-2aa8ccfd53d0')
work = Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
source = (root / 'chrono-admin-06-klar-planung.html').read_text(encoding='utf-8')
source = source.replace('chrono-klar-planung', 'chrono-ruhig')
source = source.replace('Chrono Admin · Klar & Planung', 'Chrono Admin · Ruhige Übersicht')
start = source.index('<header class="cp-chrome">')
end = source.index('<nav class="cp-workspace"', start)
source = source[:start] + (work / 'chrono-quiet-chrome.html').read_text(encoding='utf-8') + source[end:]
source = source.replace('function render(){const titles=', 'function renderBase(){const titles=', 1)
start = source.index('function overview()')
end = source.index('function planDetail(', start)
source = source[:start] + (work / 'chrono-quiet-overview.js').read_text(encoding='utf-8') + '\n' + source[end:]
source = source.replace('<h2>Diese Woche</h2>', "<h2>${state.week===0?'Diese Woche':state.week===1?'Nächste Woche':'Kalenderwoche '+planWeekNumber(dates[0])}</h2>")
start = source.index('function customization(){open(\'Dashboard anpassen\'')
end = source.index('function modulePreview(', start)
source = source[:start] + source[end:]
source = source.replace('</style>', (work / 'chrono-quiet.css').read_text(encoding='utf-8') + '\n</style>', 1)
output = root / 'chrono-admin-07-ruhig.html'
output.write_text(source, encoding='utf-8')
print(output)
print(len(source.encode('utf-8')))
