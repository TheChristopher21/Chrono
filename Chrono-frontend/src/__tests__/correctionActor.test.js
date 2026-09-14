import { describe, expect, it, vi } from 'vitest';
import { formatEntrySourceIndicator, formatRequestAdmin } from '../utils/correctionActor';

const t = vi.fn((_key, fallback) => fallback);

describe('correction actor formatting', () => {
    it('shows the admin initials after an admin correction', () => {
        expect(formatEntrySourceIndicator({
            source: 'ADMIN_CORRECTION',
            correctionAdminInitials: 'AB',
        }, t)).toBe(' (AdmK AB)');
    });

    it('shows who approved a user correction', () => {
        expect(formatEntrySourceIndicator({
            source: 'USER_CORRECTION',
            correctionAdminInitials: 'CD',
        }, t)).toBe(' (UsrK · AdmK CD)');
        expect(formatRequestAdmin({ processedByAdminInitials: 'CD' }, t)).toBe('AdmK CD');
    });

    it('keeps the old label for historical entries without actor data', () => {
        expect(formatEntrySourceIndicator({ source: 'ADMIN_CORRECTION' }, t)).toBe(' (AdmK)');
    });
});
