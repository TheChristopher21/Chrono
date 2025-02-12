// src/electron-main.js
import { app, BrowserWindow, ipcMain } from 'electron';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow;
let backendProcess;

/**
 * Gibt den Basis-Pfad zurück.
 * Im Produktionsmodus: process.resourcesPath (in dem sich beispielsweise app.asar befindet)
 * Im Entwicklungsmodus: den Ordner oberhalb von __dirname.
 */
function getBasePath() {
    return app.isPackaged ? process.resourcesPath : path.join(__dirname, '..');
}

/**
 * Ermittelt den Pfad zum "dist"-Ordner.
 * Wird zunächst geprüft, ob im Basis-Pfad (resources) ein Ordner "dist" vorhanden ist.
 * Falls nicht – wird angenommen, dass die Dateien im asar-Archiv liegen.
 */
function getDistPath() {
    const basePath = getBasePath();
    const distPath = path.join(basePath, 'dist');
    if (fs.existsSync(distPath)) {
        return distPath;
    } else {
        // Falls nicht, vermute, dass sich der dist-Ordner innerhalb von app.asar befindet.
        const asarDistPath = path.join(basePath, 'app.asar', 'dist');
        if (fs.existsSync(asarDistPath)) {
            return asarDistPath;
        }
    }
    // Fallback: Gib den ursprünglichen dist-Pfad zurück
    return distPath;
}

function startBackend() {
    const basePath = getBasePath();
    const backendJar = path.join(basePath, 'backend', 'Chrono-0.0.1-SNAPSHOT.jar');
    console.log("[INFO] Basis-Pfad:", basePath);
    console.log("[INFO] Backend-JAR Pfad:", backendJar);

    if (!fs.existsSync(backendJar)) {
        console.error("[ERROR] Backend-JAR nicht gefunden! Pfad:", backendJar);
        return;
    }

    const javaPath = "java"; // Stelle sicher, dass java im PATH verfügbar ist.
    // Nutze stdio: 'inherit' für volle Log-Ausgabe
    backendProcess = spawn(javaPath, ['-jar', backendJar], {
        detached: true,
        shell: true,
        stdio: 'inherit', // Alle Ausgaben werden in der Konsole angezeigt
        windowsHide: true
    });

    backendProcess.on('error', (err) => {
        console.error("[BACKEND] Fehler beim Starten:", err);
    });

    backendProcess.on('exit', (code, signal) => {
        console.log("[BACKEND] Prozess beendet mit Code:", code, "Signal:", signal);
        if (code !== 0) {
            console.error("[BACKEND] Fehler beim Backend.");
        }
    });

    backendProcess.unref();
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1920,
        height: 1080,
        show: true,
        webPreferences: {
            // Kein Preload‑Script
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false // Erlaubt das Laden lokaler file://-Ressourcen
        }
    });

    // CSP-Anpassung: Erlaube lokale Dateien (file:) und den Zugriff auf http://localhost:8080
    mainWindow.webContents.session.webRequest.onHeadersReceived((details, callback) => {
        callback({
            responseHeaders: {
                ...details.responseHeaders,
                'Content-Security-Policy': [
                    "default-src 'self' file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080"
                ]
            }
        });
    });

    const distPath = getDistPath();
    const indexPath = path.join(distPath, 'index.html');
    console.log("[INFO] Lade index.html von:", indexPath);

    if (!fs.existsSync(indexPath)) {
        console.error("[ERROR] index.html wurde nicht gefunden unter:", indexPath);
    }

    // Lade die index.html per loadFile
    mainWindow.loadFile(indexPath)
        .then(() => {
            console.log("[INFO] index.html erfolgreich geladen.");
            mainWindow.once('ready-to-show', () => {
                mainWindow.show();
                console.log("[INFO] Hauptfenster wird angezeigt.");
            });
        })
        .catch(err => {
            console.error("[ERROR] Fehler beim Laden von index.html:", err);
        });
}

/**
 * IPC-Handler zum Öffnen eines neuen Fensters für den Report.
 */
ipcMain.handle('openPrintReport', async (event, reportUrl) => {
    const printWindow = new BrowserWindow({
        width: 800,
        height: 600,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false
        }
    });

    printWindow.webContents.session.webRequest.onHeadersReceived((details, callback) => {
        callback({
            responseHeaders: {
                ...details.responseHeaders,
                'Content-Security-Policy': [
                    "default-src 'self' file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080"
                ]
            }
        });
    });

    try {
        await printWindow.loadURL(reportUrl);
        console.log("[INFO] Report-URL geladen:", reportUrl);
    } catch (err) {
        console.error("[ERROR] Fehler beim Laden der Report-URL:", err);
    }

    // Nach dem Laden injiziere einen Meta-Tag zur weiteren Verstärkung der CSP
    printWindow.webContents.executeJavaScript(`
    if (!document.querySelector('meta[http-equiv="Content-Security-Policy"]')) {
      const meta = document.createElement('meta');
      meta.httpEquiv = "Content-Security-Policy";
      meta.content = "default-src 'self' file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080";
      document.head.appendChild(meta);
    }
  `).then(() => {
        console.log("[INFO] CSP-Meta-Tag im Report-Fenster eingefügt.");
    }).catch(err => {
        console.error("[ERROR] Beim Einfügen des CSP-Meta-Tags im Report-Fenster:", err);
    });

    printWindow.show();
});

app.whenReady().then(() => {
    console.log("[APP] App ist bereit.");
    startBackend();   // Starte das Backend
    createWindow();   // Erstelle und zeige das Hauptfenster

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        if (backendProcess) backendProcess.kill();
        app.quit();
    }
});
