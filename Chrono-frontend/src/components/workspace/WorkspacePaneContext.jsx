import { createContext, useContext } from 'react';

export const WorkspacePaneContext = createContext(true);

// Public pages and components rendered outside a workspace pane stay active.
export const useWorkspacePaneActive = () => useContext(WorkspacePaneContext);
