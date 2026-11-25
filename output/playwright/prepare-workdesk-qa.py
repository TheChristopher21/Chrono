from pathlib import Path
p = Path('C:/Users/siefe/WebstormProjects/Chrono/output/playwright')
s = (p / 'chrono-admin-balanced-layout-qa.cjs').read_text(encoding='utf-8')
s = s.replace("['08-agenda']", "['09-arbeitsplatz']").replace('[1056, 768, 352]', '[1472, 1056, 768, 352]')
(p / 'chrono-admin-workdesk-layout-qa.cjs').write_text(s, encoding='utf-8')
