/**
 * Preferencia de modo de la persona, no del restaurante.
 *
 * El sistema Sala define un default por pantalla —el modo del restaurante, con
 * caja y cocina en oscuro porque son estaciones fijas de turno largo—, pero
 * quien esta frente al monitor puede sobreescribirlo. Un cajero de noche y un
 * administrador revisando reportes de dia no quieren lo mismo, y ninguno de los
 * dos deberia depender de una tabla de configuracion para conseguirlo.
 */
export type ThemePreference = "auto" | "light" | "dark";

/** Se guarda por navegador: es una preferencia de puesto de trabajo, no de cuenta. */
export const THEME_STORAGE_KEY = "callejon9:theme";

export const THEME_PREFERENCES: { value: ThemePreference; label: string }[] = [
  { value: "auto", label: "Auto" },
  { value: "light", label: "Claro" },
  { value: "dark", label: "Oscuro" },
];

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "auto" || value === "light" || value === "dark";
}

/** Lee la preferencia guardada. Devuelve `auto` si no hay ninguna o no se puede leer. */
export function readStoredPreference(): ThemePreference {
  if (typeof window === "undefined") {
    return "auto";
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : "auto";
  } catch {
    // localStorage puede estar bloqueado (modo privado, politica del navegador).
    // No es motivo para romper la pantalla: se cae al default del sistema.
    return "auto";
  }
}

export function storePreference(preference: ThemePreference): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Igual que arriba: la preferencia simplemente no sobrevive a la recarga.
  }
}

/**
 * Modo efectivo de una pantalla: la preferencia explicita gana; con `auto`
 * decide el sistema.
 */
export function resolveThemeMode(
  preference: ThemePreference,
  systemDefault: "light" | "dark",
): "light" | "dark" {
  return preference === "auto" ? systemDefault : preference;
}
