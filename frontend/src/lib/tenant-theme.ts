import type { CSSProperties } from "react";

/**
 * Identidad visual de un restaurante. Son las cuatro unicas decisiones que un
 * tenant puede tomar sobre la apariencia del sistema; todo lo demas —escala
 * tipografica, espaciado, jerarquia, radios— es del sistema, asi que ninguna
 * combinacion puede romper la lectura.
 *
 * Del color se guarda el *hue* y su chroma, no dos hex sueltos: el sistema
 * deriva las dos luminancias (L 0.52 en claro, L 0.72 en oscuro) desde
 * `globals.css`, y asi el par claro/oscuro nunca queda descoordinado.
 */
export interface TenantIdentity {
  /** Hue del acento en modo claro (0-360). */
  hue: number;
  chroma: number;
  /** Hue del acento en modo oscuro; suele ir unos grados mas calido. */
  hueDark: number;
  chromaDark: number;
  /** Preferencia del restaurante. Caja y cocina van en oscuro pase lo que pase. */
  mode: "light" | "dark";
  displayFont: DisplayFont;
}

/** Lista cerrada de tipografias de display permitidas. */
export type DisplayFont = "serif" | "grotesk" | "sans";

const DISPLAY_FONT_STACK: Record<DisplayFont, string> = {
  serif: "var(--font-instrument-serif), Georgia, serif",
  grotesk: "var(--font-space-grotesk), system-ui, sans-serif",
  sans: "var(--font-geist-sans), system-ui, sans-serif",
};

/** Terracota, claro, Instrument Serif: la identidad del sistema sin personalizar. */
export const DEFAULT_IDENTITY: TenantIdentity = {
  hue: 34,
  chroma: 0.13,
  hueDark: 45,
  chromaDark: 0.14,
  mode: "light",
  displayFont: "serif",
};

/**
 * Identidades conocidas por slug.
 *
 * El backend todavia no expone la configuracion de marca (`GET
 * /api/v1/auth/me` solo devuelve tenant, slug y nombre), asi que vive aqui
 * mientras tanto. Cuando el endpoint la incluya, basta con alimentar
 * {@link resolveIdentity} con esa respuesta en vez de con esta tabla: el resto
 * del frontend no cambia, porque solo consume las custom properties.
 */
const IDENTITY_BY_SLUG: Record<string, Partial<TenantIdentity>> = {
  "el-callejon": { hue: 34, hueDark: 45, displayFont: "serif", mode: "light" },
  "cafeteria-norte": { hue: 152, hueDark: 152, displayFont: "grotesk", mode: "light" },
  "mariscos-la-perla": { hue: 225, hueDark: 225, displayFont: "sans", mode: "dark" },
};

/** Identidad de un restaurante a partir de su slug, con la del sistema como base. */
export function resolveIdentity(slug: string | undefined): TenantIdentity {
  if (!slug) {
    return DEFAULT_IDENTITY;
  }
  return { ...DEFAULT_IDENTITY, ...IDENTITY_BY_SLUG[slug] };
}

/**
 * Custom properties que hay que colgar del contenedor de la app para que los
 * tokens de `globals.css` deriven el acento del restaurante. Se aplican como
 * `style` en vez de generar clases: el hue es un dato, no una variante.
 */
export function identityStyle(identity: TenantIdentity): CSSProperties {
  return {
    "--brand-hue": String(identity.hue),
    "--brand-chroma": String(identity.chroma),
    "--brand-hue-dark": String(identity.hueDark),
    "--brand-chroma-dark": String(identity.chromaDark),
    "--font-display-family": DISPLAY_FONT_STACK[identity.displayFont],
  } as CSSProperties;
}

/**
 * Monograma del restaurante: la primera letra de su nombre sobre el color de
 * marca. Hace de logo hasta que exista subida de imagen, que ocupara
 * exactamente la misma caja.
 */
export function monogramOf(name: string | undefined): string {
  return name?.trim().charAt(0).toUpperCase() || "C";
}
