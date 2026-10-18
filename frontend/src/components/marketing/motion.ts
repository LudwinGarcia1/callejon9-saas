import type { CSSProperties } from "react";

/*
 * Variables de animación del sitio público. Van fuera de reveal.tsx porque
 * ese módulo es de cliente y estas funciones se llaman desde el servidor.
 */

/** Orden de un elemento dentro de su bloque, para escalonar la entrada. */
export function stagger(index: number): CSSProperties {
  return { "--i": index } as CSSProperties;
}

/** Retardo de una animación de entrada (`.mk-enter`, `.mk-toast`). */
export function delay(ms: number): CSSProperties {
  return { "--mk-delay": `${ms}ms` } as CSSProperties;
}
