# Sistema visual: cimientos y pantalla de cocina — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar al sistema una base visual propia —tipografía que sí se aplica, números alineados, colores de estado separados de los de marca y tres densidades por contexto— y validarla rediseñando la pantalla de cocina, que es el caso más exigente.

**Architecture:** Todo el sistema vive en tokens CSS de `globals.css`. Ninguna tarea modifica `components/ui/`: son componentes generados por shadcn y su valor está en poder regenerarse. Las pantallas eligen su densidad con un atributo `data-density` en su contenedor raíz, y los componentes heredan el resultado a través de los tokens.

**Tech Stack:** Next.js App Router, Tailwind v4 (`@theme inline`), shadcn/ui, TypeScript.

## Global Constraints

- **Código en inglés, textos de interfaz en español.** Los textos de UI **sí llevan acentos** (`"En preparación"`); la regla de escribir sin acentos aplica solo a los mensajes de error de Java.
- **No se modifica ningún archivo de `frontend/src/components/ui/`.** Si una necesidad parece exigirlo, se resuelve con tokens o con `className` desde la pantalla.
- **No se introduce ningún arnés de pruebas de frontend.** La verificación es medida en navegador, según la sección *Verificación* del spec.
- **Verificar en navegador antes de cada commit, no al final.** En el bloque de inventario los dos únicos defectos que llegaron al usuario fueron de frontend, y viajaron ya commiteados.
- **Medir, no mirar.** Toda afirmación sobre tipografía, tamaño o contraste se comprueba leyendo `getComputedStyle` o `getBoundingClientRect`, nunca a ojo.
- El backend debe estar arriba para ver la pantalla de cocina con datos. Desde la raíz: `.\scripts\run-dev.ps1` en una terminal y `npm run dev` desde `frontend/` en otra.
- `npm run build` y `npm run dev` compiten por el directorio `.next`. Para verificar tipos sin detener el dev server, usar `npx tsc --noEmit`.

---

## Mapa de archivos

**Se crean:**

| Archivo | Responsabilidad |
|---|---|
| `frontend/src/lib/kitchen-timing.ts` | Antigüedad de una comanda y su etiqueta legible. Funciones puras, sin React |

**Se modifican:**

| Archivo | Cambio |
|---|---|
| `frontend/src/app/globals.css` | Enlace de la fuente, números tabulares, escala de estado, tokens de densidad |
| `frontend/src/app/(authenticated)/kitchen/kitchen-view.tsx` | Densidad expandida, oscuro fijo, jerarquía, semáforo, renglones listos atenuados |

**Se crea al final:**

| Archivo | Responsabilidad |
|---|---|
| `docs/sistema-visual.md` | Los tokens y cuándo usar cada uno, escrito ya validado contra una pantalla real |

---

## Orden de las tareas y por qué

La tarea 1 no toca ninguna pantalla y se nota en las diez, así que va primero y de forma aislada: si algo se rompe, se sabe exactamente qué lo rompió.

La tarea 2 somete esos tokens al caso más duro. La 3 documenta el sistema **después** de que sobrevivió al piloto, nunca antes: un sistema de diseño que no se ha estrellado contra una pantalla real es una hipótesis.

| Tarea | Entregable independiente |
|---|---|
| 1 | Cimientos: la aplicación deja de renderizarse en Times New Roman |
| 2 | Cocina rediseñada, con semáforo de tiempo |
| 3 | Los tokens ajustados con lo aprendido, y documentados |

---

### Task 1: Cimientos

**Files:**
- Modify: `frontend/src/app/globals.css`

**Interfaces:**
- Produces: tokens CSS `--state-critical`, `--state-critical-foreground`, `--state-warning`, `--state-warning-foreground`, `--state-ready`, `--state-ready-foreground`; y por densidad `--density-text-base`, `--density-text-lg`, `--density-text-sm`, `--density-gap`, `--control-height`, aplicados con `data-density="compact" | "comfortable" | "spacious"`.

