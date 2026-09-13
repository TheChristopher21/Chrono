/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const workspaceMock = vi.hoisted(() => ({
    tabs: [
        { id: 'dashboard', title: 'Dashboard', icon: 'Zeit', pinned: false },
        { id: 'admin', title: 'Admin', icon: 'AD', pinned: false },
    ],
    activeTabId: 'dashboard',
    activeTab: { id: 'dashboard', title: 'Dashboard', icon: 'Zeit' },
    recentlyClosed: [{ id: 'closed', title: 'PMS', url: '/pms' }],
    launchItems: [{ key: 'pms', label: 'PMS', description: 'Hotel', group: 'Module', icon: 'PMS', url: '/pms' }],
    activateTab: vi.fn(),
    openRoute: vi.fn(() => true),
    closeTab: vi.fn(),
    togglePinned: vi.fn(),
    reorderTab: vi.fn(),
    closeOtherTabs: vi.fn(),
    closeTabsToRight: vi.fn(),
    restoreLastClosed: vi.fn(() => true),
}));

vi.mock('../WorkspaceTabsContext.jsx', () => ({ useWorkspaceTabs: () => workspaceMock }));
vi.mock('../../../context/LanguageContext.jsx', () => ({
    useTranslation: () => ({ t: (_key, fallback) => fallback }),
}));

import WorkspaceTabStrip from '../WorkspaceTabStrip.jsx';

