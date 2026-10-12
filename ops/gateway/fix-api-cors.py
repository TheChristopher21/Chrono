#!/usr/bin/env python3
"""Remove the legacy API CORS override from Nginx Proxy Manager (dry run by default).

Spring Security owns the origin/header allowlist. Persist the repair in both
NPM's host record and generated config so a later NPM save cannot undo it.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess


LEGACY_CORS = """add_header 'Access-Control-Allow-Origin' 'https://chrono-logisch.ch' always;
add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
add_header 'Access-Control-Allow-Headers' 'Authorization,Content-Type,Origin' always;
add_header 'Access-Control-Allow-Credentials' 'true' always;

if ($request_method = 'OPTIONS') {
    return 204;
}"""


def repair(data_dir, container, apply):
    database = data_dir / 'database.sqlite'
    # mode=rw refuses to silently create a database at the wrong location.
    db = sqlite3.connect(f'{database.resolve().as_uri()}?mode=rw', uri=True)
    db.row_factory = sqlite3.Row
    config = None
    original = None
    changed = False
    try:
        db.execute('BEGIN IMMEDIATE')
        hosts = [row for row in db.execute(
            'SELECT id, domain_names, advanced_config, locations FROM proxy_host WHERE is_deleted=0'
        ) if json.loads(row['domain_names']) == ['api.chrono-logisch.ch']]
        if len(hosts) != 1:
            raise RuntimeError('Expected exactly one dedicated api.chrono-logisch.ch proxy host')
        host = dict(hosts[0])
        advanced = host['advanced_config'].replace('\r\n', '\n').strip()
        config = data_dir / 'nginx' / 'proxy_host' / f"{int(host['id'])}.conf"
        original = config.read_bytes()
        contents = original.decode('utf-8').replace('\r\n', '\n')
        if not advanced and 'Access-Control-' not in contents and 'return 204;' not in contents:
            print('[OK] API proxy has no legacy CORS override; nothing to change.')
            return
        if advanced != LEGACY_CORS or contents.count(LEGACY_CORS) != 1:
            raise RuntimeError('Unrecognized proxy configuration; refusing an automatic edit')
        if json.loads(host['locations'] or '[]'):
            raise RuntimeError('Custom locations require a manual review before this repair')
        repaired = contents.replace(LEGACY_CORS, '', 1)
        if 'Access-Control-' in repaired or 'return 204;' in repaired:
            raise RuntimeError('Additional CORS/OPTIONS directives require manual review')
        print(f"[INFO] Host {host['id']}: remove only the legacy CORS block; forward OPTIONS to Spring.")
        if not apply:
            print('[INFO] Dry run. Use --apply after verifying the backend preflight response.')
            return
        backup = data_dir / 'cors-backups' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        backup.mkdir(parents=True, mode=0o700)
        shutil.copy2(config, backup / config.name)
        (backup / 'proxy-host.json').write_text(json.dumps(host, indent=2), encoding='utf-8')
        changed = True
        config.write_bytes(repaired.encode('utf-8'))
        subprocess.run(['docker', 'exec', container, 'nginx', '-t'], check=True)
        db.execute('UPDATE proxy_host SET advanced_config=? WHERE id=?', ('', host['id']))
        subprocess.run(['docker', 'exec', container, 'nginx', '-s', 'reload'], check=True)
        db.commit()
        print(f'[OK] API CORS override removed and proxy reloaded. Backup: {backup}')
    except Exception:
        db.rollback()
        if changed:
            config.write_bytes(original)
            subprocess.run(['docker', 'exec', container, 'nginx', '-t'], check=True)
            subprocess.run(['docker', 'exec', container, 'nginx', '-s', 'reload'], check=True)
        raise
    finally:
        db.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=Path('/root/chrono/data'))
    parser.add_argument('--container', default='chrono-nginx-1')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    repair(args.data_dir, args.container, args.apply)
