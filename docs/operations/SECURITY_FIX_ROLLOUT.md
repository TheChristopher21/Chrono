# Manuelles Sicherheitsupdate: bestehende Firmen erhalten

Dieses Update wird nicht automatisch auf den Live-Server gebracht. Es enthält
keine automatische Firmenzuordnung historischer Buchhaltungs- oder NFC-Daten.
Bestehende Firmen, Mitarbeitende, Lohnabrechnungen einschließlich
Überstundenauszahlungen, Überstundenstände und Zeitbuchungen werden durch die
Sicherheitsmigrationen nicht neu berechnet oder verändert. Die nachstehenden
Prüfungen gehören zur manuellen Freigabe auf der tatsächlichen Installation.

## Vorbereitung auf einer wiederhergestellten Kopie

1. Eine vollständige Datenbanksicherung und die zugehörigen Dokument-/PDF-Dateien
   sichern. Den Restore gemäß [BACKUP_RESTORE.md](BACKUP_RESTORE.md) auf einer
   getrennten Datenbank prüfen. Die Sicherung und die bisherige Anwendungsversion
   bis zur erfolgreichen Abnahme behalten.
2. Die Ausgangswerte mit den passenden Abschnitten aus
   [security-ownership-readonly.sql](../../ops/audit/security-ownership-readonly.sql)
   schreibgeschützt erfassen. Die Datei enthält Vorher- und Nachher-Abfragen;
   Abschnitte mit neuen Spalten erst nach der Migration ausführen. Tabellen und
   Spalten zuvor prüfen, da ältere Installationen optionale Tabellen nicht haben.
   Ergebnisse lokal geschützt aufbewahren; sie enthalten Finanz-/Zeitdaten.
3. Das Update zuerst mit dieser wiederhergestellten Kopie und getrennten
   Testzugängen prüfen. Echte Zahlungs-/Versand-/Beds24-Worker dort nicht starten.
   Insbesondere vorhandene Lohnbeträge, ausgezahlte Überstunden,
   `tracking_balance_in_minutes`, Zeitstempel und PDF-Zuordnungen vergleichen.
   Vorhandene Lohnabrechnungen nicht erneut freigeben, berechnen oder korrigieren,
   um das Update zu testen.

## Das vorhandene NFC-Gerät der Firma Praxis

Auf der Ziel-Datenbank schreibgeschützt ausführen:

```sql
SELECT id, name FROM companies WHERE name = 'Praxis';
```

Nur bei genau einer Zeile fortfahren und die Firma anhand des vorhandenen
Geräts bestätigen. Bei keiner oder mehreren Zeilen die Zuordnung zuerst klären.
Keine feste ID übernehmen und nicht die erste Firma verwenden. In der bestehenden
Backend-Umgebung ergänzen:

```dotenv
NFC_AGENT_COMPANY_ID=<bestätigte ID der Firma Praxis>
NFC_AGENT_DEVICE_ID=praxis-reader
NFC_AGENT_REGISTRY_JSON=[]
```

Den vorhandenen `NFC_AGENT_TOKEN` beibehalten. Am Gerät bleiben Token,
`X-Agent-Token`-Header und bisherige Stempel-/Queue-Anfragen unverändert.
`NFC_AGENT_ALLOW_LOCALHOST_LEGACY` bleibt in Produktion `false`. Beide
Compose-Dateien reichen die neuen Variablen an das Backend weiter.
`ops/preflight.ps1` prüft vor dem Deployment die Form der Firmen-/Gerätezuordnung;
es kann deren fachliche Richtigkeit nicht aus der Datenbank ermitteln. Das
Backend prüft zusätzlich, dass die konfigurierte Firma existiert.

Alte NFC-Aufträge bleiben unverändert gespeichert und werden ohne verlässliche
Eigentümerschaft nicht mehr ausgeliefert oder abgeschlossen. Keine alten
Aufträge pauschal Praxis zuweisen oder erneut abspielen. Bei einem noch nötigen
Programmierauftrag nach Prüfung von Mitarbeiter und Gerät einen neuen Auftrag
über die Firmenoberfläche anlegen. Details:
[NFC_AND_SHIFT_ISOLATION.md](NFC_AND_SHIFT_ISOLATION.md).

