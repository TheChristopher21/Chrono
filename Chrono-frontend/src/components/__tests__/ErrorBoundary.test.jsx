/** @vitest-environment jsdom */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import ErrorBoundary from "../ErrorBoundary.jsx";

function ThrowError() {
    throw new Error("Test error");
}

describe("ErrorBoundary", () => {
    let consoleErrorSpy;

    beforeEach(() => {
        consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    });

    afterEach(() => {
        consoleErrorSpy.mockRestore();
    });

    it("renders fallback UI and reloads on button click", async () => {
        const reloadMock = vi.fn();
        const originalLocation = window.location;
        Object.defineProperty(window, "location", {
            writable: true,
            value: { ...originalLocation, reload: reloadMock },
        });

        render(
            <ErrorBoundary>
                <ThrowError />
            </ErrorBoundary>
        );

        expect(
            screen.getByText(/unerwarteter Fehler aufgetreten/i)
        ).toBeInTheDocument();
        const button = screen.getByRole("button", { name: /neu laden/i });
        expect(button).toBeInTheDocument();

        await userEvent.click(button);
        expect(reloadMock).toHaveBeenCalled();

        Object.defineProperty(window, "location", { value: originalLocation });
    });
});
