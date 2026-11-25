// CSV quoting and spreadsheet text interpretation are separate boundaries.
// Keep actual numeric values numeric; never let stored text become a formula.
const FORMULA_PREFIX = /^[\p{White_Space}\p{Cc}\p{Cf}]*[=+\-@＝＋－＠]/u;

export function csvCell(value) {
    let text = String(value ?? "");
    if (typeof value === "string" && FORMULA_PREFIX.test(text)) text = `'${text}`;
    return `"${text.replaceAll('"', '""')}"`;
}

export function csvRow(values) {
    return values.map(csvCell).join(",");
}
