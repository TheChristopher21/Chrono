import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import OutboundOrderDrawerActions from "./OutboundOrderDrawerActions.jsx";

const text = {
    title: "Reservieren & ausliefern", description: "FEFO", ordered: "Bestellt", reserved: "Reserviert",
    fulfilled: "Erfüllt", warehouse: "Lager", selectWarehouse: "Lager wählen", reserve: "Bestand reservieren",
    fulfill: "Kommissionieren & ausbuchen", working: "Wird verarbeitet", failed: "Fehler",
};

describe("OutboundOrderDrawerActions", () => {
    it("shows quantities and starts a reservation for the selected warehouse", async () => {
        const onReserve = vi.fn().mockResolvedValue(undefined);
        render(
            <OutboundOrderDrawerActions
                record={{ id: 12, status: "CONFIRMED", lines: [{ quantity: 5, reservedQuantity: 2, fulfilledQuantity: 1 }] }}
                warehouses={[{ id: 4, code: "MAIN", name: "Hauptlager" }]}
                text={text}
                onReserve={onReserve}
                onFulfill={vi.fn()}
            />
        );

        expect(screen.getByText("5")).toBeInTheDocument();
        expect(screen.getByText("2")).toBeInTheDocument();
        expect(screen.getByText("1")).toBeInTheDocument();
        await userEvent.click(screen.getByRole("button", { name: "Bestand reservieren" }));
        expect(onReserve).toHaveBeenCalledWith(12, 4);
    });
});
