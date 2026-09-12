# Sistema visual real

- Fuente de tokens: `frontend/src/app/globals.css`.
- Colores: variables semánticas OKLCH para background, card, surface-alt, primary, brand, estados, bordes y charts; existen modos claro y `.dark`.
- Marca por tenant: hue/chroma claro/oscuro y familia display aplicados por `frontend/src/lib/tenant-theme.ts`.
- Tipografías: Geist sans/mono y display elegible entre Instrument Serif, Space Grotesk o Geist.
- Forma: radios deliberadamente compactos (3–8 px); jerarquía mediante superficie y borde, no sombras/curvas excesivas.
- Movimiento: transiciones de color/borde/sombra de 120 ms; evita movimiento de tamaño.
- Iconos: Lucide según `frontend/components.json`.

Caja y cocina pueden usar contenedores `.dark` aunque el tenant esté en modo claro. Usa clases/tokens existentes antes de agregar nuevos.
