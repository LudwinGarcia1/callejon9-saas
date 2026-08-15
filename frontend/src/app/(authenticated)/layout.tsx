"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname } from "next/navigation";

import { AppSidebar } from "@/components/layout/app-sidebar";
import { AppTopbar } from "@/components/layout/app-topbar";
import { ThemePreferenceContext } from "@/hooks/use-theme-preference";
import { useSession } from "@/hooks/use-session";
import { identityStyle, resolveIdentity } from "@/lib/tenant-theme";
import {
  readStoredPreference,
  resolveThemeMode,
  storePreference,
  type ThemePreference,
} from "@/lib/theme-mode";
import { cn } from "@/lib/utils";

/**
 * Pantallas que el sistema propone en oscuro: son estaciones fijas de turno
 * largo, con el operador de pie y a distancia. Es solo el default — la persona
 * puede fijar el modo que quiera desde el selector.
 */
const DARK_BY_DEFAULT_ROUTES = ["/cashier", "/kitchen"];

/**
 * Layout compartido por las pantallas que requieren sesion iniciada
 * (mesero, cocina, caja, administracion y plataforma).
 *
 * Aqui se inyecta la identidad del restaurante: hue y chroma de la marca y la
 * tipografia de display viajan como custom properties. Todo lo demas —escala,
 * espaciado, jerarquia, radios— es del sistema y no se puede personalizar.
 *
 * El modo claro/oscuro es la excepcion, y a proposito: el restaurante define el
 * default y el sistema oscurece caja y cocina, pero la ultima palabra la tiene
 * quien esta frente al monitor. Saltar de claro a oscuro al cambiar de seccion
 * se lee como una falla, no como una decision, y solo quien trabaja ahi sabe si
 * le conviene el oscuro.
 *
 * El modo se aplica en dos sitios: en el contenedor, para que la pantalla se
 * pinte bien de inmediato; y en `<html>`, para que lo que vive en un portal
 * —dialogos, listas de select, avisos— use los mismos tokens.
 */
export default function AuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user } = useSession();
  const pathname = usePathname();
  const identity = useMemo(() => resolveIdentity(user?.slug), [user?.slug]);
  const style = useMemo(() => identityStyle(identity), [identity]);

  // Arranca en `auto` y se sincroniza tras montar: leer localStorage durante el
  // render romperia la hidratacion, porque el servidor no lo puede ver.
  const [preference, setPreferenceState] = useState<ThemePreference>("auto");
  useEffect(() => {
    setPreferenceState(readStoredPreference());
  }, []);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    storePreference(next);
  }, []);

  const systemDefault: "light" | "dark" =
    DARK_BY_DEFAULT_ROUTES.some((route) => pathname.startsWith(route)) ||
    identity.mode === "dark"
      ? "dark"
      : "light";
  const resolved = resolveThemeMode(preference, systemDefault);
  const isDark = resolved === "dark";

  useEffect(() => {
    const root = document.documentElement;
    const properties = style as Record<string, string>;

    for (const [name, value] of Object.entries(properties)) {
      root.style.setProperty(name, value);
    }
    root.classList.toggle("dark", isDark);

    // Fuera de las pantallas autenticadas (login y alta) el sistema vuelve a su
    // identidad propia: la marca de la plataforma no es la del restaurante.
    return () => {
      for (const name of Object.keys(properties)) {
        root.style.removeProperty(name);
      }
      root.classList.remove("dark");
    };
  }, [style, isDark]);

  const themeValue = useMemo(
    () => ({ preference, setPreference, resolved, systemDefault }),
    [preference, setPreference, resolved, systemDefault],
  );

  return (
    <ThemePreferenceContext.Provider value={themeValue}>
      <div
        style={style}
        className={cn(
          "flex min-h-screen flex-col bg-background text-foreground xl:flex-row",
          isDark && "dark",
        )}
      >
        <AppSidebar />
        <AppTopbar />
        <main className="flex min-w-0 flex-1 flex-col">{children}</main>
      </div>
    </ThemePreferenceContext.Provider>
  );
}
