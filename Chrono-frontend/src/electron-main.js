import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import os from 'os'; // Neuer Import
import { Buffer } from 'buffer';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow;
let backendProcess;


function getBasePath() {
    return app.isPackaged ? process.resourcesPath : path.join(__dirname, '..');
}


function getDistPath() {
    const basePath = getBasePath();
    const distPath = path.join(basePath, 'dist');
    if (fs.existsSync(distPath)) {
        return distPath;
    } else {
        const asarDistPath = path.join(basePath, 'app.asar', 'dist');
        if (fs.existsSync(asarDistPath)) {
            return asarDistPath;
        }
    }
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

    const javaPath = "java";
    backendProcess = spawn(javaPath, ['-jar', backendJar], {
        detached: true,
        shell: true,
        stdio: 'inherit',
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
        webPreferences: {
            preload: path.join(getBasePath(), 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false
        }
    });


    mainWindow.webContents.session.webRequest.onHeadersReceived((details, callback) => {
        callback({
            responseHeaders: {
                ...details.responseHeaders,
                'Content-Security-Policy': [
                    "default-src 'self' blob: file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080"
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
                    "default-src 'self' blob: file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080"
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

    printWindow.webContents.executeJavaScript(`
      if (!document.querySelector('meta[http-equiv="Content-Security-Policy"]')) {
        const meta = document.createElement('meta');
        meta.httpEquiv = "Content-Security-Policy";
        meta.content = "default-src 'self' blob: file:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; connect-src 'self' http://localhost:8080";
        document.head.appendChild(meta);
      }
    `).then(() => {
        console.log("[INFO] CSP-Meta-Tag im Report-Fenster eingefügt.");
    }).catch(err => {
        console.error("[ERROR] Beim Einfügen des CSP-Meta-Tags im Report-Fenster:", err);
    });

    printWindow.show();
});


ipcMain.handle('saveAndOpenPDF', async (event, pdfBase64) => {
    const tempDir = os.tmpdir();
    const tempPath = path.join(tempDir, 'report.pdf');
    fs.writeFileSync(tempPath, Buffer.from(pdfBase64, 'base64'));
    await shell.openPath(tempPath);
    return tempPath;
});

app.whenReady().then(() => {
    console.log("[APP] App ist bereit.");
    startBackend();
    createWindow();

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
