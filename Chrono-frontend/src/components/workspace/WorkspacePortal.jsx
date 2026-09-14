import { createPortal } from 'react-dom';
import { useWorkspacePaneActive } from './WorkspacePaneContext.jsx';

// Activity hides ordinary DOM, but a portal can live outside that hidden tree.
// Keep overlays tied to the visible pane as well.
export default function WorkspacePortal({ children, container }) {
    const active = useWorkspacePaneActive();
    if (!active || typeof document === 'undefined') return null;
    return createPortal(children, container ?? document.body);
}