- [ ] **Step 1: Medir el fallo actual en el navegador**

Con el frontend corriendo, abrir cualquier pantalla autenticada y ejecutar en la consola:

```js
({
  fuente: getComputedStyle(document.body).fontFamily,
  variableGeist: getComputedStyle(document.body).getPropertyValue('--font-geist-sans').trim(),
  variableUsada: getComputedStyle(document.documentElement).getPropertyValue('--font-sans').trim() || '(sin definir)',
})
```

Esperado, y es el rojo de esta tarea:

```
fuente:         "Times New Roman"
variableGeist:  "Geist", "Geist Fallback"
variableUsada:  (sin definir)
```

Anotar el resultado. Si la fuente **no** sale Times New Roman, detenerse: el diagnóstico del spec ya no aplica y hay que rehacerlo antes de tocar nada.

- [ ] **Step 2: Conectar la fuente**

En `frontend/src/app/globals.css`, dentro de `@theme inline`, sustituir las dos líneas que hoy dicen:

```css
  --font-sans: var(--font-sans);
  --font-mono: var(--font-geist-mono);
  --font-heading: var(--font-sans);
```

por:

```css
  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
  --font-heading: var(--font-geist-sans);
```

`--font-sans: var(--font-sans)` era una autorreferencia circular: la variable nunca resolvía y `html { @apply font-sans; }` caía a la fuente por defecto del navegador. `layout.tsx` ya expone `--font-geist-sans`, así que basta apuntar ahí.

- [ ] **Step 3: Verificar que la fuente ahora se aplica**

Recargar la página y repetir la medición del paso 1.

Esperado: `fuente` contiene `Geist`. Si sigue en Times New Roman, no continuar: el resto de la tarea se apoya en esto.

- [ ] **Step 4: Añadir números tabulares**

En `globals.css`, dentro del bloque `@layer base` existente, añadir una regla nueva después de la de `html`:

```css
  table {
    font-variant-numeric: tabular-nums;
  }
```

Con esto, en la columna de stock `-84 kg` y `3 kg` alinean sus dígitos. Se aplica a `table` y no a `body` a propósito: los números tabulares tienen todos el mismo ancho, lo que ayuda en columnas y estorba en texto corrido.

- [ ] **Step 5: Verificar la alineación**

Abrir `/inventory` y ejecutar:

```js
getComputedStyle(document.querySelector('tbody td')).fontVariantNumeric
```

Esperado: `"tabular-nums"`.

- [ ] **Step 6: Añadir la escala semántica de estado**

En `globals.css`, al final del bloque `:root` (después de `--sidebar-ring`), añadir:

```css
  /* Escala de estado: significado, no marca. El restaurante podra elegir su
     color corporativo, pero nunca estos: si un cliente con identidad roja
     pudiera teñir la interfaz, sus alertas dejarian de distinguirse. */
  --state-critical: oklch(0.55 0.2 27);
  --state-critical-foreground: oklch(0.98 0 0);
  --state-warning: oklch(0.75 0.15 75);
  --state-warning-foreground: oklch(0.22 0 0);
  --state-ready: oklch(0.6 0.13 150);
  --state-ready-foreground: oklch(0.98 0 0);
```

Y al final del bloque `.dark` (después de su `--sidebar-ring`), las variantes para fondo oscuro:

```css
  --state-critical: oklch(0.62 0.19 27);
  --state-critical-foreground: oklch(0.15 0 0);
  --state-warning: oklch(0.78 0.14 75);
  --state-warning-foreground: oklch(0.15 0 0);
  --state-ready: oklch(0.68 0.12 150);
  --state-ready-foreground: oklch(0.15 0 0);
```

**No se define un `--state-normal`.** El nivel normal usa los tokens neutros que ya existen (`--card`, `--border`): un estado que no requiere atención no debe pedirla. Esa es la razón de que el producto sea gris.

