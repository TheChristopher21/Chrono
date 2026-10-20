"""Synthetic program tests only; never application data. BMF public test interface 2026."""
import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen
import xml.etree.ElementTree as ET

base = dict(LZZ=2, RE4=500000, STKL=1, KVZ='2.9', PVZ=1, PVS=0, PVA=0, ZKF=0, R=0, af=0, f=1)
cases = [{**base, 'LZZ': 4, 'RE4': amount} for amount in (10000, 16666, 31250, 62500)]
cases += [{**base, 'JRE4': 6000000, 'SONSTB': amount} for amount in (50000, 250000, 500000)]
cases += [{**base, 'STKL': 4, 'af': 1, 'f': '0.853', 'JRE4': 6500000, 'SONSTB': 400000, 'JFREIB': 120000, 'JHINZU': 6000, 'R': 1, 'ZKF': 1}]
rows = []
for inputs in cases:
    url = 'https://www.bmf-steuerrechner.de/interface/2026Version1.xhtml?' + urlencode({'code': 'LSt2026ext', **inputs})
    with urlopen(url, timeout=30) as response:
        xml = ET.fromstring(response.read())
    outputs = {item.attrib['name']: item.attrib['value'] for item in xml.findall('./ausgaben/ausgabe')}
    if not all(name in outputs for name in ('LSTLZZ', 'SOLZLZZ', 'BK', 'STS', 'SOLZS', 'BKS')):
        raise RuntimeError('Incomplete BMF response')
    rows.append({'inputs': inputs, 'outputs': outputs})
    print(f'Independent synthetic reference {len(rows)}/{len(cases)}', flush=True)
path = Path(__file__).resolve().parents[2] / 'Chrono-backend/src/test/resources/payroll/bmf-2026-extended-reference.json'
path.write_text(json.dumps({'source': 'BMF 2026Version1 program-test interface', 'retrieved': '2026-10-16', 'cases': rows}, indent=2) + '\n', encoding='utf-8')
