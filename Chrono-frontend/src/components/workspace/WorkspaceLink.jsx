import { Link } from 'react-router-dom';
import { useWorkspaceTabs } from './WorkspaceTabsContext.jsx';

// Keep native links for public/external destinations and browser modifier shortcuts.
export default function WorkspaceLink({ to, onClick, onAuxClick, onMouseDown, target, download, ...props }) {
    const workspace = useWorkspaceTabs();
    const canUseWorkspace = () => !download && (!target || target === '_self')
        && typeof to === 'string' && workspace?.canHandleRoute(to);

    return <Link {...props} to={to} target={target} download={download}
        onClick={(event) => {
            onClick?.(event);
            if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            if (!canUseWorkspace()) return;
            event.preventDefault();
            workspace.openRoute(to);
        }}
        onMouseDown={(event) => {
            onMouseDown?.(event);
            if (event.button === 1 && canUseWorkspace()) event.preventDefault();
        }}
        onAuxClick={(event) => {
            onAuxClick?.(event);
            if (event.defaultPrevented || event.button !== 1 || !canUseWorkspace()) return;
            event.preventDefault();
            workspace.openRoute(to, { forceNew: true });
        }}
    />;
}
