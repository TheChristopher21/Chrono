import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent, within } from "@testing-library/react";

vi.mock("@react-three/fiber", () => ({
    Canvas: () => <div data-testid="three-canvas" />,
}));

vi.mock("@react-three/drei", () => ({
    Line: () => null,
    OrbitControls: () => null,
}));

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock("../utils/api.js", () => ({
    default: {
        get: (...args) => mockGet(...args),
        post: (...args) => mockPost(...args),
    },
}));

import ChronoTwoDashboard from "../pages/ChronoTwo/ChronoTwoDashboard.jsx";

const products = [
    {
        id: "SKU-AR-01",
        name: "Chrono Vision Bot",
        category: "Robotics",
        weightKg: 18.5,
        salesPrice: 1799,
        demandSegment: "A",
    },
];

const locations = [
    { id: "A-01-01", zone: "A", x: 2, y: 4, z: 1, capacity: 80, occupied: 24 },
    { id: "B-01-02", zone: "B", x: 10, y: 3, z: 2, capacity: 70, occupied: 40 },
];

const inventory = [
    { productId: "SKU-AR-01", locationId: "A-01-01", quantity: 24 },
];

const kpis = {
    kpis: { pick_rate: 318, inventory_turnover: 8.6 },
    trends: { pick_rate: 4.2, inventory_turnover: 1.1 },
};

const returnsData = [
    { caseId: "RET-1", productId: "SKU-AR-GLV", reason: "Defekt", status: "inspection" },
];

const ledger = [
    {
        id: "MOV-1",
        productId: "SKU-AR-01",
        fromLocation: "A-01-01",
        toLocation: "B-01-02",
        quantity: 5,
    },
];

const forecast = {
    productId: "SKU-AR-01",
    forecast: { "2025-01-01": 120 },
    stockOutRisk: false,
    overstockRisk: false,
};

const pickRouteResponse = {
    waypoints: [
        { locationId: "A-01-01", productId: "SKU-AR-01", x: 2, y: 4, z: 1, etaSeconds: 12.5 },
        { locationId: "B-01-02", productId: "SKU-AR-01", x: 10, y: 3, z: 2, etaSeconds: 34.8 },
    ],
    totalDistance: 18.2,
    estimatedDurationSeconds: 52.6,
};

beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();

    mockGet.mockImplementation((url) => {
        switch (url) {
            case "/chrono2/products":
                return Promise.resolve({ data: products });
            case "/chrono2/locations":
                return Promise.resolve({ data: locations });
            case "/chrono2/inventory":
                return Promise.resolve({ data: inventory });
            case "/chrono2/analytics/kpis":
                return Promise.resolve({ data: kpis });
            case "/chrono2/outbound/returns":
                return Promise.resolve({ data: returnsData });
            case "/chrono2/blockchain/movement":
                return Promise.resolve({ data: ledger });
            default:
                if (url.startsWith("/chrono2/inventory/") && url.endsWith("/forecast")) {
                    return Promise.resolve({ data: forecast });
                }
                return Promise.resolve({ data: {} });
        }
    });

    mockPost.mockImplementation((url, payload) => {
        if (url === "/chrono2/outbound/pick-route") {
            return Promise.resolve({ data: pickRouteResponse });
        }
        return Promise.resolve({ data: payload });
    });
});

describe("ChronoTwoDashboard", () => {
    it("renders data and submits pick route payload", async () => {
        render(<ChronoTwoDashboard />);

        expect(await screen.findByText("Chrono Vision Bot")).toBeInTheDocument();

        const pickCard = screen.getByText("Pick-by-Vision Routen").closest("article");
        const productInput = within(pickCard).getByLabelText("Produkt-ID");
        fireEvent.change(productInput, { target: { value: "SKU-AR-01" } });

        const quantityInput = within(pickCard).getByLabelText("Menge");
        fireEvent.change(quantityInput, { target: { value: "3" } });

        const submitButton = within(pickCard).getByRole("button", { name: "Route planen" });
        fireEvent.click(submitButton);

        await waitFor(() => {
            expect(mockPost).toHaveBeenCalledWith("/chrono2/outbound/pick-route", {
                items: [{ productId: "SKU-AR-01", quantity: 3 }],
            });
        });

        expect(await within(pickCard).findByText(/Gesamtzeit:/)).toBeInTheDocument();
        const skuMentions = await within(pickCard).findAllByText(/SKU-AR-01/);
        expect(skuMentions.length).toBeGreaterThan(0);
    });
});
