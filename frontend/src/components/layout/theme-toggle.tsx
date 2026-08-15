"use client";

import { MonitorIcon, MoonIcon, SunIcon } from "lucide-react";

import { useThemePreference } from "@/hooks/use-theme-preference";
import { THEME_PREFERENCES, type ThemePreference } from "@/lib/theme-mode";
import { cn } from "@/lib/utils";

const ICONS: Record<ThemePreference, typeof SunIcon> = {
  auto: MonitorIcon,
  light: SunIcon,
  dark: MoonIcon,
};

/**
 * Selector de modo. `Auto` deja decidir al sistema —el modo del restaurante,
 * con caja y cocina en oscuro—; las otras dos lo fijan para este navegador.
 *
 * `compact` deja solo los iconos, para el header horizontal de tablet donde no
 * hay ancho para las tres etiquetas.
 */
export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const { preference, setPreference, resolved, systemDefault } = useThemePreference();

  return (
    <div
      role="group"
      aria-label="Modo de color"
      className={cn("flex gap-1", compact ? "gap-1" : "w-full")}
    >
      {THEME_PREFERENCES.map((option) => {
        const Icon = ICONS[option.value];
        const isActive = preference === option.value;
        const hint =
          option.value === "auto"
            ? `Automático: esta pantalla va en ${systemDefault === "dark" ? "oscuro" : "claro"}`
            : `Fijar modo ${option.label.toLowerCase()}`;

        return (
          <button
            key={option.value}
            type="button"
            onClick={() => setPreference(option.value)}
            aria-pressed={isActive}
            title={hint}
            className={cn(
              "focus-sala inline-flex items-center justify-center gap-1.5 rounded-sm border",
              compact ? "size-10" : "h-[34px] flex-1 px-2",
              isActive
                ? "border-brand bg-brand-tint text-foreground"
                : "border-border text-muted-foreground hover:text-foreground",
            )}
          >
            <Icon className="size-3.5" />
            {!compact && (
              <span className="font-mono text-[10px] tracking-[0.08em] uppercase">
                {option.label}
              </span>
            )}
            <span className="sr-only">{hint}</span>
          </button>
        );
      })}
      {/* El modo con el que se esta pintando ahora, para lectores de pantalla. */}
      <span className="sr-only" aria-live="polite">
        Modo actual: {resolved === "dark" ? "oscuro" : "claro"}
      </span>
    </div>
  );
}