Beds24 ist hier bisher nicht live. `APP_PMS_BEDS24_BINDINGS` bleibt leer und
`APP_PMS_BEDS24_WORKER_ENABLED=false`. Spätere Freigaben benötigen eine geprüfte
exakte Firmen-/Hotel-/Secret-Referenz gemäß [PMS_BEDS24.md](PMS_BEDS24.md).

## Migration und Abnahme

1. Ein Wartungsfenster wählen und **alle Instanzen der alten Anwendung** vor der
   Migration anhalten. Alte und neue Version dürfen nicht gleichzeitig schreiben.
   Zahlungsautomatik zunächst mit `APP_PMS_PAYMENTS_AUTOMATION_ENABLED=false`
   ausgeschaltet lassen. Vorhandene Provider- und sonstige Worker-Freigaben für
   die Abnahme bewusst prüfen.
2. Die geprüften neuen Images und die ergänzte Umgebung mit dem bestehenden
   anwendungsbezogenen Deployment verwenden. Nicht Datenbank, Infrastruktur oder
   Volumes neu anlegen. Vorab `ops/preflight.ps1` mit dieser Umgebung ausführen.
   Das Update enthält V57–V59; bereits angewandte Migrationen und deren Prüfsummen
   nicht bearbeiten, überspringen oder mit einem pauschalen Repair übergehen.
3. Nach der Migration die Ausgangswerte vergleichen: alte IDs, Firmenzuordnungen,
   Lohnwerte, Überstundenstände, Zeitbuchungen, Journalzeilen, Anlagen und
   Anbieterreferenzen müssen erhalten sein. V57 ergänzt nullable Firmenfelder;
   alte Buchhaltung bleibt ohne automatische Zuordnung. V58 ergänzt getrennte
   Zahlungsidentitäten und lässt vorhandene `request_key`-Werte unverändert.
   V59 ergänzt NFC-/Schicht-Eigentümerschaft; gemeinsame Schichtvorgaben behalten
   ihre bisherigen Werte und dienen unveränderlich als Vorgaben.
4. In der Kopie ein unverändertes Praxis-Stempeln und die Ablehnung eines
   Stempelversuchs für eine andere Firma prüfen. Neue Teststempel nur in der
   Kopie erzeugen. Eigene NFC-Queue und Abschluss prüfen; ein historischer
   eigentümerloser Auftrag darf nicht ausgeliefert werden. Danach je Firma
   Zeitansicht, Lohnansicht und unabhängige Schichtbearbeitung prüfen.
5. Erst nach erfolgreicher Abnahme die erforderlichen Worker bewusst freigeben.
   Bei Zahlungen auch bestehende offene/unklare Provider-Aufträge prüfen; Details
   stehen in [PMS_PAYMENT_AUTOMATION.md](PMS_PAYMENT_AUTOMATION.md).

Historische Buchhaltung ohne sichere Firmenzuordnung ist für Firmenkonten
gesperrt. Ein Superadministrator kann sie ausschließlich lesend über
`GET /api/superadmin/accounting/legacy?page=0&size=20` prüfen. Dieses Update stellt
keine Schreibfunktion zur nachträglichen Zuordnung bereit. Neue Buchungen und
Konten gehören zur tatsächlichen Firma. Erkennt eine erneute Lohnbuchung einen
alten eigentümerlosen Journalbeleg derselben Abrechnung, wird eine Doppelbuchung
verhindert und eine geprüfte Zuordnung verlangt; dadurch wird die alte
Lohnabrechnung nicht verändert. Auch eine bereits einer anderen Firma zugehörige
Lohnbuchung wird nach einem Mitarbeiter-Firmenwechsel weder verschoben noch
erneut angelegt.

## Rückkehr zur alten Version

Kein automatisches Down-Migrate und keine Spaltenlöschung vornehmen. Insbesondere
nach neuen Zahlungsaufträgen kennt die alte Anwendung die getrennte Browser- und
Provider-Identität nicht. Ein bloßer Image-Wechsel ist daher kein sicherer
Rollback. Die Anwendung anhalten und die Rückkehr mit der geprüften Sicherung,
Dokumentdateien und Originalversion abstimmen. Seit der Sicherung entstandene
Zeitstempel, Lohnänderungen und externe Provider-Vorgänge vorher sichern und
abgleichen; ein unbedachter Restore würde diese neuen Vorgänge verlieren oder
bei externen Zahlungen zu einem widersprüchlichen Zustand führen.