describe('WorkspaceTabStrip interactions', () => {
    beforeEach(() => {
        Object.values(workspaceMock).forEach((value) => {
            if (typeof value === 'function' && 'mockClear' in value) value.mockClear();
        });
        workspaceMock.openRoute.mockReturnValue(true);
        window.requestAnimationFrame = (callback) => {
            callback();
            return 1;
        };
    });

    it('opens an extra instance from the launcher with the middle mouse button', async () => {
        render(<WorkspaceTabStrip />);
        await userEvent.click(screen.getAllByRole('button', { name: 'Arbeitsbereich öffnen' })[0]);
        const item = within(screen.getByRole('dialog')).getByRole('button', { name: /PMS/ });
        fireEvent(item, new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true }));
        expect(workspaceMock.openRoute).toHaveBeenCalledExactlyOnceWith('/pms', { forceNew: true });
    });

    it('closes an unpinned tab with the middle mouse button', () => {
        render(<WorkspaceTabStrip />);
        fireEvent(screen.getByRole('tab', { name: 'Dashboard' }), new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true }));
        expect(workspaceMock.closeTab).toHaveBeenCalledWith('dashboard');
        expect(workspaceMock.activateTab).not.toHaveBeenCalled();
    });

    it('moves focus with arrow navigation and returns focus when the actions menu closes', async () => {
        const user = userEvent.setup();
        render(<WorkspaceTabStrip />);
        const dashboardTab = screen.getByRole('tab', { name: 'Dashboard' });
        const adminTab = screen.getByRole('tab', { name: 'Admin' });
        dashboardTab.focus();

        fireEvent.keyDown(dashboardTab, { key: 'ArrowRight' });
        expect(workspaceMock.activateTab).toHaveBeenCalledWith('admin');
        expect(adminTab).toHaveFocus();

        fireEvent.keyDown(adminTab, { key: 'Home' });
        expect(workspaceMock.activateTab).toHaveBeenCalledWith('dashboard');
        expect(dashboardTab).toHaveFocus();

        fireEvent.keyDown(dashboardTab, { key: 'End' });
        expect(workspaceMock.activateTab).toHaveBeenCalledWith('admin');
        expect(adminTab).toHaveFocus();

        const menuTrigger = screen.getByRole('button', { name: 'Tab-Aktionen' });
        await user.click(menuTrigger);
        const menu = screen.getByRole('menu');
        const menuItems = within(menu).getAllByRole('menuitem');
        expect(menuItems[0]).toHaveFocus();
        fireEvent.keyDown(menu, { key: 'ArrowDown' });
        expect(menuItems[1]).toHaveFocus();
        fireEvent.keyDown(menu, { key: 'End' });
        expect(menuItems[2]).toHaveFocus();
        fireEvent.keyDown(document, { key: 'Escape' });
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(menuTrigger).toHaveFocus();
    });

    it('closes the mobile drawer after selecting or restoring a tab', async () => {
        const user = userEvent.setup();
        const { container } = render(<WorkspaceTabStrip />);
        const trigger = container.querySelector('.workspace-mobile-trigger');

        fireEvent.click(trigger);
        let drawer = container.querySelector('.workspace-mobile-drawer');
        fireEvent.click(drawer.querySelector('button[title="Admin"]'));
        expect(workspaceMock.activateTab).toHaveBeenCalledWith('admin');
        expect(container.querySelector('.workspace-mobile-drawer')).not.toBeInTheDocument();

        fireEvent.click(trigger);
        drawer = container.querySelector('.workspace-mobile-drawer');
        fireEvent.click([...drawer.querySelectorAll('button')]
            .find((button) => button.textContent.includes('Geschlossenen Tab wiederherstellen')));
        expect(workspaceMock.restoreLastClosed).toHaveBeenCalled();
        expect(container.querySelector('.workspace-mobile-drawer')).not.toBeInTheDocument();
    });

    it('exposes clear state and relationships for tabs and controls', () => {
        const { container } = render(<WorkspaceTabStrip />);
        const dashboardTab = screen.getByRole('tab', { name: 'Dashboard' });
        const adminTab = screen.getByRole('tab', { name: 'Admin' });

        expect(dashboardTab).toHaveAttribute('aria-selected', 'true');
        expect(dashboardTab).toHaveAttribute('tabindex', '0');
        expect(adminTab).toHaveAttribute('aria-selected', 'false');
        expect(adminTab).toHaveAttribute('tabindex', '-1');

        const dashboardPin = screen.getByRole('button', { name: 'Tab anheften: Dashboard' });
        expect(dashboardPin).toHaveAttribute('aria-pressed', 'false');
        expect(screen.getByRole('button', { name: 'Tab anheften: Admin' })).toBeInTheDocument();

        const desktopLauncher = container.querySelector('.workspace-tab-add');
        expect(desktopLauncher).toHaveAttribute('aria-haspopup', 'dialog');
        expect(desktopLauncher).toHaveAttribute('aria-controls', 'workspace-launcher-desktop');

        const mobileTrigger = container.querySelector('.workspace-mobile-trigger');
        expect(mobileTrigger).toHaveAttribute('aria-controls', 'workspace-mobile-drawer');
    });

    it('closes the mobile drawer with Escape and restores focus to its trigger', async () => {
        const user = userEvent.setup();
        const { container } = render(<WorkspaceTabStrip />);
        const trigger = container.querySelector('.workspace-mobile-trigger');

        await user.click(trigger);
        expect(container.querySelector('.workspace-mobile-drawer')).toBeInTheDocument();
        fireEvent.keyDown(document, { key: 'Escape' });

        await waitFor(() => expect(container.querySelector('.workspace-mobile-drawer')).not.toBeInTheDocument());
        expect(trigger).toHaveFocus();
    });

    it('keeps the launcher open and reports the limit when opening is rejected', async () => {
        const user = userEvent.setup();
        workspaceMock.openRoute.mockReturnValue(false);
        render(<WorkspaceTabStrip />);

        await user.click(screen.getByTitle(/Arbeitsbereich öffnen/));
        const dialogs = screen.getAllByRole('dialog', { name: 'Arbeitsbereich öffnen' });
        await user.click(within(dialogs[0]).getByRole('button', { name: /PMS/ }));

        expect(screen.getAllByRole('alert')[0]).toHaveTextContent('Maximal 12 Tabs');
        expect(screen.getAllByRole('dialog', { name: 'Arbeitsbereich öffnen' }).length).toBeGreaterThan(0);
    });
});
