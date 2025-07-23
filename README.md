# Chrono

Chrono ist eine Zeiterfassungsanwendung, die aus einem Spring Boot Backend und einem React/Vite Frontend besteht. Das Frontend kann auch als Electron-App verpackt werden und startet dabei automatisch das Backend.

## Technischer Überblick

- **Backend:** Spring Boot 3 mit Maven Wrapper. Enthält REST‑APIs, Datenbankzugriff per JPA und Sicherheit über JWT.
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

## Weiterführendes

Für Code-Qualitätsanalysen liegen `qodana.yaml`‑Dateien in beiden Teilprojekten. Die Einstellungen in `application.properties` geben Hinweise auf benötigte Umgebungsvariablen für die Datenbank- und Mailkonfiguration.


## Lokaler Chatbot

Für einen lokal betriebenen KI-Chatbot erwartet das Backend einen HTTP-Endpunkt, der auf Anfragen unter `llm.base-url` reagiert. Standardmäßig wird `http://localhost:5000` genutzt. Beispiel in `application.properties`:

```
# Local LLM endpoint
llm.base-url=${LLM_BASE_URL:http://localhost:5000}
```

Der Endpunkt sollte JSON im Format `{ "prompt": "Nachricht" }` akzeptieren und mit `{ "response": "Antwort" }` antworten.
