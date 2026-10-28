# CAL-153 — UAT-08: Cocina y tiempo real

> **Estado: preparación; UAT no ejecutada.** Este archivo es un guion para la sesión, no es evidencia de aprobación. No se han tomado capturas ni registrado resultados reales.

Consulta las reglas de ejecución y evidencia en [`docs/pruebas/README.md`](../../README.md).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-153 — UAT · Cocina y tiempo real](https://linear.app/callejon19/issue/CAL-153/uat-cocina-y-tiempo-real) |
| Padre | [CAL-117 — UAT con usuarios por rol](https://linear.app/callejon19/issue/CAL-117/ejecutar-pruebas-de-aceptacion-uat-con-usuarios-por-rol) |
| Tipo | UAT |
| Escenario | UAT-08; participante: cocinero |
| Entorno | Azure; pendiente de confirmar despliegue vigente |
| Frontend | `https://restohub.azurewebsites.net` |
| API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Restaurante de prueba | `pruebas` |
| Commit probado / registro de despliegue | Pendiente; no ejecutar hasta registrar el SHA actual de API y frontend en Linear |
| Participante | P1 — cocinero externo al equipo; identificar solo por perfil al ejecutar |
| Estado de ejecución | ⛔ Bloqueado por dependencias; no ejecutado |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Valeria | Asignada en Linear |
| Ejecutor | Pendiente de asignar | Registrar al iniciar la sesión |
| Revisor | Pendiente de asignar | Debe ser distinto del ejecutor |
| Participante P1 | Pendiente de confirmar | Cocinero externo al equipo; no guardar nombre ni datos personales en el repositorio |

## 3. Fechas

No hay sesión UAT programada ni fechas reales registradas. Completar las fechas planeadas y reales durante la coordinación y ejecución; usar `America/Mexico_City` y hacerlas coincidir con el historial de Linear.

## 4. Alcance y criterios

**Objetivo.** Validar con un cocinero que los pedidos generados por UAT-06 y UAT-07 llegan al tablero, se distinguen por antigüedad y pueden llevarse a «Listo», y que el mesero observa el estado final sin recargar.

**Fuera de alcance.** El handshake WebSocket, una alerta sonora y una medición de latencia instantánea no aparecen en los criterios de CAL-153. El tablero actual consulta pedidos cada 5 s; registrar el tiempo observado sin describirlo como WebSocket o sonido.

| Criterio literal de CAL-153 | Caso |
|---|---|
| Los pedidos aparecen solos, sin recargar la página. | C01 |
| El cocinero distingue qué pedido llegó primero y cuánto lleva esperando. | C02 |
| Al marcar el pedido como listo, el mesero lo ve. | C03 |

## 5. Preparación y bloqueos

La ejecución en Azure está **bloqueada** hasta que se cumplan todos estos puntos:

- [ ] Resolver y volver a probar en Azure [CAL-169](https://linear.app/callejon19/issue/CAL-169), defecto crítico que puede impedir el inicio de sesión; [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) continúa en In Progress y bloquea la UAT padre.
- [ ] Ejecutar CAL-151 (UAT-06) y CAL-152 (UAT-07) y generar dos pedidos identificables para esta sesión. Al preparar este guion, ambas tareas siguen en In Progress.
- [ ] Confirmar que API y frontend desplegados corresponden a los SHA actuales. El último registro disponible apunta a API `ea9b5c1` y frontend `e6a6782` (21–22 oct); no usar esos SHA como evidencia de un despliegue actual.
- [ ] Confirmar `/actuator/health` en `UP`, acceso a `restohub.azurewebsites.net` y cuentas de prueba activas para los roles necesarios. No escribir contraseñas ni cookies en este archivo.
- [ ] Coordinar al participante P1 (cocinero externo al equipo), al ejecutor, al revisor y el dispositivo/pantalla del mesero.
- [x] CAL-140 figura Done en Linear desde el 27 oct. Su evidencia anterior registró el pedido en KDS sin recarga, pero también los defectos CAL-170 y CAL-172; revisar el despliegue actual antes de interpretar la UAT.

**Contexto técnico comprobado en el código.** El backend devuelve órdenes por `sentToKitchenAt` ascendente; la vista de cocina conserva ese orden y presenta el tiempo transcurrido. Cocina consulta `GET /api/v1/kitchen/orders` cada 5 s. Esta rama agrega el refresco de la orden web del mesero al mismo intervalo mientras la orden siga activa; deja de consultar al quedar pagada o cancelada. Esto requiere integración y despliegue antes de servir como prueba en Azure. La aplicación móvil debe observarse por separado durante C03.

**Automatización de apoyo.** La suite frontend comprueba que KDS incorpora pedidos en el siguiente sondeo y conserva el orden recibido, y que la orden web del mesero muestra «Lista» sin recargar. También se conservan las pruebas de `elapsedLabel` y el orden ascendente del backend. La ejecución local focalizada en esta rama fue de 3 archivos y 15 pruebas aprobadas con `pnpm exec vitest run src/test/order-mutations.test.tsx src/test/kitchen-view-refresh.test.tsx src/test/kitchen-timing.test.ts`. Estos checks son regresión del código; no sustituyen la sesión con P1.

**Defectos relacionados.** [CAL-170](https://linear.app/callejon19/issue/CAL-170/mostrar-al-mesero-los-cambios-de-cocina-sin-recargar-la-orden) registró que la vista web del mesero no se actualizaba. Esta rama añade la consulta periódica web; CAL-170 sigue requiriendo revisión y re-prueba. CAL-172 (cliente web sin conexión al canal `/ws`) y CAL-171 (origen Azure no permitido) no se consideran criterios de esta UAT: registrar un defecto si la ejecución revela que impiden alguno de los tres criterios.

## 6. Casos de prueba

Hasta resolver los bloqueos, los tres casos están ⛔ **Bloqueados** y no se han intentado.

### C01 — Los pedidos aparecen en cocina sin recargar

**Precondición.** UAT-06 y UAT-07 terminaron y dejaron un pedido distinto cada una. La sesión de cocina está abierta en `/kitchen` antes de generar ambos pedidos.

1. Mantener visible el tablero sin recargar ni cambiar de ruta.
2. Generar el pedido UAT-06 desde mesero web y el pedido UAT-07 desde mesero móvil.
3. Registrar folio y hora de envío de cada pedido; observar en qué momento aparece su tarjeta en cocina.
4. En DevTools → Network, confirmar una respuesta `200` de `GET /api/v1/kitchen/orders` que contenga ambos folios. Redactar cookies antes de guardar cualquier respuesta.

**Esperado.** Las dos tarjetas aparecen por actualización del tablero, sin acción manual de recarga. Registrar la demora real. Capturas previstas: `capturas/C01-P01-kds-antes.png`, `C01-P02-pedido-web-en-kds.png`, `C01-P03-pedido-movil-en-kds.png`.

### C02 — El tablero diferencia el orden de llegada y la espera

**Precondición.** Ambos pedidos están visibles en KDS y sus horas de envío se registraron en C01.

1. Comparar el folio enviado primero con la posición de las tarjetas.
2. Comparar la etiqueta de tiempo de espera de ambas tarjetas con la hora real de envío.

**Esperado.** El más antiguo aparece primero y muestra un tiempo de espera mayor. Registrar etiquetas y tiempos observados; no completar resultados por inferencia. Captura prevista: `capturas/C02-antiguedad-y-espera.png`.

### C03 — El mesero ve el pedido listo sin recargar

**Precondición.** Dejar abiertas la orden web y la vista del mesero móvil asociadas a los pedidos, antes de que cocina cambie sus estados.

1. El cocinero avanza los productos de cada orden por los estados disponibles hasta «Listo».
2. Registrar la hora en que el último producto pasa a «Listo».
3. Sin recargar las vistas del mesero, observar la orden web y la vista móvil; registrar el tiempo hasta que cada una muestra «Lista».
4. Si una vista no se actualiza, capturar ese resultado antes de recargar. Después, recargar solo para diagnosticar si el dato sí persistió.

**Esperado.** El mesero ve «Lista» sin recargar. El cambio web de esta rama refresca la orden en intervalos de 5 s cuando esté desplegado; el comportamiento móvil se debe comprobar en la sesión. Capturas previstas: `capturas/C03-P01-cocina-listo.png`, `C03-P02-mesero-web-listo.png`, `C03-P03-mesero-movil-listo.png`.

## 7. Resultado de preparación

| Caso | Estado | Resultado real | Defecto |
|---|---|---|---|
| C01 | ⛔ Bloqueado | No ejecutado; faltan CAL-151/CAL-152, despliegue vigente y liberar el bloqueo CAL-169 | — |
| C02 | ⛔ Bloqueado | No ejecutado; depende de los pedidos de C01 | — |
| C03 | ⛔ Bloqueado | No ejecutado; depende de C01, del despliegue de esta rama y de liberar CAL-169 | — |

No hay capturas, resultados observados ni aceptación del participante todavía. Al ejecutar, registrar fallos como issues de Linear con severidad y enlazarlos aquí.

## 8. Cierre pendiente

Después de la sesión: completar fechas reales, observaciones textuales del participante, métricas medidas, capturas con URL y reloj visibles, respuestas necesarias con cookies redactadas, defectos y conclusión. La declaración de autenticidad y la sección de revisión se llenan únicamente tras una ejecución real y revisión independiente.

## Sesi?n local guiada en PC y Android

El ensayo solicitado el 2026-10-28 se documenta por separado en [sesion-local-pc-android.md](sesion-local-pc-android.md). Usa un restaurante ficticio local y la web en Chrome Android; no se declara ejecutada la sesi?n formal de Azure ni la app m?vil. Resultados y capturas pendientes de confirmar.