- [ ] **Step 7: Añadir los tokens de densidad**

En `globals.css`, después del bloque `.dark` y antes de `@layer base`, añadir:

```css
/*
 * Tres calibraciones para tres contextos fisicos reales:
 * compacta   -> PC en caja, sentado y con raton
 * comoda     -> tablet en el piso, de pie y con prisa (44px tactiles)
 * amplia     -> pantalla de cocina, leida a metro y medio y tocada con dedos
 *
 * Salen de los mismos tokens: se calibra una vez, no se redisena tres veces.
 * Se aplican con data-density en el contenedor raiz de cada pantalla.
 */
[data-density="compact"] {
  --density-text-sm: 0.75rem;
  --density-text-base: 0.875rem;
  --density-text-lg: 1rem;
  --density-gap: 0.5rem;
  --control-height: 2rem;
}

[data-density="comfortable"] {
  --density-text-sm: 0.875rem;
  --density-text-base: 1rem;
  --density-text-lg: 1.25rem;
  --density-gap: 0.75rem;
  --control-height: 2.75rem;
}

[data-density="spacious"] {
  --density-text-sm: 1rem;
  --density-text-base: 1.25rem;
  --density-text-lg: 1.75rem;
  --density-gap: 1rem;
  --control-height: 3.5rem;
}
```

`--control-height` es la altura mínima de cualquier cosa que se toque. Los tamaños de `button.tsx` van de `h-6` a `h-9` —24 a 36 px— y ninguno alcanza los 44 px táctiles, así que la altura viene de aquí y se aplica con `className`, sin tocar el componente.

- [ ] **Step 8: Verificar que compila y que nada se rompió**

```powershell
npm run lint
npx tsc --noEmit
```

Esperado: sin errores.

Después, abrir `/inventory`, `/admin` y `/waiter` en el navegador y confirmar que se ven con Geist y sin desajustes de espaciado. Los tokens de densidad todavía no los usa nadie, así que ninguna pantalla debe haber cambiado de tamaño.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/app/globals.css
git commit -m "fix(frontend): apply the font that was already loaded and add the design tokens"
```

---

### Task 2: Pantalla de cocina

**Files:**
- Create: `frontend/src/lib/kitchen-timing.ts`
- Modify: `frontend/src/app/(authenticated)/kitchen/kitchen-view.tsx`

**Interfaces:**
- Consumes: de la tarea 1, `data-density="spacious"`, `--control-height`, `--density-text-*`, `--state-*`.
- Produces: `OrderAge = "normal" | "warning" | "critical"`; `orderAge(sentAt: string | null, now: number): OrderAge`; `elapsedLabel(sentAt: string | null, now: number): string`.

- [ ] **Step 1: Crear las funciones de tiempo**

Crear `frontend/src/lib/kitchen-timing.ts`:

```ts
/** Umbrales del semaforo de cocina, en minutos desde que la comanda se envio. */
const WARNING_AFTER_MINUTES = 15;
const CRITICAL_AFTER_MINUTES = 25;

export type OrderAge = "normal" | "warning" | "critical";

/**
 * Que tan urgente es una comanda por el tiempo que lleva esperando.
 *
 * `now` entra como parametro y no se lee aqui con Date.now() para que el
 * resultado sea reproducible: el componente decide cada cuanto avanza el
 * reloj, y esta funcion siempre da la misma respuesta para las mismas
 * entradas.
 */
export function orderAge(sentAt: string | null, now: number): OrderAge {
  if (!sentAt) {
    return "normal";
  }

  const minutes = (now - new Date(sentAt).getTime()) / 60_000;

  if (minutes >= CRITICAL_AFTER_MINUTES) {
    return "critical";
  }
  if (minutes >= WARNING_AFTER_MINUTES) {
    return "warning";
  }
  return "normal";
}

