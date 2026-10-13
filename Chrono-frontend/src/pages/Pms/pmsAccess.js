export const PMS_SECTION_PERMISSIONS = Object.freeze({
    reservations: 'FRONT_DESK', 'room-plan': 'FRONT_DESK', groups: 'FRONT_DESK',
    guests: 'GUESTS', organizations: 'GUESTS', 'digital-check-in': 'GUESTS', communications: 'GUESTS',
    housekeeping: 'HOUSEKEEPING', folios: 'FINANCE', invoices: 'FINANCE', audit: 'FINANCE',
    commerce: 'FINANCE', rates: 'RATES', reports: 'REPORTS', portfolio: 'REPORTS',
    integrations: 'INTEGRATIONS', events: 'FRONT_DESK',
});

export function pmsHasPermission(access, propertyId, permission, manage = false) {
    if (access?.master) return true;
    const level = access?.properties?.find((property) => String(property.propertyId) === String(propertyId))?.permissions?.[permission];
    return manage ? level === 'MANAGE' : level === 'MANAGE' || level === 'VIEW';
}
