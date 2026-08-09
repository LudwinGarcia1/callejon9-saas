# Diseño — Sistema visual y pantallas de servicio

- **Fecha:** 2026-08-08
- **Estado:** diseñado, pendiente de plan de implementación
- **Punto de partida:** 174 pruebas verdes; inventario bloque 1 entregado y verificado en navegador; interfaz funcional pero sin identidad

## Contexto

El sistema hace lo que un restaurante necesita y lo hace bien, pero no lo parece. La interfaz es la que shadcn genera por defecto: gris sin color propio, densidad de escritorio, y —esto no es una elección de estilo sino un cable suelto— **la aplicación entera se renderiza en Times New Roman**.

`globals.css:10` declara `--font-sans: var(--font-sans)`, una autorreferencia circular. El layout expone la fuente como `--font-geist-sans` y nadie conecta ambas, así que la variable no resuelve y el navegador aplica su fuente por defecto. Medido en el navegador:

```
fuenteAplicadaAlCuerpo: "Times New Roman"
variableGeistSans:      "Geist", "Geist Fallback"   <- descargada en cada carga
variableFontSans:       (sin definir)               <- pero el tema apunta aquí
```

Buena parte de lo que hace que el sistema parezca inacabado no son decisiones de diseño pendientes: es esto.

Este documento es el primero de cinco bloques que llevan el proyecto de demostración a producto. Los otros cuatro —operaciones completas, cuentas serias, producto vendible y despliegue— se diseñarán cada uno en su propio spec, en ese orden.

### Decisiones tomadas

| Pregunta | Elección |
|---|---|
| ¿De quién es la marca? | **Del restaurante.** El producto es cromáticamente neutro y cada cliente aporta su color y su logo |
| ¿Hasta dónde llega la personalización? | Color, logo y **qué módulos ve** cada restaurante. Ni renombrar secciones ni configurar el contenido de las pantallas. Los tres se implementan fuera de este spec (ver *Fuera de alcance*) |
| ¿Tipografía por restaurante? | **No se expone.** Los tokens la contemplan; se abre cuando un cliente la pida |
| ¿En qué se usa? | **Tablet en piso, PC en caja, pantalla grande en cocina** |
| ¿Cómo se construye? | **Piloto primero.** Cimientos mínimos, una pantalla real, consolidar, replicar |
| ¿Qué entra en este spec? | Cimientos y las **tres pantallas de servicio**. El tema por restaurante va al siguiente |

La primera decisión ordena todas las demás: **si el color es del cliente, el producto no puede tener personalidad cromática propia** sin pelearse con él. Un restaurante con identidad naranja y otro con identidad vino tinto tienen que verse bien los dos, y eso solo ocurre si el sistema es neutro y el color aparece en pocos lugares deliberados.

---

## 1. Cimientos

Cuatro cambios en `globals.css` y `layout.tsx`. Ninguno toca una pantalla y los cuatro se notan en las diez.

### Conectar la fuente

Apuntar `--font-sans` a la variable que el layout sí define. Es el cambio de mayor efecto por línea escrita de todo el proyecto.

### Números tabulares

`font-variant-numeric: tabular-nums` en tablas y cifras. Hoy, en la columna de stock, `-84 kg` y `3 kg` no alinean porque cada dígito tiene un ancho distinto. En pantallas donde se comparan cantidades y dinero de un vistazo, se nota más que cualquier otra decisión tipográfica.

### Escala semántica de estado, separada de la marca

Hoy `--destructive` es el único token con color; el resto es gris con saturación cero. Se añade una escala propia —**crítico**, **atención**, **normal**, **listo**— que el restaurante no puede tocar.

Existe por una razón concreta: si un cliente eligiera rojo como color corporativo y los estados salieran de la misma escala, sus alertas dejarían de distinguirse del resto de la interfaz. `StatusBadge` ya insinúa esta separación al dar `destructive` solo a `NEGATIVE`; esto la formaliza.

### Tokens de densidad

Tres valores para espaciado base, tamaño de texto y altura mínima de objetivo interactivo:

| Contexto | Calibración | Por qué |
|---|---|---|
| PC en caja | Compacta | Sentado y con ratón: cabe más información y se agradece |
| Tablet en piso | Cómoda | Objetivos de 44 px o más; se usa de pie y con prisa |
| Pantalla en cocina | Expandida | Se lee a metro y medio y se toca con los dedos, a veces con guantes |

Se aplican con un atributo en la raíz de cada pantalla, no con clases repartidas por los componentes. Las tres salen de los mismos tokens: se calibra una vez, no se rediseña tres veces.

### Lo que no se toca

**Los componentes de `components/ui/`.** Son shadcn generado y su valor está en ser reemplazables. Todo lo anterior vive en tokens, así que los componentes lo heredan sin modificarse.

---

## 2. Piloto: pantalla de Cocina

Se elige como piloto por ser **el extremo del espectro** —distancia de lectura, objetivos táctiles grandes, modo oscuro, contraste máximo— y a la vez **la más simple de estructura**, un grid de tarjetas con sus renglones. Si el sistema aguanta ahí, las otras dos son calibraciones más suaves; y si hay que rehacerla tres veces, es barato.

### Lo visual

**Densidad expandida.** El texto de los platillos deja `text-sm` (14 px) y las notas dejan `text-xs`, que a metro y medio no se leen. Los botones pasan de `size="sm"` —unos 32 px de alto— a objetivos de 56 px o más.

