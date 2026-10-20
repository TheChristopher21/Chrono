"""Deterministically translate the unmodified official BMF XML to Java (no runtime network).

Run from repository root: python tools/payroll/generate_bmf.py
Only the published PAP grammar is accepted; unknown nodes fail the build step.
"""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
source = root / 'Chrono-backend/src/main/resources/payroll/de/Lohnsteuer2026.xml'
target = root / 'Chrono-backend/src/main/java/com/chrono/chrono/services/payroll/BmfLohnsteuer2026.java'
pap = ET.fromstring(source.read_bytes())
lines = [
    'package com.chrono.chrono.services.payroll;', '', 'import java.math.BigDecimal;', '',
    '/** Generated from the official BMF PAP. Do not edit; run tools/payroll/generate_bmf.py.',
    ' * Source: https://www.bmf-steuerrechner.de/javax.faces.resource/daten/xmls/Lohnsteuer2026.xml.xhtml',
    ' * SHA-256: ' + hashlib.sha256(source.read_bytes()).hexdigest(), ' */',
    '@SuppressWarnings("deprecation")', 'public final class BmfLohnsteuer2026 {'
]
for group in pap.find('VARIABLES'):
    for field in group:
        typ, name = field.attrib['type'], field.attrib['name']
        default = field.attrib.get('default', 'BigDecimal.ZERO' if typ == 'BigDecimal' else '0')
        access = 'public' if field.tag in ('INPUT', 'OUTPUT') else 'private'
        lines.append(f'    {access} {typ} {name} = {default};')
for field in pap.find('CONSTANTS'):
    lines.append(f'    private static final {field.attrib["type"]} {field.attrib["name"]} = {field.attrib["value"]};')

def body(node, indent):
    pad = '    ' * indent
    for child in node:
        if child.tag == 'EVAL':
            lines.append(pad + child.attrib['exec'].rstrip(';') + ';')
        elif child.tag == 'EXECUTE':
            lines.append(pad + child.attrib['method'] + '();')
        elif child.tag == 'IF':
            lines.append(pad + 'if (' + child.attrib['expr'] + ') {')
            body(child.find('THEN'), indent + 1)
            if child.find('ELSE') is not None:
                lines.append(pad + '} else {')
                body(child.find('ELSE'), indent + 1)
            lines.append(pad + '}')
        else:
            raise ValueError('Unsupported PAP element: ' + child.tag)

for method in pap.find('METHODS'):
    signature = 'public void calculate()' if method.tag == 'MAIN' else 'private void ' + method.attrib['name'] + '()'
    lines.append('    ' + signature + ' {')
    body(method, 2)
    lines.append('    }')
lines.append('}')
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'Generated {target.name}; source SHA-256 {hashlib.sha256(source.read_bytes()).hexdigest()}')
