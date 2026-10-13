/** @vitest-environment node */
import fs from 'node:fs';
import { describe, expect, it } from 'vitest';

const scope = '.admin-projects-page.scoped-dashboard.neo-dashboard';
const css = fs.readFileSync(
    new URL('../../styles/AdminProjectsPageScoped.css', import.meta.url),
    'utf8'
);

describe('AdminProjectsPage scoped styles', () => {
    it('uses the application theme attribute instead of the operating-system theme', () => {
        expect(css).not.toContain('prefers-color-scheme');
        expect(css).toContain(`[data-theme="light"] ${scope}`);
        expect(css).toContain(`[data-theme="dark"] ${scope}`);
    });

    it('keeps every style rule inside the AdminProjects page scope', () => {
        const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '');
        const rulePreludePattern = /([^{}]+)\{/g;
        let match;

        while ((match = rulePreludePattern.exec(withoutComments)) !== null) {
            const prelude = match[1].trim();
            if (prelude.startsWith('@')) continue;

            prelude.split(',').forEach((selector) => {
                expect(selector, `Unscoped selector: ${selector}`).toContain(scope);
            });
        }
    });

    it('contains responsive overflow protection for narrow screens', () => {
        const rootBlock = css.match(
            /\.admin-projects-page\.scoped-dashboard\.neo-dashboard\s*\{([^}]*)\}/
        )?.[1] ?? '';

        expect(css).toMatch(/@media\s*\(max-width:\s*900px\)/);
        expect(css).toMatch(/@media\s*\(max-width:\s*768px\)/);
        expect(rootBlock).toContain('box-sizing: border-box');
        expect(rootBlock).toContain('overflow-x: clip');
        expect(css).toContain('overflow-wrap: anywhere');
        expect(css).toContain('min-width: 0');
    });
});
