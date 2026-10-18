"use client";

import { useEffect, useRef } from "react";
import type { ReactNode } from "react";

/**
 * Menú móvil sobre <details>: sin JavaScript abre y cierra igual. Con
 * JavaScript se cierra al tocar un enlace, al tocar fuera y con Escape, para
 * que no quede encima del contenido después de navegar.
 */
export function MenuDetails({ children, className }: { children: ReactNode; className?: string }) {
  const ref = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    const details = ref.current;
    if (!details) return;
    const close = () => {
      details.open = false;
    };
    const onClick = (event: MouseEvent) => {
      if (!details.open) return;
      const target = event.target as Node;
      if (!details.contains(target)) close();
      else if ((target as Element).closest?.("a")) close();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && details.open) {
        close();
        details.querySelector("summary")?.focus();
      }
    };
    document.addEventListener("click", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("click", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  return (
    <details ref={ref} className={className}>
      {children}
    </details>
  );
}
