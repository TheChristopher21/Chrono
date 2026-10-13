// Electron emits the physical mouse back/forward buttons as app commands on
// Windows and Linux. Use the same Chromium history as React Router.
export function installWindowHistoryNavigation(window) {
    window.on('app-command', (_event, command) => {
        const history = window.webContents.navigationHistory;
        if (command === 'browser-backward' && history.canGoBack()) history.goBack();
        if (command === 'browser-forward' && history.canGoForward()) history.goForward();
    });
}
