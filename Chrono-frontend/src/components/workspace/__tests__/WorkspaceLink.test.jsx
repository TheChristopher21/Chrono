/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const workspace = vi.hoisted(() => ({ canHandleRoute: vi.fn(), openRoute: vi.fn() }));
vi.mock('../WorkspaceTabsContext.jsx', () => ({ useWorkspaceTabs: () => workspace }));
import WorkspaceLink from '../WorkspaceLink.jsx';

const renderLink = (props = {}) => {
    render(<MemoryRouter><WorkspaceLink to="/pms?section=guests" {...props}>Gäste</WorkspaceLink></MemoryRouter>);
    return screen.getByRole('link', { name: 'Gäste' });
};

describe('WorkspaceLink mouse navigation', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        workspace.canHandleRoute.mockReturnValue(true);
        workspace.openRoute.mockReturnValue(true);
    });

    it('uses an existing app tab for a normal click', () => {
        const closeMenu = vi.fn();
        const link = renderLink({ onClick: closeMenu });
        expect(fireEvent.click(link, { button: 0 })).toBe(false);
        expect(closeMenu).toHaveBeenCalledOnce();
        expect(workspace.openRoute).toHaveBeenCalledWith('/pms?section=guests');
    });

    it('opens exactly one extra app tab on middle click and prevents native browser tabs', () => {
        const link = renderLink();
        expect(fireEvent.mouseDown(link, { button: 1 })).toBe(false);
        expect(workspace.openRoute).not.toHaveBeenCalled();
        expect(fireEvent(link, new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true }))).toBe(false);
        expect(workspace.openRoute).toHaveBeenCalledExactlyOnceWith('/pms?section=guests', { forceNew: true });
    });

    it('leaves back/forward mouse buttons to native browser history', () => {
        const link = renderLink();
        for (const button of [3, 4]) {
            expect(fireEvent(link, new MouseEvent('auxclick', { button, bubbles: true, cancelable: true }))).toBe(true);
        }
        expect(workspace.openRoute).not.toHaveBeenCalled();
    });

    it('does not navigate around the limit when all tabs are pinned', () => {
        workspace.openRoute.mockReturnValue(false);
        expect(fireEvent.click(renderLink(), { button: 0 })).toBe(false);
    });

    it('respects an explicitly cancelled click', () => {
        fireEvent.click(renderLink({ onClick: (event) => event.preventDefault() }), { button: 0 });
        expect(workspace.openRoute).not.toHaveBeenCalled();
    });

    it('preserves explicit browser targets', () => {
        fireEvent(renderLink({ target: '_blank' }), new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true }));
        expect(workspace.openRoute).not.toHaveBeenCalled();
    });
});
