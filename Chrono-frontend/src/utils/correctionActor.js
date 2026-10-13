export const formatCorrectionAdmin = (initials, t) => {
    const normalizedInitials = typeof initials === 'string' ? initials.trim() : '';
    if (!normalizedInitials) return '';
    const adminLabel = t('adminDashboard.entrySource.adminLabel', 'AdmK');
    return `${adminLabel} ${normalizedInitials}`;
};

export const formatEntrySourceIndicator = (entry, t) => {
    if (!entry) return '';

    if (entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser) {
        return t('adminDashboard.entrySource.autoSuffix', ' (Auto)');
    }

    const adminActor = formatCorrectionAdmin(entry.correctionAdminInitials, t);
    if (entry.source === 'ADMIN_CORRECTION') {
        return adminActor
            ? ` (${adminActor})`
            : t('adminDashboard.entrySource.adminSuffix', ' (AdmK)');
    }
    if (entry.source === 'USER_CORRECTION') {
        const userLabel = t('adminDashboard.entrySource.userLabel', 'UsrK');
        return adminActor
            ? ` (${userLabel} · ${adminActor})`
            : t('adminDashboard.entrySource.userSuffix', ' (UsrK)');
    }
    if (entry.source === 'MANUAL_IMPORT') {
        return t('adminDashboard.entrySource.importSuffix', ' (Imp)');
    }
    return '';
};

export const formatRequestAdmin = (request, t) => (
    formatCorrectionAdmin(request?.processedByAdminInitials, t)
);