/**
 * Cuanto lleva esperando, en palabras. A metro y medio de distancia nadie
 * resta una hora contra el reloj de la pared, que es lo que la pantalla
 * pedia hasta ahora al mostrar "enviada 16:44".
 */
export function elapsedLabel(sentAt: string | null, now: number): string {
  if (!sentAt) {
    return "";
  }

  const minutes = Math.floor((now - new Date(sentAt).getTime()) / 60_000);

  if (minutes < 1) {
    return "recién enviada";
  }
  return `hace ${minutes} min`;
}
```

- [ ] **Step 2: Añadir el reloj y los imports a la pantalla**

En `kitchen-view.tsx`, añadir a los imports de React y de librerías:

```tsx
import { useEffect, useState } from "react";
```

y junto a los demás imports de `@/lib`:

```tsx
import { elapsedLabel, orderAge, type OrderAge } from "@/lib/kitchen-timing";
```

Después de la constante `KITCHEN_POLL_INTERVAL_MS`, añadir:

```tsx
/** El reloj avanza solo para que el tiempo transcurrido no se congele entre
 * refetches. Treinta segundos basta: los umbrales estan en minutos. */
const CLOCK_TICK_MS = 30_000;
```

Dentro de `KitchenView`, después de `const queryClient = useQueryClient();`:

```tsx
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
    return () => clearInterval(id);
  }, []);
```

- [ ] **Step 3: Añadir el mapa de estilos del semáforo**

En `kitchen-view.tsx`, antes de la función `KitchenView`, añadir:

```tsx
/**
 * El nivel normal no lleva color: un estado que no requiere atencion no debe
 * pedirla. Solo destacan las comandas que llevan esperando demasiado.
 */
const AGE_CARD_STYLES: Record<OrderAge, string> = {
  normal: "",
  warning: "border-2 border-[var(--state-warning)]",
  critical: "border-2 border-[var(--state-critical)] bg-[var(--state-critical)]/10",
};

const AGE_TEXT_STYLES: Record<OrderAge, string> = {
  normal: "text-muted-foreground",
  warning: "text-[var(--state-warning)] font-medium",
  critical: "text-[var(--state-critical)] font-semibold",
};
```

- [ ] **Step 4: Aplicar densidad amplia y oscuro fijo**

En `kitchen-view.tsx`, sustituir la línea de apertura del `return`:

```tsx
    <div className="flex flex-col gap-6">
```

por:

```tsx
    <div className="dark flex flex-col gap-6 bg-background text-foreground" data-density="spacious">
```

`dark` va fijo, sin interruptor: es la única pantalla encendida ocho horas seguidas, en un ambiente caluroso y con mucho brillo alrededor, y una decisión más en pleno servicio no ayuda a nadie.

**Efecto conocido a resolver en la tarea 3:** esto oscurece el contenido pero **no la barra lateral**, que vive en el layout y quedará clara al costado. Una franja blanca brillante junto a una pantalla pensada para mirarse a metro y medio molesta. Verificarlo en el paso 11 y decidir entonces entre dos salidas: llevar el oscuro al layout cuando la ruta sea `/kitchen`, o dejar la barra clara si en la práctica no estorba. No adelantar la decisión sin haberlo visto.

- [ ] **Step 5: Rehacer la cabecera de cada tarjeta**

Sustituir el bloque `<CardHeader>` completo, que hoy es:

```tsx
                <CardHeader className="flex flex-row items-start justify-between gap-2">
                  <div>
                    <CardTitle>Orden {order.folio}</CardTitle>
                    <p className="text-sm text-muted-foreground">
                      {tableLabel(order.tableId)}
                      {order.sentToKitchenAt
                        ? ` · enviada ${formatShortTime(order.sentToKitchenAt)}`
                        : ""}
                    </p>
                  </div>
                  <StatusBadge kind="order" status={order.status} />
                </CardHeader>
