import { createContext, useContext } from 'react';

export const AdminWorkspaceShellContext = createContext(false);
export const useAdminWorkspaceShell = () => useContext(AdminWorkspaceShellContext);
