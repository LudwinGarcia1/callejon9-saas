# Evidencia de prueba · CAL-151 — UAT · Pedidos del mesero

> **Estado: guion preparado; ejecución y resultados pendientes de confirmación.** Los casos están listos para llenarse **durante** la sesión; los resultados obtenidos se dejan vacíos a propósito. No se han recibido capturas ni resultados por caso, y una declaración general de ejecución no confirma por sí sola que los criterios hayan pasado. El participante no lee este documento: recibe solo [`guion-participante.md`](guion-participante.md).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-151](https://linear.app/callejon19/issue/CAL-151) — UAT · Pedidos del mesero |
| Historia o issue padre | [CAL-117](https://linear.app/callejon19/issue/CAL-117) — Ejecutar pruebas de aceptación (UAT) con usuarios por rol |
| Tipo de prueba | UAT (escenario UAT-06) |
| Módulo | Pedidos del mesero (web, `/waiter`) |
| Entorno previsto | Azure: frontend `https://restohub.azurewebsites.net`, API `https://app-callejon9-api-prod-3878c6.azurewebsites.net`, restaurante `pruebas` |
| Ensayo coordinado | Local: PC (`http://localhost:3000`) y Chrome en Android (`http://192.168.1.10:3000`; confirmar la IP vigente), restaurante ficticio `demo-15-datos`. **No demuestra Azure** |
| API desde el navegador | Proxy relativo `/api/v1/*` en los dos entornos |
| Último despliegue registrado | API y frontend en `68cf1f0` de `main`: comentarios «Despliegue» de [CAL-19](https://linear.app/callejon19/issue/CAL-19) y [CAL-16](https://linear.app/callejon19/issue/CAL-16) (2026-10-29 y 2026-10-30) |
| Versión realmente servida | Se confirma el día de la sesión, para frontend y backend; no se infiere del HEAD ni del nombre del JAR |
| Ejecución de CI | No aplica (UAT manual) |
| Navegador / dispositivo | Modelo, navegador y versiones se anotan el día de la sesión |
| Confirmación de ejecución | Pendiente |
| Versión de esta evidencia | v1 · 2026-10-30 · combina el guion detallado del 2026-10-28 con la versión publicada en `main` (PR #113) |

Formato: [guía de pruebas](../../README.md) y [plantilla](../../plantilla-evidencia.md). No guardar contraseñas, cookies ni datos personales.

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba (facilitador) | Por definir | Conduce la sesión, cronometra, toma capturas y anota; **no** opera la pantalla |
| Revisor independiente | Por definir | **Distinto del ejecutor y del participante.** Llena la sección 10 |
| Participante P1 | Mesero. Perfil por confirmar | Se identifica solo como P1; sin nombre ni datos personales en el repositorio |

**Participante.** `docs/pruebas/README.md` pide que el participante sea externo al equipo; se prefiere a alguien que haya trabajado de mesero. Si al final participa un integrante, se declara como desviación aquí y en la sección 8. En ese caso se aplican las mismas salvaguardas que en la sesión de caja de CAL-154:
- No implementó el módulo de pedidos ni ejecutó la SIT CAL-138.
- No lee este documento.
- Los criterios se preguntan con las palabras exactas del issue.
- Toda ayuda queda anotada.

## 3. Fechas

Todas las horas en `America/Mexico_City`. La fecha en que se confirma la ejecución no sustituye las fechas reales.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Issue padre (CAL-117) | 2026-10-20 | 2026-10-26 | 2026-10-21 | | Abierto |
| Esta tarea (CAL-151) | | | 2026-10-21 | | En Linear pasó a Done el 2026-10-21 03:46 y regresó a In Progress a las 04:06. El formato anterior se rechazó porque no tenía respaldo de una sesión real |
| Preparación del entorno y datos | 2026-10-28 | | 2026-10-28 | | Este guion |
| Ejecución de los casos | Por acordar; antes de la sesión de caja (UAT-09) | | | | |
| Revisión y re-prueba *(si hubo defectos)* | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un mesero, sin ayuda, abre una mesa, captura el pedido de cuatro comensales con notas, corrige o cancela antes de enviar a cocina y recibe folio y total con la mesa ocupada.

| Criterio del issue (literal) | Caso(s) que lo cubren |
|---|---|
| 1. Captura el pedido completo sin ayuda. | C01, C02 |
| 2. Recibe folio y total, y la mesa queda ocupada. | C01, C04 |
| 3. Si se equivoca antes de enviar a cocina, puede corregir o cancelar. | C03 (corregir), C05 (cancelar) |

**Equivalencia con la versión anterior de `main`.** La versión publicada en el PR #113 agrupaba los casos en tres; aquí se separan para que cada criterio tenga su propio resultado:

| Caso anterior | Casos actuales |
|---|---|
| C01 — Captura completa | C01 (abrir la mesa) y C02 (capturar con notas) |
| C02 — Corrección y cancelación | C03 (corregir) y C05 (cancelar) |
| C03 — Envío confirmado | C04 |

**Fuera de alcance.**
- Declarar la aprobación sin resultados por caso y sin la aceptación del participante.
- Atribuir a Azure un ensayo local, o sustituir la app móvil por Chrome en Android: la app móvil la cubre [CAL-152](https://linear.app/callejon19/issue/CAL-152).
- Las reglas de la API: tope de 99 por línea y que un mesero no toque la orden de otro. Las cubre la SIT [CAL-138](https://linear.app/callejon19/issue/CAL-138).
- Agregar productos a una mesa ya enviada a cocina, de [CAL-161](https://linear.app/callejon19/issue/CAL-161).
- El tablero de cocina, de UAT-08 ([CAL-153](https://linear.app/callejon19/issue/CAL-153)).
- El cobro, de UAT-09 ([CAL-154](https://linear.app/callejon19/issue/CAL-154)).

## 5. Preparación

**Dependencias**

- [x] SIT [CAL-138](https://linear.app/callejon19/issue/CAL-138) · Pedidos del mesero aprobada (Done en Linear).
- [ ] La versión servida el día de la sesión coincide con el último comentario «Despliegue» en Linear. Si hubo un despliegue posterior, se actualiza la sección 1 y se revisa de nuevo la sección 6.

**Hallazgos de la preparación (aún sin ejecutar).** Al leer el código para escribir los resultados esperados aparecieron dos diferencias con los criterios. Se identificaron en `e6a6782` y siguen igual en `main` (`64fa190`). No se dan por fallidas: el caso se ejecuta y se anota lo que ocurra. El PR [#111](https://github.com/LudwinGarcia1/callejon9-saas/pull/111) contiene cambios para los dos; antes de atribuirle un resultado hay que comprobar que esté integrado **y** desplegado.

1. **Notas por producto (criterio 1).**
   - La API sí acepta una nota de hasta 500 caracteres por línea (`AddOrderItemRequest.notes`) y la pantalla de la orden la muestra bajo el producto.
   - Pero la web del mesero no tiene dónde escribirla: el carrito (`product-picker.tsx`) solo maneja cantidades, y «Agregar a la orden» envía `productId` y `quantity` (`order-view.tsx`).
2. **Corregir después de «Agregar a la orden» (criterio 3).**
   - Las líneas del carrito se corrigen con «Quitar una unidad», «Agregar una unidad» y «Quitar del carrito».
   - Una vez agregadas a la orden, la API no tiene forma de quitar ni cambiar una línea: `OrderController` solo expone abrir, consultar, agregar, enviar a cocina y cancelar.
   - Lo único disponible es cancelar la orden completa.

**Precondiciones del entorno** *(se marcan el día de la sesión)*

- [ ] Backend encendido y `/actuator/health` en `UP` *(respuesta en `respuestas/health.txt`)*
- [ ] La pantalla `/login` del entorno elegido es accesible
- [ ] Versión servida confirmada y anotada en la sección 1
- [ ] La cuenta del mesero inicia sesión. El facilitador lo comprueba antes, sin fallar contraseñas.
- [ ] En «Mesas» hay al menos **dos mesas libres** con capacidad para 4 personas o más, una para C01 a C04 y otra para C05. Se anotan sus números en la tabla de datos.
- [ ] Los productos del guion del participante están activos en el catálogo. Sus precios del día se anotan en la tabla de datos y con ellos se calcula el total esperado.
- [ ] Ventana de navegador nueva, con la barra de direcciones y el reloj del sistema visibles
- [ ] Sesiones separadas por rol y solo datos ficticios

**Datos de prueba**

| Dato | Valor | Cómo se obtuvo |
|---|---|---|
| Entorno usado | Azure · Local | Se marca el día de la sesión |
| Restaurante | `pruebas` (Azure) o `demo-15-datos` (local) | Restaurantes de prueba de CAL-117 |
| Cuenta WAITER | `mesero@pruebas.com` en Azure; la cuenta de mesero de `demo-15-datos` en local | Cuentas de prueba de CAL-117 |
| Mesa para C01 a C04 | | Se anota el día de la sesión |
| Mesa para C05 | | Se anota el día de la sesión |
| Precios del día | Sopa de tortilla: · Guacamole con totopos: · Margarita: · Agua de horchata: · Flan napolitano: | Pantalla del catálogo, antes de empezar |
| Total esperado tras C02 | | 2 × sopa + 1 × guacamole + 2 × margarita + 2 × horchata + 1 × flan, con los precios del día |
| Total esperado tras C03 | | 1 × sopa + 2 × guacamole + 2 × margarita + 2 × horchata + 1 × flan |

> La contraseña la entrega el facilitador en persona. Nunca se escribe aquí, ni en capturas ni en el chat.

**Cuidado con el freno de intentos.** El backend bloquea 15 minutos una cuenta tras 5 contraseñas erróneas, y bloquea a un cliente tras 20 fallos. Desde el despliegue de [CAL-169](https://linear.app/callejon19/issue/CAL-169) (2026-10-29), restohub envía a la API la IP del navegador, así que cada cliente debería tener su propio contador; la re-prueba por restohub sigue pendiente. El participante no prueba contraseñas.

**Para la sesión de caja.** La orden de C04 **no se cancela**: queda en cocina para que UAT-09 ([CAL-154](https://linear.app/callejon19/issue/CAL-154)) la cobre. Se anota su folio en la sección 8.

## 6. Casos de prueba

Comportamiento esperado tomado del código (`e6a6782`, sin cambios en estos textos hasta `64fa190`):
- **Mesas** (`waiter-view.tsx`): cada mesa libre dice «Libre» y «Lista para sentar». Una ocupada dice «Ocupada», muestra la hora y el folio, la cuenta abierta y el botón «Ver comanda».
- **Abrir mesa** (`open-table-dialog.tsx`): diálogo con «Número de comensales» y botón «Abrir orden». Al confirmar aparece «Mesa N ocupada. Orden ORD-… abierta.» y se abre `/waiter/order/{id}`.
- **Orden** (`order-view.tsx`, `product-picker.tsx`):
  - Encabezado «ORD-… · abierta HH:MM» y «Mesa N · 4 comensales», con el estado «Nueva».
  - Los productos se juntan en el carrito y se confirman con «Agregar a la orden», que muestra «Productos agregados a la orden.».
  - «Enviar a cocina» muestra «Orden enviada a cocina.» y el recuadro «Ya está en cocina · Se envió a cocina a las HH:MM.».
  - «Cancelar orden» pide confirmación («Sí, cancelar orden»), muestra «Orden cancelada. La mesa quedó libre.» y regresa a «Mesas». El mesero solo puede cancelar mientras la orden está en «Nueva».

**Evidencia.** Los nombres de captura de cada caso son los previstos; **ninguno de esos archivos existe todavía**. Cada captura se guarda en `capturas/` solo después de tomarla en la sesión real.

### C01 — El mesero abre una mesa para cuatro comensales

| Campo | Valor |
|---|---|
| Criterio cubierto | 1, 2 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** Sesión iniciada como mesero, en «Mesas». Arranca el cronómetro al leer la situación 1 del guion.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Elegir una mesa libre y abrirla | 4 comensales | Aviso «Mesa N ocupada. Orden ORD-… abierta.»; pantalla de la orden con «Mesa N · 4 comensales» y estado «Nueva» | | `capturas/C01-P01-orden-abierta.png` |

### C02 — Captura el pedido completo con notas, sin ayuda

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Agregar al carrito lo que pidieron los cuatro comensales | Pedido de la situación 1 del guion | El carrito muestra 5 líneas con sus cantidades | | `capturas/C02-P01-carrito.png` |
| 2 | Escribir las notas de cada producto | «sin crema», «sin hielo», «sin sal en el borde» | Cada nota queda ligada a su producto. **Ver hallazgo 1 de la sección 5** | | `capturas/C02-P02-notas.png` |
| 3 | Confirmar el pedido | — | Aviso «Productos agregados a la orden.»; la orden lista cada producto con su cantidad, su nota debajo y el «Total de la orden» | | `capturas/C02-P03-orden-con-productos.png` |
| 4 | El facilitador anota si P1 pidió ayuda, en qué paso y qué se le dijo | — | Sin ayuda | | Sección 9 |

**Qué observar en C02.**
- Se anota, literal, qué buscó P1 para escribir las notas y qué hizo si no encontró dónde. No se le indica dónde están ni se le sugiere omitirlas.
- El carrito junta en una sola línea un producto repetido: las dos sopas quedan como «2×». Se anota si eso le impidió distinguir la sopa «sin crema» de la otra.

### C03 — Corrige un error antes de enviar a cocina

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** P1 dijo que terminó de capturar (C02) y la orden sigue en «Nueva».

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El facilitador lee la situación 2 del guion | El comensal 3 cambia una sopa de tortilla por un guacamole con totopos | — | | — |
| 2 | P1 corrige el pedido | — | La orden queda con 1 sopa de tortilla y 2 guacamoles; el total se recalcula. **Ver hallazgo 2 de la sección 5** | | `capturas/C03-P01-corregido.png` |

**Qué observar en C03.** Si P1 ya había tocado «Agregar a la orden», la sopa no se puede quitar desde la web. Se anota, literal, lo que intentó y cómo resolvió. Por ejemplo, si canceló la orden completa y volvió a capturar, o si dejó la sopa. Si P1 cancela la orden completa, C04 se hace con la nueva orden y el folio de C01 se anota como cancelado.

### C04 — Envía a cocina; recibe folio y total, y la mesa queda ocupada

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Enviar el pedido a cocina, una sola vez | — | Aviso «Orden enviada a cocina.»; estado «Enviada a cocina»; recuadro «Ya está en cocina · Se envió a cocina a las HH:MM.» | | `capturas/C04-P01-enviada.png` |
| 2 | Preguntar a P1: «¿cuál es el folio de la orden y cuánto es la cuenta?» | — | P1 lee el folio `ORD-…` y el «Total de la orden», igual al total esperado tras C03 (sección 5) | | Respuesta literal en la sección 9 |
| 3 | Regresar a «Mesas» | — | La mesa dice «Ocupada», con la hora y el mismo folio, la cuenta igual al total y el botón «Ver comanda» | | `capturas/C04-P02-mesa-ocupada.png` |

Detener el cronómetro aquí: es la duración de la sección 9.

### C05 — Cancela una orden antes de enviarla a cocina

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** Hay otra mesa libre (la de C05 en la sección 5).

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El facilitador lee la situación 3 del guion; P1 abre la mesa y agrega el producto | 2 comensales; 1 Agua de horchata | Orden en «Nueva» con el producto agregado | | `capturas/C05-P01-orden.png` |
| 2 | P1 cancela la orden | — | Pide confirmación «Cancelar orden ORD-…»; tras «Sí, cancelar orden», aviso «Orden cancelada. La mesa quedó libre.» y regresa a «Mesas» | | `capturas/C05-P02-confirmacion.png` |
| 3 | Observar la mesa | — | La mesa vuelve a «Libre» | | `capturas/C05-P03-mesa-libre.png` |

## 7. Defectos encontrados

No se han recibido descripciones de fallos; eso **no** equivale a ausencia de defectos. Cada fallo se registra con observación, severidad, issue en Linear y re-prueba.

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | | | | | |

## 8. Resumen y aceptación

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 5 | 0 | 0 | 0 | 5 |

El resultado por caso y la aceptación del participante se registran también en [registro-aceptacion.md](registro-aceptacion.md). El resultado global no se declara aprobado hasta completar los dos.

**Desviaciones y limitaciones.**
- La sesión no se ha realizado. Faltan el participante, el facilitador y el revisor.
- Un ensayo local (`demo-15-datos`) no demuestra el comportamiento en Azure; si solo se ejecuta en local, se declara aquí.
- Hay dos hallazgos de preparación (sección 5) que pueden hacer fallar los criterios 1 y 3.

**Folio que queda para UAT-09:** *se llena en la sesión.*

**Conclusión del ejecutor.** Sin ejecutar. Se llena solo después de la sesión real.

## 9. Anexo UAT — aceptación de usuario

| Campo | Valor |
|---|---|
| Perfil del participante | P1 — mesero; ¿conocía el sistema? |
| Entorno | Azure · Local |
| Modalidad | Presencial · Remota (con pantalla compartida) |
| Facilitador | |
| Duración medida | *Cronómetro: de la situación 1 al paso 3 de C04* |
| Ayuda brindada | |
| Comentarios del participante | *Palabras literales, tomadas al momento* |
| Respuesta a «¿cuál es el folio y cuánto es la cuenta?» (C04) | |
| ¿Acepta el escenario? | Sí · No · Con observaciones |
| Hoja de aceptación firmada | Se adjunta en Linear a CAL-151 y a CAL-117; no se sube al repositorio |

## 10. Revisión y cierre

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL, la fecha y hora del sistema y coinciden con los pasos
- [ ] Los mensajes y rutas coinciden con el código de la versión servida
- [ ] La versión probada corresponde al registro de despliegue, o el ensayo local está declarado como tal
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

Según [workflow/workflow.md](../../../../workflow/workflow.md), Done requiere evidencia completa revisada, aprobación independiente y PR integrado en `main`. Publicar esta evidencia no cierra el issue automáticamente.

## 11. Autenticidad y límites

Este documento contiene instrucciones y resultados esperados. A la fecha de esta versión (v1, 2026-10-30) no se ha recibido confirmación de ejecución de ningún caso. Se firma después de la ejecución real.

**Ejecutor:** — · **Fecha:** —
