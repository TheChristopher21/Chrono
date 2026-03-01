# Chrono

Chrono ist eine Zeiterfassungsanwendung, die aus einem Spring Boot Backend und einem React/Vite Frontend besteht. Das Frontend kann auch als Electron-App verpackt werden und startet dabei automatisch das Backend.

## Technischer Überblick

- **Backend:** Spring Boot 3 mit Maven Wrapper. Enthält REST‑APIs, Datenbankzugriff per JPA und Sicherheit über JWT. Die Payroll‑Logik berücksichtigt gesetzliche Abzüge für Deutschland und die Schweiz. Dazu zählen Soli/Kirchensteuer, Beitragsbemessungsgrenzen, Mini‑/Midi‑Job‑Reduktionen sowie koordinierter BVG‑Lohn und Arbeitgeberumlagen.
- Dabei wird auch die BVG-Eintrittsschwelle von 22'680 CHF/Jahr geprüft.
- **Frontend:** React 19 mit Vite. Entwicklung und Build werden über npm gesteuert. Für den Desktop‑Einsatz steht eine Electron-Integration bereit.

## Voraussetzungen

- **Java 17** oder neuer
- **Node.js** und **npm** (empfohlen Version 18 oder höher)
- Eine MySQL‑Datenbank

## Setup und Starten

### Backend

1. Abhängigkeiten laden und Anwendung starten:
   ```bash
   cd Chrono-backend
   ./mvnw spring-boot:run
   ```
   Alternativ kann ein ausführbares JAR erstellt werden:
   ```bash
   ./mvnw package
   java -jar target/Chrono-0.0.1-SNAPSHOT.jar
   ```
2. Notwendige Datenbank‑ und Mail‑Zugangsdaten werden über Umgebungsvariablen in `application.properties` eingelesen.

### Frontend

1. Abhängigkeiten installieren:
   ```bash
   cd Chrono-frontend
   npm install
   ```
2. Entwicklungsserver starten:
   ```bash
   npm run dev
   ```
   Das Frontend erreicht das Backend standardmäßig unter `http://localhost:8080`.
3. Für eine Electron‑Version:
   ```bash
   npm run electron
   ```

## Projektstruktur

```
Chrono/
├── Chrono-backend/      # Spring Boot Anwendung
│   ├── pom.xml          # Maven Konfiguration
│   └── src/main/        # Java‑Quellcode und Ressourcen
└── Chrono-frontend/     # React/Vite Frontend
    ├── package.json     # npm Konfiguration
    ├── vite.config.js   # Vite Einstellungen
    └── src/             # React-Komponenten und Assets
```

Im Backend befinden sich unter `com.chrono.chrono` die Pakete `controller`, `services`, `repositories` usw. Das Frontend gliedert sich in `components`, `pages`, `styles` und weitere Hilfsdateien.

## Startbefehle im Überblick

| Ort               | Befehl                          | Zweck                           |
|-------------------|---------------------------------|---------------------------------|
| `Chrono-backend`  | `./mvnw spring-boot:run`        | Backend im Dev-Modus starten    |
| `Chrono-frontend` | `npm run dev`                   | Frontend mit Vite starten       |
| `Chrono-frontend` | `npm run build`                 | Produktionsbuild des Frontends  |
| `Chrono-frontend` | `npm run electron`              | Electron-App inklusive Backend  |

## Monitoring & Logging

Das Backend stellt über Spring Boot Actuator Metriken unter `/actuator/prometheus` bereit und gibt Logs im JSON-Format auf `STDOUT` aus.

### Prometheus und Grafana

```bash
docker network create monitoring

docker run -d --name=prometheus --network=monitoring \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  -p 9090:9090 prom/prometheus

docker run -d --name=grafana --network=monitoring \
  -p 3000:3000 grafana/grafana
```

Die `prometheus.yml` sollte das Backend unter `http://<server>:8080/actuator/prometheus` scrapen.

### Log-Sammlung (optional)

```bash
docker run -d --name=loki --network=monitoring -p 3100:3100 grafana/loki
docker run -d --name=promtail --network=monitoring \
  -v $(pwd)/promtail-config.yml:/etc/promtail/config.yml grafana/promtail
```

## Weiterführendes

Für Code-Qualitätsanalysen liegen `qodana.yaml`‑Dateien in beiden Teilprojekten. Die Einstellungen in `application.properties` geben Hinweise auf benötigte Umgebungsvariablen für die Datenbank- und Mailkonfiguration.


## Lokaler Chatbot

Für einen lokal betriebenen KI-Chatbot erwartet das Backend einen HTTP-Endpunkt, der auf Anfragen unter `llm.base-url` reagiert. Standardmäßig wird `http://localhost:5000` genutzt. Beispiel in `application.properties`:

```
# Local LLM endpoint
llm.base-url=${LLM_BASE_URL:http://localhost:5000}
```

Der Endpunkt sollte JSON im Format `{ "prompt": "Nachricht" }` akzeptieren und mit `{ "response": "Antwort" }` antworten.

Im Frontend befindet sich auf jeder Seite unten rechts ein ausklappbarer Chat-Button. Darüber lassen sich Fragen direkt an den lokalen Chatbot stellen.


## Lizenz

Copyright (c) 2024 Christopher Siefert. Alle Rechte vorbehalten.

Der gesamte Code dieses Projekts ist proprietär. Eine Nutzung, Vervielfältigung, Veränderung oder Weitergabe ist ohne vorherige schriftliche Genehmigung von Christopher Siefert nicht gestattet. Weitere Informationen stehen in der [LICENSE](LICENSE).
