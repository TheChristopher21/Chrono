from pathlib import Path
from html.parser import HTMLParser

work=Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
s=(work/'chrono-admin-workdesk-layout-qa.cjs').read_text(encoding='utf-8').replace('09-arbeitsplatz','10-browser')
(work/'chrono-real-layout-qa.cjs').write_text(s,encoding='utf-8')
s=(work/'chrono-workdesk-flow-qa.cjs').read_text(encoding='utf-8').replace('09-arbeitsplatz','10-browser').replace('chrono-arbeitsplatz','chrono-browser-design')
s=s.replace('.cp-sheet .cp-notice','.cp-vac-saved').replace('.cp-sheet .cp-toolbar','.cp-vac-monthbar')
s=s.replace('chrono-admin-09-review-light.png','chrono-admin-10-review-light.png')
s=s.replace("check(await f.locator('[name=\"person\"]').isVisible(),'Vacation stays open');", "check(await f.locator('.cp-vacation').isVisible(),'Vacation stays open');")
(work/'chrono-real-flow-qa.cjs').write_text(s,encoding='utf-8')
class PreviewParser(HTMLParser):
    def handle_starttag(self,tag,attrs):
        if tag=='iframe': self.document=dict(attrs).get('srcdoc','')
p=PreviewParser();p.feed((work/'chrono-admin-10-browser-preview.html').read_text(encoding='utf-8'))
doc=p.document.replace('<html lang="en"','<html lang="de-CH"')
doc=doc.replace('</head>', '<style>html,body{margin:0!important;padding:0!important}body{background:light-dark(#f5f7fb,#111824)}#chrono-browser-design{min-height:100vh}#chrono-browser-design .cp-layout{min-height:calc(100vh - 112px)}</style></head>')
(work/'chrono-admin-browser.html').write_text(doc,encoding='utf-8')
print('QA and full-window browser preview ready')