```

por:

```tsx
                <CardHeader className="flex flex-row items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-[length:var(--density-text-lg)]">
                      {tableLabel(order.tableId)}
                    </CardTitle>
                    <p className={cn("text-[length:var(--density-text-base)]", AGE_TEXT_STYLES[age])}>
                      {elapsedLabel(order.sentToKitchenAt, now)}
                    </p>
                    <p className="text-[length:var(--density-text-sm)] text-muted-foreground">
                      Orden {order.folio}
                    </p>
                  </div>
                  <StatusBadge kind="order" status={order.status} />
                </CardHeader>
```

La mesa pasa a ser el título y el folio baja a tercera línea: el folio le sirve a caja, no a cocina. Lo que la cocina necesita saber es de qué mesa es y cuánto lleva esperando.

Esto requiere `age` en el alcance del `map` y el helper `cn`. En el mismo `map`, justo después de `{ordersQuery.data?.map((order) => (`, cambiar la forma de flecha implícita por una con cuerpo:

```tsx
            {ordersQuery.data?.map((order) => {
              const age = orderAge(order.sentToKitchenAt, now);
              return (
```

y cerrar al final del `map`, sustituyendo `))}` por:

```tsx
              );
            })}
```

Añadir a los imports:

```tsx
import { cn } from "@/lib/utils";
```

Y quitar `formatShortTime` del import de `@/lib/format` si ya no se usa en el archivo.

- [ ] **Step 6: Aplicar el semáforo a la tarjeta y agrandar el botón**

Sustituir la apertura de la tarjeta:

```tsx
              <Card key={order.id}>
```

por:

```tsx
              <Card key={order.id} className={AGE_CARD_STYLES[age]}>
```

Sustituir el bloque del botón, que hoy es:

```tsx
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isPending}
                            onClick={() =>
                              advanceItemMutation.mutate({ itemId: item.id, status: next })
                            }
                          >
                            {isPending
                              ? "Actualizando..."
                              : `Marcar como ${KITCHEN_STATUS_LABELS[next]}`}
                          </Button>
```

por:

```tsx
                          <Button
                            variant="outline"
                            disabled={isPending}
                            className="h-[var(--control-height)] w-full text-[length:var(--density-text-base)]"
                            onClick={() =>
                              advanceItemMutation.mutate({ itemId: item.id, status: next })
                            }
                          >
                            {isPending
                              ? "Actualizando..."
                              : `Marcar como ${KITCHEN_STATUS_LABELS[next]}`}
                          </Button>
```

Se quita `size="sm"` (28 px de alto) y la altura pasa a salir del token: 56 px en densidad amplia. `w-full` porque un objetivo ancho es más fácil de acertar con el dedo que uno estrecho.

- [ ] **Step 7: Agrandar los renglones y atenuar los ya listos**

Sustituir el bloque del renglón, que hoy es:

```tsx
                        <div className="flex items-center justify-between gap-2">
                          <div>
                            <p className="text-sm font-medium">
                              {item.quantity} x {item.productName}
                            </p>
                            {item.notes && (
                              <p className="text-xs text-muted-foreground">{item.notes}</p>
                            )}
                          </div>
                          <StatusBadge kind="kitchen" status={item.kitchenStatus} />
                        </div>
```

por:

```tsx
                        <div
                          className={cn(
                            "flex items-center justify-between gap-2",
                            isReadyOrBeyond(item.kitchenStatus) && "opacity-50",
                          )}
                        >
                          <div>
                            <p className="text-[length:var(--density-text-base)] font-medium">
                              {item.quantity} x {item.productName}
                            </p>
                            {item.notes && (
                              <p className="text-[length:var(--density-text-sm)] text-muted-foreground">
                                {item.notes}
                              </p>
                            )}
                          </div>
                          <StatusBadge kind="kitchen" status={item.kitchenStatus} />
                        </div>
