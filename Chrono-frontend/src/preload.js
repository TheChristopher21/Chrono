const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electron', {
    openPrintReport: (reportUrl) => ipcRenderer.invoke('openPrintReport', reportUrl),
    saveAndOpenPDF: (pdfBase64) => ipcRenderer.invoke('saveAndOpenPDF', pdfBase64)
});
