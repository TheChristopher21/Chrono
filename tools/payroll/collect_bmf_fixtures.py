"""Opt-in PROGRAM TESTS only: collect synthetic BMF reference results. Never send employee data.

API terms: https://www.bmf-steuerrechner.de/interface/einganginterface.xhtml
The public test code displayed there is valid until 15 January 2027.
Not invoked by the application or the normal test suite.
"""
import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen
import xml.etree.ElementTree as ET

base = dict(LZZ=2, RE4=500000, STKL=1, KVZ='2.9', PVZ=1, PVS=0, PVA=0, ZKF=0, R=0, af=0, f=1)
cases = [{**base, 'STKL': i} for i in range(1, 7)]
cases += [{**base, 'RE4': amount} for amount in (200001, 581250, 845000, 1000000, 2000000)]
cases += [{**base, 'PVZ': 0, 'PVA': i, 'PVS': 1, 'ZKF': '1.5', 'R': 1} for i in (0, 1, 4)]
cases += [{**base, 'STKL': 4, 'af': 1, 'f': '0.853', 'LZZFREIB': 15000, 'LZZHINZU': 2500, 'ZKF': 1, 'R': 1}]
rows = []
for inputs in cases:
    url = 'https://www.bmf-steuerrechner.de/interface/2026Version1.xhtml?' + urlencode({'code': 'LSt2026ext', **inputs})
    with urlopen(url, timeout=30) as response:
        xml = ET.fromstring(response.read())
    outputs = {item.attrib['name']: item.attrib['value'] for item in xml.findall('./ausgaben/ausgabe')}
    if not all(name in outputs for name in ('LSTLZZ', 'SOLZLZZ', 'BK')):
        raise RuntimeError('BMF returned no valid test result: ' + ET.tostring(xml, encoding='unicode'))
    rows.append({'inputs': inputs, 'outputs': outputs})
    print(f'Validated reference {len(rows)}/{len(cases)}', flush=True)
path = Path(__file__).resolve().parents[2] / 'Chrono-backend/src/test/resources/payroll/bmf-2026-reference.json'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps({'source': 'BMF 2026Version1 program-test interface', 'retrieved': '2026-10-16', 'cases': rows}, indent=2) + '\n', encoding='utf-8')
