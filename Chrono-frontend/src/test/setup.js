import '@testing-library/jest-dom';

// Node's native channel connects independent Vitest worker threads, unlike
// isolated jsdom windows. Cross-tab tests install their own scoped channel fake.
Object.defineProperty(globalThis, 'BroadcastChannel', {
    configurable: true,
    writable: true,
    value: undefined,
});
