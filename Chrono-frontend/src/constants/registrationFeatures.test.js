import { describe, expect, it } from "vitest";
import { FEATURE_CATALOG, TOGGLABLE_FEATURE_KEYS } from "./registrationFeatures";

describe("registration feature catalog", () => {
    it("offers the PMS pilot module for a flat monthly price", () => {
        const pms = FEATURE_CATALOG.find((feature) => feature.key === "pms");

        expect(pms).toMatchObject({
            name: "Hotelmanagement (PMS) (in finaler Entwicklungsphase)",
            price: 249,
            priceType: "flat",
            required: false,
            alwaysAvailable: false,
        });
        expect(TOGGLABLE_FEATURE_KEYS).toContain("pms");
    });
});
