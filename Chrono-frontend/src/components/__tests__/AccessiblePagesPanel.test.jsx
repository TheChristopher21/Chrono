/** @vitest-environment jsdom */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";

import AccessiblePagesPanel from "../AccessiblePagesPanel.jsx";
import { AuthContext } from "../../context/AuthContext.jsx";

const adminUser = {
    id: 7,
    username: "admin",
    roles: ["ROLE_ADMIN"],
    companyFeatureKeys: ["projects"],
    pagePermissions: {
        adminDashboard: "MANAGE",
        adminUsers: "MANAGE",
        adminProjects: "VIEW",
    },
};

const renderPanel = (currentUser = adminUser) => render(
    <MemoryRouter initialEntries={["/admin/dashboard"]}>
        <AuthContext.Provider value={{ currentUser }}>
            <AccessiblePagesPanel
                context="admin"
                title="Freigegebene Admin-Seiten"
                subtitle="Diese Kacheln richten sich direkt nach den vergebenen Benutzerrechten."
            />
        </AuthContext.Provider>
    </MemoryRouter>
);

describe("AccessiblePagesPanel", () => {
    beforeEach(() => {
        window.localStorage.clear();
    });

    it("collapses the page grid and stores the state for the current user", async () => {
        const user = userEvent.setup();
        renderPanel();

        expect(screen.getByText("Benutzerverwaltung")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: /seiten einklappen/i }));

        expect(screen.queryByText("Benutzerverwaltung")).toBeNull();
        expect(screen.getByRole("button", { name: /seiten ausklappen/i }))
            .toHaveAttribute("aria-expanded", "false");
        expect(window.localStorage.getItem("chrono.accessiblePagesPanel.admin.7")).toBe("collapsed");
    });

    it("restores a previously collapsed panel on the next render", () => {
        window.localStorage.setItem("chrono.accessiblePagesPanel.admin.7", "collapsed");

        renderPanel();

        expect(screen.queryByText("Benutzerverwaltung")).toBeNull();
        expect(screen.getByRole("button", { name: /seiten ausklappen/i })).toBeInTheDocument();
    });
});
