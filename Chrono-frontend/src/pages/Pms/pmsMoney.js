export function currencyDigits(currency = 'CHF') {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits;
}

export function currencyStep(currency = 'CHF') {
    const digits = currencyDigits(currency);
    return digits === 0 ? '1' : `0.${'0'.repeat(digits - 1)}1`;
}

export function formatPmsMoney(amount, currency = 'CHF', locale = 'de-CH') {
    return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(Number(amount || 0));
}
