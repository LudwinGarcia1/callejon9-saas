"use client";

import { BrandShell } from "@/components/layout/brand-shell";
import { Button } from "@/components/ui/button";

/**
 * Frontera de error de la aplicacion. No muestra la traza: en piso no sirve de
 * nada y el detalle util ya viaja en el `digest` que Next registra del lado del
 * servidor. Ofrece reintentar, que es lo unico accionable aqui.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <BrandShell eyebrow="Algo salió mal" title="No pudimos cargar esta pantalla">
      <p className="mt-2 text-sm leading-[1.6] text-muted-foreground text-pretty">
        Vuelve a intentarlo. Si el problema sigue, avisa a quien administre el restaurante con
        el código de abajo.
      </p>
      {error.digest && (
        <p className="mt-3 font-mono text-xs text-muted-foreground">{error.digest}</p>
      )}
      <Button size="lg" className="mt-6" onClick={() => reset()}>
        Reintentar
      </Button>
    </BrandShell>
  );
}
