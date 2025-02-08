// src/electron-main.js
import { app, BrowserWindow, ipcMain } from 'electron';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import { startNfcReader, writeUserIdToCard } from './nfcReader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow;
let backendProcess;

/**
 * Ermittelt den Basis-Pfad:
 * - Im Produktionsmodus wird process.resourcesPath genutzt.
 * - Im Entwicklungsmodus (app.isPackaged false) gehen wir eine Ebene höher,
 *   da __dirname normalerweise im "src" Ordner liegt.
 */
function getBasePath() {
    return app.isPackaged ? process.resourcesPath : path.join(__dirname, '..');
}

/**
 * Startet das Spring-Boot-Backend.
 */
function startBackend() {
    const basePath = getBasePath();
    const backendJar = path.join(basePath, 'backend', 'Chrono-0.0.1-SNAPSHOT.jar');
    console.log("[INFO] Basis-Pfad:", basePath);
    console.log("[INFO] Backend-JAR Pfad:", backendJar);

    if (!fs.existsSync(backendJar)) {
        console.error("[ERROR] Backend-JAR nicht gefunden! Stelle sicher, dass die Datei unter", backendJar, "vorhanden ist.");
        return;
    }

    const javaPath = "java"; // Stelle sicher, dass "java" im PATH ist
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
        console.error("[BACKEND] Fehler beim Starten des Backend-Prozesses:", err);
    });

    backendProcess.on('exit', (code, signal) => {
        console.log("[BACKEND] Prozess beendet mit Code:", code, "Signal:", signal);
        if (code !== 0) {
            console.error("[BACKEND] Es gab einen Fehler beim Starten des Backends.");
        }
    });

    backendProcess.unref();
}

/**
 * Erzeugt das Hauptfenster.
 */
function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1024,
        height: 768,
        show: false, // Zunächst versteckt
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    const indexPath = path.join(getBasePath(), 'dist', 'index.html');
    console.log("[INFO] Lade index.html von:", indexPath);

    // Fehler-Listener für das Laden der index.html
    mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
        console.error(`[ERROR] Laden der index.html fehlgeschlagen: ${errorDescription} (Code: ${errorCode}) für URL: ${validatedURL}`);
    });

    mainWindow.loadFile(indexPath)
        .then(() => {
            console.log("[INFO] index.html erfolgreich geladen.");
            mainWindow.once('ready-to-show', () => {
                mainWindow.show();
                console.log("[INFO] Hauptfenster wird angezeigt.");
            });
        })
        .catch(err => {
            console.error("[ERROR] Fehler beim Laden der index.html:", err);
        });
}

/**
 * IPC-Handler: "write-card" ruft writeUserIdToCard auf.
 */
function initIPC() {
    ipcMain.handle('write-card', async (event, userId) => {
        try {
            console.log("[IPC] write-card aufgerufen für:", userId);
            await writeUserIdToCard(userId);
            return { success: true };
        } catch (err) {
            console.error("[IPC] Fehler beim Kartenbeschreiben:", err);
            return { success: false, error: err.message };
        }
    });
}

app.whenReady().then(() => {
    console.log("[APP] App ist bereit.");
    startBackend();
    createWindow();
    startNfcReader();
    initIPC();

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
