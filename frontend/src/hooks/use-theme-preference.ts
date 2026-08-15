"use client";

import { createContext, useContext } from "react";

import type { ThemePreference } from "@/lib/theme-mode";

interface ThemePreferenceValue {
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
  /** Modo con el que se esta pintando ahora mismo, ya resuelto. */
  resolved: "light" | "dark";
  /** Lo que elegiria el sistema si la preferencia fuera `auto`. */
  systemDefault: "light" | "dark";
}

/**
 * El layout autenticado publica aqui la preferencia de modo para que el
 * selector pueda vivir donde tenga sentido —hoy el pie del sidebar y el header
 * de tablet— sin que el estado tenga que bajar por props a traves de toda la
 * navegacion.
 */
export const ThemePreferenceContext = createContext<ThemePreferenceValue | null>(null);

export function useThemePreference(): ThemePreferenceValue {
  const value = useContext(ThemePreferenceContext);
  if (!value) {
    throw new Error("useThemePreference debe usarse dentro del layout autenticado.");
  }
  return value;
}
