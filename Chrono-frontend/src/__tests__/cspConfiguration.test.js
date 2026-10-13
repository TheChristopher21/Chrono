/** @vitest-environment node */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const readFrontendFile = (relativePath) => readFileSync(resolve(frontendRoot, relativePath), 'utf8');

describe('production Content Security Policy', () => {
    it('loads executable HTML scripts from external files only', () => {
        const indexHtml = readFrontendFile('index.html');
        const scripts = [...indexHtml.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
        const inlineScripts = scripts.filter(([, attributes, body]) => (
            !/\bsrc\s*=/.test(attributes) && body.trim().length > 0
        ));

        expect(indexHtml).toContain('<script src="/theme-init.js"></script>');
        expect(inlineScripts).toEqual([]);
    });

    it('keeps the web-server policy strict and permits PrivacyBee styles', () => {
        const nginxConfig = readFrontendFile('nginx.conf');
        const policy = nginxConfig.match(/add_header Content-Security-Policy "([^"]+)" always;/)?.[1];

        expect(policy).toBeDefined();
        expect(policy).toContain("script-src 'self' https://app.privacybee.io;");
        expect(policy).not.toMatch(/script-src[^;]*'unsafe-inline'/);
        expect(policy).toContain("style-src 'self' 'unsafe-inline' https://app.privacybee.io;");
    });
});
