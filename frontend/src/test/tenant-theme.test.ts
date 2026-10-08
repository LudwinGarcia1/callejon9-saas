import { describe, expect, it } from "vitest";
import { DEFAULT_IDENTITY, identityStyle, monogramOf, resolveIdentity, type TenantIdentity } from "@/lib/tenant-theme";

describe("tenant branding boundaries", () => {
  it("preserves known branding and falls back for unknown slugs", () => {
    expect(resolveIdentity("cafeteria-norte")).toMatchObject({ hue: 152, displayFont: "grotesk" });
    expect(resolveIdentity("unknown")).toEqual(DEFAULT_IDENTITY);
    expect(resolveIdentity(undefined)).toEqual(DEFAULT_IDENTITY);
  });
  it("bounds numeric CSS variables", () => {
    expect(identityStyle({ ...DEFAULT_IDENTITY, hue: -1, hueDark: 361, chroma: 2, chromaDark: -1 }))
      .toMatchObject({ "--brand-hue": "0", "--brand-hue-dark": "360", "--brand-chroma": "0.4", "--brand-chroma-dark": "0" });
  });
  it.each([NaN, Infinity, -Infinity])("rejects nonfinite branding %s", (value) => {
    expect(identityStyle({ ...DEFAULT_IDENTITY, hue: value, chroma: value }))
      .toMatchObject({ "--brand-hue": "34", "--brand-chroma": "0.13" });
  });
  it("rejects CSS fragments and prototype keys at the runtime boundary", () => {
    for (const displayFont of ["serif; color:red", "__proto__", "constructor"]) {
      const input = { ...DEFAULT_IDENTITY, hue: "0; color:red", displayFont } as unknown as TenantIdentity;
      expect(identityStyle(input)).toMatchObject({ "--brand-hue": "34",
        "--font-display-family": "var(--font-instrument-serif), Georgia, serif" });
    }
  });
  it("keeps valid limits and produces a fallback monogram", () => {
    expect(identityStyle({ ...DEFAULT_IDENTITY, hue: 360, chroma: 0 })).toMatchObject({ "--brand-hue": "360", "--brand-chroma": "0" });
    expect(monogramOf("  restaurante ")).toBe("R");
    expect(monogramOf(" ")).toBe("C");
    expect(monogramOf(undefined)).toBe("C");
  });
});
