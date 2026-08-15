/**
 * Estado vacio de una grafica. Mismo patron que el resto del sistema: eyebrow
 * y una frase, sin ilustracion y sin centrar en un bloque alto que sugiera que
 * algo se esta cargando.
 */
export function ChartEmpty({
  message = "No hay ventas en el rango seleccionado.",
}: {
  message?: string;
}) {
  return (
    <div className="py-6">
      <p className="eyebrow">Sin datos</p>
      <p className="mt-1 text-sm text-muted-foreground">{message}</p>
    </div>
  );
}
