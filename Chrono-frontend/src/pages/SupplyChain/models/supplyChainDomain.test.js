import { describe, expect, it } from "vitest";
import { normalizeStock } from "./supplyChainDomain.js";

describe("normalizeStock", () => {
    it("keeps production inventory dimensions and availability", () => {
        expect(normalizeStock({
            id: 41,
            product: { id: 7 },
            warehouse: { id: 3 },
            warehouseBin: { id: 9, code: "A-01-02" },
            quantity: 12,
            reservedQuantity: 4,
            availableQuantity: 8,
            lotNumber: "LOT-26-01",
            serialNumber: null,
            expirationDate: "2027-01-10",
            inventoryStatus: "QUALITY_INSPECTION",
        })).toMatchObject({
            productId: 7,
            warehouseId: 3,
            warehouseBinId: 9,
            warehouseBinCode: "A-01-02",
            quantity: 12,
            reservedQuantity: 4,
            availableQuantity: 8,
            lotNumber: "LOT-26-01",
            expirationDate: "2027-01-10",
            status: "QUALITY_INSPECTION",
            buckets: { AVAILABLE: 8, RESERVED: 4 },
        });
    });
});
