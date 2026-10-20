import { describe, expect, it, vi } from 'vitest';
import { installWindowHistoryNavigation } from '../electron-navigation.js';

describe('Electron mouse history navigation', () => {
    it('uses Chromium history for mouse back and forward commands', () => {
        const navigationHistory = { canGoBack: () => true, canGoForward: () => true, goBack: vi.fn(), goForward: vi.fn() };
        const window = { on: vi.fn(), webContents: { navigationHistory } };
        installWindowHistoryNavigation(window);
        expect(window.on).toHaveBeenCalledWith('app-command', expect.any(Function));
        const handler = window.on.mock.calls[0][1];
        handler({}, 'browser-backward');
        handler({}, 'browser-forward');
        handler({}, 'media-play-pause');
        expect(navigationHistory.goBack).toHaveBeenCalledTimes(1);
        expect(navigationHistory.goForward).toHaveBeenCalledTimes(1);
    });

    it('does not navigate past either end of browser history', () => {
        const navigationHistory = { canGoBack: () => false, canGoForward: () => false, goBack: vi.fn(), goForward: vi.fn() };
        const window = { on: vi.fn(), webContents: { navigationHistory } };
        installWindowHistoryNavigation(window);
        const handler = window.on.mock.calls[0][1];
        handler({}, 'browser-backward');
        handler({}, 'browser-forward');
        expect(navigationHistory.goBack).not.toHaveBeenCalled();
        expect(navigationHistory.goForward).not.toHaveBeenCalled();
    });
});
