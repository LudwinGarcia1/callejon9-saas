interface LegendItem {
  /** Variable CSS del color de la serie, ej. `--series-1`. El texto nunca usa este color: solo la muestra. */
  colorVar: string;
  label: string;
}

/**
 * Leyenda para graficas de 2+ series. El texto siempre viaja en un tono de
 * tinta (nunca en el color de la serie); el color solo vive en la muestra
 * junto a la etiqueta, para que la identidad nunca dependa solo del color.
 */
export function ChartLegend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="flex flex-wrap items-center gap-4 text-xs" style={{ color: "var(--ink-secondary)" }}>
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <span
            aria-hidden
            className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: `var(${item.colorVar})` }}
          />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