```

Atenuar lo ya listo hace que lo pendiente salte a la vista sin leer cada línea. `isReadyOrBeyond` ya existe en el archivo.

- [ ] **Step 8: Verificar que compila**

```powershell
npm run lint
npx tsc --noEmit
```

Esperado: sin errores.

- [ ] **Step 9: Verificar la pantalla en el navegador, midiendo**

Con backend y frontend arriba, entrar como `KITCHEN` (`callejon-nueve` / `cocina@callejon9.com`), abrir `/kitchen` y ejecutar:

```js
const tarjeta = document.querySelector('[data-slot="card"]');
const boton = document.querySelector('[data-slot="card"] button');
({
  fuente: getComputedStyle(document.body).fontFamily,
  fondoOscuro: getComputedStyle(document.querySelector('[data-density]')).backgroundColor,
  alturaBoton: boton ? boton.getBoundingClientRect().height : '(sin botones)',
  tamanoPlatillo: tarjeta ? getComputedStyle(tarjeta.querySelector('p')).fontSize : '(sin tarjetas)',
})
```

Esperado: la fuente contiene `Geist`; el fondo es oscuro; **la altura del botón es 56 px**; el texto del platillo mide 20 px.

Si no hay comandas en el tablero, abrir una desde `/waiter` con otra sesión y enviarla a cocina.

- [ ] **Step 10: Verificar el semáforo con datos reales**

Una comanda recién enviada debe verse sin color y decir *"recién enviada"*. Para comprobar los otros dos niveles sin esperar 25 minutos, adelantar la fecha de envío de una comanda directamente en la base:

```powershell
$env:PGPASSWORD = 'app_dev_pwd'
& 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -h localhost -p 5433 -U callejon9_app -d callejon9 -c "SELECT set_config('app.tenant_id','0722e087-2417-4d64-9326-d5d458b8c41d',false); UPDATE orders SET sent_to_kitchen_at = now() - interval '18 minutes' WHERE status = 'SENT';"
```

Esperado tras el siguiente refetch (5 segundos): la tarjeta muestra borde ámbar y *"hace 18 min"*. Repetir con `'30 minutes'` para confirmar el nivel crítico, que además tiñe el fondo.

- [ ] **Step 11: Comprobación humana**

Alejarse metro y medio de la pantalla y confirmar que se lee de qué mesa es cada comanda y qué falta por preparar. Un ratio de contraste correcto no garantiza que un cocinero lea el pedido desde donde está parado, y esta es la única comprobación que ninguna medición sustituye.

Aprovechar para mirar la barra lateral clara junto al contenido oscuro (ver el paso 4) y anotar si estorba. Esa observación es la entrada de la tarea 3.

- [ ] **Step 12: Commit**

```bash
git add frontend/src/lib/kitchen-timing.ts frontend/src/app/\(authenticated\)/kitchen/kitchen-view.tsx
git commit -m "feat(frontend): redesign the kitchen board for distance reading and add the time signal"
```

---

### Task 3: Consolidar y documentar

**Files:**
- Modify: `frontend/src/app/globals.css` (solo si el piloto reveló algún ajuste)
- Create: `docs/sistema-visual.md`

**Interfaces:**
- Consumes: todo lo de las tareas 1 y 2.

- [ ] **Step 1: Ajustar los tokens con lo aprendido**

Revisar lo medido y observado en los pasos 9 a 11 de la tarea 2, y decidir si algún valor de `[data-density="spacious"]` quedó corto o excesivo. Los candidatos habituales:

- `--density-text-base` en 20 px puede quedarse corto si la pantalla está a más de dos metros.
- `--control-height` en 56 px puede resultar excesivo si caben pocas tarjetas por pantalla.

**Y resolver la barra lateral clara** anotada en el paso 11. Si estorbaba, la salida es aplicar `dark` en el layout autenticado cuando la ruta sea `/kitchen`, en `app/(authenticated)/layout.tsx`, para que la barra acompañe al contenido. Si no estorbaba, dejarlo y anotar la decisión en el documento del paso siguiente.

Si algo cambia, cambiarlo aquí y volver a medir. Si nada cambia, dejarlo y anotarlo en el documento del paso siguiente: que los valores iniciales aguantaran el caso más duro es información útil.

- [ ] **Step 2: Escribir el documento del sistema**

Crear `docs/sistema-visual.md`:

```markdown
# Sistema visual

