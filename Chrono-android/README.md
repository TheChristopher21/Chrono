# Chrono Android Native

Dieses Projekt ist der native Android-Client fuer Chrono.

## Struktur

- `app/src/main/java/ch/chronologisch/chrono/MainActivity.kt`: Einstiegspunkt und erster Compose-Screen
- `app/src/main/java/ch/chronologisch/chrono/data/`: Login, Token-Speicher, Zeiterfassung und Modul-API-Anbindung
- `app/src/main/java/ch/chronologisch/chrono/ui/login/LoginViewModel.kt`: Login-Status und Session-Wiederherstellung
- `app/src/main/java/ch/chronologisch/chrono/ui/dashboard/DashboardViewModel.kt`: Zeiterfassung und Chrono-Module laden, aktualisieren und Aktionen ausfuehren
- `app/src/main/java/ch/chronologisch/chrono/ui/theme/ChronoTheme.kt`: Native Chrono-Farben
- `app/build.gradle.kts`: Android-App-Konfiguration

## Oeffnen

In Android Studio diesen Ordner oeffnen:

```text
Chrono-android
```

## Build in PowerShell

```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
.\gradlew.bat assembleDebug
```

Der Debug-Build nutzt die App-ID `ch.chronologisch.app.debug`, damit er parallel zur spaeteren Play-Store-App installiert werden kann.

Die App nutzt aktuell dieselbe Android-App-ID wie die geplante finale Chrono-App:

```text
ch.chronologisch.app
```

Diese finale App-ID gilt fuer den Release-Build.

## Backend-Verbindung

Der Debug-Build verbindet sich mit dem lokalen Chrono-Backend auf dem Entwicklungsrechner:

```text
http://10.0.2.2:8080
```

Das ist aus dem Android-Emulator heraus die Adresse fuer `localhost` auf deinem PC. Der Release-Build ist auf die produktive API vorbereitet:

```text
https://api.chrono-logisch.ch
```

Der native Login ruft zuerst `POST /api/auth/login` auf, speichert den Token lokal und laedt danach den Benutzer ueber `GET /api/auth/me`.

## Native Zeiterfassung

Nach dem Login laedt die App die Chrono-Zeiterfassung ueber:

```text
GET /api/timetracking/history?username=...
```

Der Stempelbutton nutzt:

```text
POST /api/timetracking/punch?username=...&source=MANUAL_PUNCH
```

Wenn Kundenzeiten aktiviert sind, laedt die App zusaetzlich:

```text
GET /api/customers/recent
GET /api/projects
GET /api/tasks?projectId=...
```

Tagesnotizen werden gespeichert ueber:

```text
POST /api/timetracking/daily-note?username=...&date=...
```

Die Ansicht zeigt den heutigen Status, Start/Ende/Pause, den aktuellen Saldo, die heutige Sollzeit, die aktuelle Woche, die Stempel des Tages, Kunden-/Projekt-/Aufgaben-Auswahl und die Tagesnotiz.

## Native Module

Die App hat eigene native Screens fuer:

- Zeit, Pensum, Abwesenheit, Lohn und Profil
- Supply Chain und Info
- Admin Dashboard, Mitarbeiter, Benutzer, Kunden/Projekte, Analytics und Dienstplan
- Payroll, Buchhaltung, CRM, Banking und Firma

Die sichtbaren Bereiche richten sich nach Rollen und Page-Permissions aus `GET /api/auth/me`. Jeder Bereich laedt Live-Daten aus dem Backend; Schreibaktionen laufen ueber native Formulare in der App und nutzen dieselben geschuetzten API-Endpunkte wie die Web-App.

## Google-Play-Build

Der Play-Store-Build wird als Android App Bundle gebaut:

```powershell
.\gradlew.bat bundleRelease "-PchronoVersionCode=8" "-PchronoVersionName=0.1.8"
```

Die Datei liegt danach unter:

```text
app/build/outputs/bundle/release/app-release.aab
```

Fuer Google Play muss unter `App-Inhalte` -> `App-Zugriff` ein Test-Login hinterlegt werden, weil Chrono geschuetzte Bereiche hat. Die Vorlage liegt in `play-store/app-access.md`.

Release-Signing liest die Werte aus `local.properties` oder aus Umgebungsvariablen. Vorlage:

```text
release-signing.example.properties
```

Einen Upload-Key lokal erzeugen:

```powershell
New-Item -ItemType Directory -Force keystores
keytool -genkeypair -v -keystore keystores/chrono-upload.jks -alias chrono-upload -keyalg RSA -keysize 4096 -validity 10000
```

Danach die echten Passwoerter in `local.properties` eintragen. Bei PKCS12-Keystores sollte `CHRONO_UPLOAD_KEY_PASSWORD` gleich `CHRONO_UPLOAD_STORE_PASSWORD` sein. `local.properties` und `keystores/` sind absichtlich ignoriert und duerfen nicht ins Git.
