// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  readStoredPreference,
  resolveThemeMode,
  storePreference,
  THEME_PREFERENCES,
  THEME_STORAGE_KEY,
} from "@/lib/theme-mode";

describe("theme-mode", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("exports correct key and theme preferences", () => {
    expect(THEME_STORAGE_KEY).toBe("callejon9:theme");
    expect(THEME_PREFERENCES).toEqual([
      { value: "auto", label: "Auto" },
      { value: "light", label: "Claro" },
      { value: "dark", label: "Oscuro" },
    ]);
  });

  describe("readStoredPreference", () => {
    it("returns auto when storage is empty", () => {
      expect(readStoredPreference()).toBe("auto");
    });

    it("returns valid stored preference", () => {
      window.localStorage.setItem(THEME_STORAGE_KEY, "dark");
      expect(readStoredPreference()).toBe("dark");

      window.localStorage.setItem(THEME_STORAGE_KEY, "light");
      expect(readStoredPreference()).toBe("light");

      window.localStorage.setItem(THEME_STORAGE_KEY, "auto");
      expect(readStoredPreference()).toBe("auto");
    });

    it("falls back to auto when storage has invalid value", () => {
      window.localStorage.setItem(THEME_STORAGE_KEY, "neon-blue");
      expect(readStoredPreference()).toBe("auto");
    });

    it("falls back to auto when localStorage access throws", () => {
      vi.spyOn(window.localStorage, "getItem").mockImplementation(() => {
        throw new Error("SecurityError: Access is denied");
      });
      expect(readStoredPreference()).toBe("auto");
    });
  });

  describe("storePreference", () => {
    it("stores valid preference to localStorage", () => {
      storePreference("dark");
      expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");

      storePreference("light");
      expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");
    });

    it("swallows errors when localStorage.setItem throws", () => {
      vi.spyOn(window.localStorage, "setItem").mockImplementation(() => {
        throw new Error("QuotaExceededError");
      });
      expect(() => storePreference("dark")).not.toThrow();
    });
  });

  describe("resolveThemeMode", () => {
    it("returns system default when preference is auto", () => {
      expect(resolveThemeMode("auto", "light")).toBe("light");
      expect(resolveThemeMode("auto", "dark")).toBe("dark");
    });

    it("returns explicit preference when set to light or dark", () => {
      expect(resolveThemeMode("light", "dark")).toBe("light");
      expect(resolveThemeMode("light", "light")).toBe("light");
      expect(resolveThemeMode("dark", "light")).toBe("dark");
      expect(resolveThemeMode("dark", "dark")).toBe("dark");
    });
  });
});