Los tokens viven en `frontend/src/app/globals.css`. Este documento dice cuándo
usar cada uno. Se escribió después de aplicarlos a la pantalla de cocina, no
antes: un sistema de diseño que no se ha estrellado contra una pantalla real
es una hipótesis.

## Densidad

Cada pantalla declara su contexto físico con `data-density` en su contenedor
raíz, y los componentes de dentro leen los tokens resultantes.

| Valor | Dónde | Altura de control |
|---|---|---|
| `compact` | Caja: PC, sentado, con ratón | 32 px |
| `comfortable` | Piso: tablet, de pie, con prisa | 44 px |
| `spacious` | Cocina: pantalla a metro y medio, dedos | 56 px |

Los tamaños de `button.tsx` van de 24 a 36 px y ninguno alcanza los 44 px
táctiles, así que la altura se aplica con `className="h-[var(--control-height)]"`
desde la pantalla. **Nunca se modifica `components/ui/`**: son componentes
generados y su valor está en poder regenerarse.

## Color

El producto es cromáticamente neutro, y no por gusto: el color de marca es del
restaurante. Uno con identidad naranja y otro con identidad vino tinto tienen
que verse bien los dos, y eso solo ocurre si el sistema es gris y el color
aparece en pocos lugares deliberados.

La escala de estado es aparte y el restaurante no la toca:

| Token | Significado |
|---|---|
| `--state-critical` | Requiere acción ahora: comanda muy demorada, stock negativo |
| `--state-warning` | Requiere atención: comanda demorada, stock bajo mínimo |
| `--state-ready` | Terminado correctamente |

No existe `--state-normal`: lo normal usa los tokens neutros. Un estado que no
requiere atención no debe pedirla.

Si esta escala saliera del color del cliente, un restaurante con identidad roja
se quedaría sin poder distinguir sus alertas del resto de la interfaz.

## Tipografía

Geist, cargada en `layout.tsx` y expuesta como `--font-geist-sans`.

Las tablas llevan `font-variant-numeric: tabular-nums` para que los dígitos
alineen en columna. No se aplica a todo el documento a propósito: los números
tabulares ayudan en columnas y estorban en texto corrido.
```

- [ ] **Step 3: Verificar que todo sigue compilando**

```powershell
npm run lint
npx tsc --noEmit
```

Esperado: sin errores.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/globals.css docs/sistema-visual.md
git commit -m "docs: write the design system after validating it against the kitchen board"
```

---

## Lo que sigue, y por qué no está aquí

Este plan termina con el sistema validado contra una pantalla. **Mesero y Caja se planifican después**, cuando la tarea 3 haya ajustado los tokens con lo aprendido.

Escribirlos ahora sería redactar código contra valores que todavía no se han estrellado con ninguna pantalla real, que es justamente el error que el enfoque elegido evita. La tarea 2 puede revelar, por ejemplo, que la densidad amplia necesita otra escala tipográfica, y esa lección tiene que llegar a Mesero antes de que se escriba, no después.

## Verificación al cerrar el plan

- `npm run lint` y `npx tsc --noEmit` sin errores.
- `npm run build` limpio, deteniendo antes el dev server para que no compitan por `.next`.
- Las diez pantallas se ven con Geist, comprobado midiendo y no a ojo.
- La pantalla de cocina cumple sus cuatro medidas: fuente, fondo oscuro, botón de 56 px y texto de 20 px.
- El semáforo se ha visto en sus tres niveles con datos reales.
- `git status --short` sin cambios pendientes.
