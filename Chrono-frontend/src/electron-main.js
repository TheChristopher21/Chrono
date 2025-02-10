// src/electron-main.js
import { app, BrowserWindow } from 'electron';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow;
let backendProcess;

/**
 * Ermittelt den Basis-Pfad.
 * - Im Entwicklungsmodus: ../
 * - Im Produktionsmodus: process.resourcesPath
 */
function getBasePath() {
    return app.isPackaged
        ? process.resourcesPath
        : path.join(__dirname, '..');
}

/**
 * Startet das Spring-Boot-Backend aus dem JAR.
 */
function startBackend() {
    const basePath = getBasePath();
    const backendJar = path.join(basePath, 'backend', 'Chrono-0.0.1-SNAPSHOT.jar');

    console.log("[INFO] Basis-Pfad:", basePath);
    console.log("[INFO] Backend-JAR Pfad:", backendJar);

    if (!fs.existsSync(backendJar)) {
        console.error("[ERROR] Backend-JAR nicht gefunden! Pfad:", backendJar);
        return;
    }

    const javaPath = "java"; // Java muss im PATH liegen
    backendProcess = spawn(javaPath, ['-jar', backendJar], {
        detached: true,
        shell: false,
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true
    });

    backendProcess.stdout.on('data', (data) => {
        console.log("[BACKEND STDOUT]", data.toString());
    });

    backendProcess.stderr.on('data', (data) => {
        console.error("[BACKEND STDERR]", data.toString());
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

/**
 * Erzeugt das Hauptfenster und lädt index.html (aus dist/).
 */
function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1024,
        height: 768,
        show: false,
        webPreferences: {
            // preload: path.join(__dirname, 'preload.js'), // Falls du gar kein Preload brauchst, kannst du das weglassen
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    const indexPath = path.join(getBasePath(), 'dist', 'index.html');
    console.log("[INFO] Lade index.html von:", indexPath);

    mainWindow.webContents.on('did-fail-load', (event, code, desc, validatedURL) => {
        console.error(`[ERROR] Laden von index.html fehlgeschlagen: ${desc} (Code: ${code}), URL: ${validatedURL}`);
    });

    mainWindow.loadFile(indexPath)
        .then(() => {
            console.log("[INFO] index.html geladen.");
            mainWindow.once('ready-to-show', () => {
                mainWindow.show();
                console.log("[INFO] Hauptfenster angezeigt.");
            });
        })
        .catch(err => {
            console.error("[ERROR] Fehler beim Laden der index.html:", err);
        });
}

app.whenReady().then(() => {
    console.log("[APP] App ist bereit.");
    startBackend();   // Spring-Boot-Backend starten
    createWindow();   // BrowserWindow erstellen

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        // Backend-Prozess ggf. beenden
        if (backendProcess) backendProcess.kill();
        app.quit();
    }
});
