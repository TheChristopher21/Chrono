// C:\Users\siefe\IdeaProjects\Chrono\Chrono-frontend\src\test\setup.js
import '@testing-library/jest-dom/vitest'; // <- bringt toBeInTheDocument etc.

import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
afterEach(() => cleanup());

// (optional) matchMedia-Mock, falls nötig:
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},            // deprecated, aber manche Libs rufen es
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false
    }),
});