**Oscuro fijo, sin interruptor.** Es la única pantalla encendida ocho horas seguidas, en ambiente caluroso y con mucho brillo alrededor. Un interruptor aquí solo añade una decisión que nadie quiere tomar en servicio. Los tokens de `.dark` ya existen y son utilizables.

**Jerarquía por distancia.** Hoy el folio es el título de la tarjeta, pero el folio le sirve a caja, no a cocina. Lo que la cocina necesita saber es de qué mesa es y qué falta por preparar. Se invierte: mesa prominente, folio secundario.

**Los renglones ya listos se atenúan**, para que lo pendiente salte a la vista sin leer cada línea.

### El semáforo de tiempo

Hoy la tarjeta dice *"enviada 16:44"*. A metro y medio, en plena comanda, nadie resta esa hora contra el reloj. Se muestra el **tiempo transcurrido** y la tarjeta cambia de color según él, con la escala semántica de la sección 1:

| Antigüedad | Señal |
|---|---|
| Menos de 10 min | Normal |
| 10 a 20 min | Atención |
| Más de 20 min | Crítico |

Es lo que convierte una lista de comandas en una pantalla de cocina: la información que hace falta es *qué lleva más tiempo esperando*, y el sistema ya la tiene sin mostrarla. No requiere ningún dato nuevo del backend — `sentToKitchenAt` ya viaja en la respuesta.

Los umbrales quedan fijos. Son una decisión de negocio que cambia por restaurante —en una taquería veinte minutos es un desastre y en un restaurante de cortes es normal—, así que se hacen configurables cuando un cliente lo pida, no antes.

---

## 3. Mesero y Caja

Se abordan después del piloto, con el sistema ya consolidado contra un caso real.

**Mesero** valida la calibración cómoda en tablet: es la pantalla más compleja del sistema —selector de productos, renglones, totales— y la que más se usa. Es donde el sistema de tokens se pone a prueba de verdad.

**Caja** va al final por ser la más parecida a lo que ya existe: escritorio, ratón, densidad compacta.

El sistema de tokens se escribe como documento cuando ya sobrevivió a dos pantallas opuestas, no antes. Un sistema de diseño que no se ha estrellado contra una pantalla real es una hipótesis.

---

## Orden de construcción

Cada paso deja el sistema utilizable, así que un corte a mitad de camino no rompe nada.

1. **Cimientos** — fuente, números tabulares, escala de estado, tokens de densidad
2. **Cocina** — piloto completo, con semáforo de tiempo
3. **Consolidar** — ajustar los tokens con lo aprendido en el piloto
4. **Mesero** — calibración cómoda en tablet
5. **Caja** — calibración compacta

El paso 1 se nota en las diez pantallas de inmediato, así que aunque el trabajo se detuviera ahí, el sistema ya habría dejado de renderizarse en Times New Roman.

---

## Verificación

Esta sección pesa más de lo habitual por una razón medida: en el bloque de inventario, de seis defectos encontrados, **los dos que llegaron al usuario fueron los de frontend**, y no los vieron el lint, ni el build, ni 174 pruebas. Un trabajo enteramente visual no puede apoyarse en herramientas que ya demostraron no servir para esto.

**Comprobar en navegador antes de commitear cada pantalla, no al final.** En inventario la verificación se dejó para el cierre, y por eso la key duplicada y el signo invertido viajaron ya commiteados.

**Medir, no mirar.** Es lo que funcionó: la fuente en Times New Roman no se detectó mirando la pantalla —se ve "rara" pero uno lo achaca al estilo—, se detectó leyendo `getComputedStyle`. Las cuatro promesas de este trabajo son medibles:

| Promesa | Comprobación |
|---|---|
| La fuente se aplica | `getComputedStyle(body).fontFamily` contiene Geist, no Times |
| Los números alinean | `font-variant-numeric` resuelto en las celdas numéricas |
| Se puede tocar con dedos | Ningún objetivo interactivo bajo su mínimo, leído con `getBoundingClientRect` |
| Se lee con contraste | Ratio calculado sobre el texto real, no estimado a ojo |

**Y una comprobación humana que ninguna medición sustituye:** ver la pantalla de cocina a la distancia real. Un ratio correcto no garantiza que un cocinero lea el pedido desde donde está parado.

### Sin arnés de pruebas de frontend

Introducir Vitest, Testing Library o Playwright sería la respuesta ortodoxa y no se adopta ahora: es un proyecto en sí mismo, y las pruebas que habrían atrapado los defectos de inventario son de renderizado visual, las más caras de montar y las más frágiles de mantener. Se formaliza lo que ya demostró funcionar y se deja el arnés para cuando exista una razón concreta que lo pida.

---

## Fuera de alcance

**El tema por restaurante.** Las columnas de color y logo, el endpoint que las sirve, la inyección de tokens al iniciar sesión y la pantalla donde el administrador lo configura. Va al bloque siguiente por una razón de orden: no se puede parametrizar lo que no está sistematizado. El tema consiste en sustituir el valor de unos tokens por los del cliente, y hasta que esos tokens existan y estén probados contra tres pantallas reales, no hay nada que sustituir.

**Los módulos activos por restaurante.** No son identidad visual sino una palanca de negocio —qué incluye cada plan—, así que viven junto al cobro y los límites, en el bloque de producto vendible.

**Las pantallas de administración, historial, analítica e inventario.** Heredan los cimientos de la sección 1, que es lo que arregla su problema más visible, pero su rediseño es una segunda pasada.

**Tipografía por restaurante, personalización de la estructura de las pantallas, y cualquier forma de constructor visual.**

**El despliegue**, que es el último de los cinco bloques.
