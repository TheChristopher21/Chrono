import { describe, expect, it } from "vitest";
import { csvCell, csvRow } from "../csv.js";

describe("spreadsheet-safe CSV text cells", () => {
    it.each(["=1+1", "+SUM(A1:A2)", "-1+2", "@SUM(A1)", "\t=1+1", " \r\n=1+1", "\0=1+1", "\u200b=1+1", "\ufeff@A1", "＝1+1"])(
        "keeps stored text %j literal", (text) => {
            expect(csvCell(text)).toBe(`"'${text}"`);
        },
    );

    it("preserves normal names, punctuation, line breaks and actual numeric amounts", () => {
        expect(csvRow(["Praxis", 'Artikel, "A"', "Zeile\nZwei", -12.5, 0, null]))
            .toBe('"Praxis","Artikel, ""A""","Zeile\nZwei","-12.5","0",""');
    });

    it("protects product text while keeping the same export columns", () => {
        const inventory = [{ sku: "P-1", product: "=1+1", quantity: 12 }];
        const keys = ["sku", "product", "quantity"];
        expect(inventory.map((row) => csvRow(keys.map((key) => row[key]))))
            .toEqual(['"P-1","\'=1+1","12"']);
    });
});
