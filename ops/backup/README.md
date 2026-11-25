# Regelmäßige Sicherung und Wiederherstellungsprüfung

Für einen bestehenden Chrono-Docker-Host installiert `install-host-timer.sh` einen eigenständigen systemd-Timer. Er ersetzt keine produktiven Container und benötigt keine zusätzliche Passwortdatei. `host-backup.sh` verwendet den bereits laufenden MySQL-Container und dessen eigene Zugangsdaten ausschließlich für lesende Datenbankabfragen und `mysqldump`.

```bash
sudo bash ops/backup/install-host-timer.sh
sudo systemctl start --no-block chrono-backup.service
sudo systemctl status chrono-backup.service
sudo journalctl -u chrono-backup.service --no-pager
```

Standard: Daten unter `/root/chrono/data`, MySQL-Container `chrono-mysql-1`, täglicher Lauf ab 02:15 UTC mit bis zu fünf Minuten Streuung. Abweichende Pfade/Container können über `systemctl edit chrono-backup.service` mit `Environment=CHRONO_BACKUP_ROOT=...` und `Environment=CHRONO_MYSQL_CONTAINER=...` gesetzt werden. Das Zielverzeichnis muss schon existieren. Änderungen an den Skripten werden durch erneutes Ausführen des Installers nach `/opt/chrono-backup` übernommen.

Jeder Lauf erstellt einen konsistenten SQL-Dump mit Prüfsumme. Anschließend wird der Dump in einen neuen MySQL-Container mit derselben lokalen Image-ID importiert. Dieser hat kein Netzwerk, keine veröffentlichten Ports und begrenzte CPU-/Speicherressourcen; MySQL-Ereignisse sind deaktiviert. Der Prozess vergleicht sämtliche Basistabellen, die Anzahl der PMS-Tabellen, die vier PMS-Kerntabellen und die Flyway-Version mit der Quelle. Der eigene Restore-Container samt eigenem anonymem Datenvolume wird danach entfernt. Produktionsdaten und Produktionsvolumes werden nicht zurückgespielt oder gelöscht.

Erst nach erfolgreichem Import werden `restore-evidence/restore-verification.json` und `backups/latest.ok` auf den neuen Stand gesetzt. Fehlgeschlagene Läufe schreiben einen FAILED-Nachweis und lassen die vorherige bestätigte Sicherung stehen. Dateinamen und Prüfsummen sind zwischen Host und Backend-Mount kompatibel; die dedizierten Mount-Verzeichnisse und bestätigten Dateien sind für den nicht privilegierten Backend-Benutzer lesbar. Rotation betrifft ausschließlich `regular-*.sql` dieses Dienstes nach 30 Tagen; die aktuelle und vorherige bestätigte Generation bleiben erhalten.

Ein Deployment-Backup `predeploy-*.sql.gz` dient der Absicherung des alten Releases. Es wird nicht als reguläre Sicherung befördert. Nach einer Schemaänderung kann mit `systemctl start --no-block chrono-backup.service` sofort ein neuer Nachweis erstellt werden. Die vorhandene Compose-Alternative `mysql-backup`/`backup-restore-test` bleibt verfügbar; nur eine Variante sollte die regulären Marker verwalten.

Der PMS-Prüfer und die Compose-Restore-Skripte unterstützen reguläre `.sql` und `.sql.gz`. Ein vorhandener `latest.ok` ist maßgeblich. Ungültige Marker, Teil-Dateien, beschädigte Archive und fehlende Kerntabellen werden nicht durch einen beliebigen neueren Export verdeckt.

Prüfungen ohne Produktivdaten:

```bash
docker run --rm -v "$PWD/ops/backup:/scripts:ro" node:20-bookworm bash /scripts/tests/host-backup-test.sh
docker run --rm -v "$PWD/ops/backup:/scripts:ro" node:20-bookworm bash /scripts/tests/restore-format-test.sh
```
