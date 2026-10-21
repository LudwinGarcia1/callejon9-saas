// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ThemePreferenceContext,
  useThemePreference,
} from "@/hooks/use-theme-preference";

afterEach(cleanup);

function TestConsumer() {
  const { preference, resolved, systemDefault } = useThemePreference();
  return (
    <div>
      <span data-testid="pref">{preference}</span>
      <span data-testid="resolved">{resolved}</span>
      <span data-testid="sys">{systemDefault}</span>
    </div>
  );
}

describe("useThemePreference", () => {
  it("throws descriptive error when rendered outside ThemePreferenceContext", () => {
    // Prevent React from logging error boundaries in test output
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => render(<TestConsumer />)).toThrow(
      "useThemePreference debe usarse dentro del layout autenticado.",
    );

    consoleError.mockRestore();
  });

  it("returns context values when wrapped in ThemePreferenceContext.Provider", () => {
    const setPreference = vi.fn();
    render(
      <ThemePreferenceContext.Provider
        value={{
          preference: "dark",
          setPreference,
          resolved: "dark",
          systemDefault: "light",
        }}
      >
        <TestConsumer />
      </ThemePreferenceContext.Provider>,
    );

    expect(screen.getByTestId("pref").textContent).toBe("dark");
    expect(screen.getByTestId("resolved").textContent).toBe("dark");
    expect(screen.getByTestId("sys").textContent).toBe("light");
  });
});
