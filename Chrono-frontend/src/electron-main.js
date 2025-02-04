import { app, BrowserWindow } from 'electron';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow;
let backendProcess;

function getJavaPath() {
    if (process.platform === "win32") {
        return "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe";
    } else {
        return "/usr/bin/java"; // Standard-Pfad unter Linux
    }
}

function startBackend() {
    let backendJar;

    if (process.platform === 'win32') {
        backendJar = path.join(process.resourcesPath, 'backend', 'Chrono-0.0.1-SNAPSHOT.jar');
    } else {
        backendJar = path.join(process.resourcesPath, 'backend', 'Chrono-0.0.1-SNAPSHOT.jar');
    }

    console.log("🚀 Backend JAR Pfad:", backendJar);

    if (!fs.existsSync(backendJar)) {
        console.error("❌ Backend JAR nicht gefunden:", backendJar);
        return;
    }

    let javaPath = "java";  // Unter Linux reicht einfach "java"

    backendProcess = spawn(javaPath, ['-jar', backendJar], {
        detached: true,
        shell: false,
        stdio: 'ignore'
    });

    backendProcess.unref();
}


function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1024,
        height: 768,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    const indexPath = path.join(__dirname, '..', 'dist', 'index.html');
    console.log("Lade index.html von:", indexPath);
    mainWindow.loadFile(indexPath);
}

app.whenReady().then(() => {
    startBackend(); // Starte das Backend
    createWindow();

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        if (backendProcess) backendProcess.kill();
        app.quit();
    }
});
