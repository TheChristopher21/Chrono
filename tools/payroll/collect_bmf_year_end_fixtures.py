"""Explicit synthetic PROGRAM TESTS against BMF; never send real payroll data."""
import json
from datetime import date
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen
import xml.etree.ElementTree as ET

base = dict(LZZ=1, STKL=1, KVZ='2.9', PVZ=1, PVS=0, PVA=0, ZKF=0, R=0, af=0, f=1)
cases = [{**base, 'RE4': amount} for amount in (6000000, 6500000, 12000000)]
cases += [{**base, 'RE4': 6500000, 'STKL': 4, 'R': 1, 'ZKF': '1.5', 'PVZ': 0, 'PVA': 1}]
rows = []
for inputs in cases:
    url = 'https://www.bmf-steuerrechner.de/interface/2026Version1.xhtml?' + urlencode({'code': 'LSt2026ext', **inputs})
    with urlopen(url, timeout=25) as response:
        xml = ET.fromstring(response.read())
    outputs = {node.attrib['name']: node.attrib['value'] for node in xml.findall('./ausgaben/ausgabe')}
    if not all(key in outputs for key in ('LSTLZZ', 'SOLZLZZ', 'BK')):
        raise RuntimeError('No BMF annual reference result')
    rows.append({'inputs': inputs, 'outputs': outputs})
path = Path(__file__).resolve().parents[2] / 'Chrono-backend/src/test/resources/payroll/bmf-2026-year-end-reference.json'
path.write_text(json.dumps({'source': 'Official BMF 2026Version1 program-test interface, annual LZZ=1', 'retrieved': '2026-11-03', 'cases': rows}, indent=2) + '\n', encoding='utf-8')
print(f'{len(rows)} independent annual BMF fixtures written.')
