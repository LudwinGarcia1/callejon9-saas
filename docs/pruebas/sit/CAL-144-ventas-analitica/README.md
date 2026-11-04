# Evidencia de prueba · CAL-144 — SIT · Ventas, historial y analítica

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID) y registraba una revisión que no ocurrió. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo, completa, sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-144](https://linear.app/callejon19/issue/CAL-144/sit-ventas-historial-y-analitica) — SIT · Ventas, historial y analítica |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Ventas, historial y analítica (web: `/history`, `/admin`, `/analytics`; backend `sale` y `analytics`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Brave 156.1.98.60 (Chromium 156.0.8078.48) automatizado con Playwright 1.56.1 en Garuda Linux, ventana de 1440 × 900; un contexto de navegador por rol |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v2 · 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear (reasignado el 2026-10-28) |
| Ejecutor de la prueba | Octavio Duarte | Script de Playwright de esta carpeta |
| Revisor | Braulio Hernández Mejía | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | Sigue abierta: 7 SIT se re-ejecutan |
| Esta tarea (CAL-144) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:11 | 2026-10-28 14:12 | Los cobros del día son los de la SIT de CAL-141 |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:20 | 2026-10-28 14:21 | — |
| Re-prueba de defectos | | | | | Defecto abierto (sección 7) |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que una venta cobrada en caja llega igual, por la API de Azure, al historial que ven caja y administrador y a la analítica del administrador.

**Fuera de alcance.** Rangos de varios días y la exportación de reportes.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Las ventas del día en caja y en el historial coinciden con los cobros hechos durante la SIT. | C01 |
| 2. La analítica del administrador refleja esos mismos totales en el periodo. | C02 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:20
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Cobros hechos durante la SIT el 2026-10-28: uno, en [CAL-141](../CAL-141-cobro-pagos/README.md) C01 — orden `ORD-261028201139`, ticket `TCK-261028201211`, subtotal $325.00 + propina $32.50 = **$357.50**, pagado con Efectivo $200.00 y Tarjeta $157.50. El script lo lee de `../CAL-141-cobro-pagos/respuestas/estado-ejecucion.json`

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Caja | `caja@pruebas.com` (CASHIER) | Alta de usuarios de prueba del 2026-10-16 |
| Administrador | `admin@pruebas.com` (ADMIN) | Alta de usuarios de prueba del 2026-10-16 |
| Periodo | 2026-10-28 a 2026-10-28 | Día de la ejecución, en `America/Mexico_City` |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-ventas.mjs`](playwright/sit-ventas.mjs) (`casos`, y `defecto` para la evidencia de la sección 7). Las lecturas por API se hicieron con la sesión de cada rol por el proxy `/api/v1/*`. Cada captura lleva una banda roja con la URL y la hora.

### C01 — Las ventas del día en caja y en el historial coinciden con los cobros de la SIT

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (`caja@pruebas.com`) y ADMIN (`admin@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:20` / `2026-10-28 14:21` |
| Estado | ✅ Aprobado |
| Defecto vinculado | [CAL-184](https://linear.app/callejon19/issue/CAL-184), observado fuera del criterio: «Cobrado hoy» de `/admin` |

**Precondición.** El único cobro del día en `pruebas` es el de CAL-141 ($357.50).

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Caja: abrir «Historial» (rango por defecto: hoy) | 2026-10-28 | 1 venta, $357.50 | 14:20:38 · «Ventas en el rango 1», «Total cobrado $357.50»; fila `ORD-261028201139`, Mixto, $357.50 | [`C01-P01`](capturas/C01-P01-historial-de-caja.png) |
| 2 | Pedir las ventas del día con la sesión de caja | `GET /api/v1/sales?from=2026-10-28&to=2026-10-28` | Igual | **200**, `summary: {count: 1, total: 357.5}`; `paymentMix`: CASH 200, CARD 157.5 | [`C01-ventas-del-dia-caja.txt`](respuestas/C01-ventas-del-dia-caja.txt) |
| 3 | Administrador: abrir «Historial» | 2026-10-28 | Lo mismo que caja | 14:20:40 · «Ventas en el rango 1», «Total cobrado $357.50» | [`C01-P02`](capturas/C01-P02-historial-del-administrador.png) |
| 4 | Pedir las ventas del día con la sesión del administrador | misma petición | Igual | **200**, `summary: {count: 1, total: 357.5}`; la venta es `ORD-261028201139`, `MIXED`, `total: 357.5`, `createdAt: 2026-10-28T20:12:11Z` | [`C01-ventas-del-dia-administrador.txt`](respuestas/C01-ventas-del-dia-administrador.txt) |
| 5 | Administrador: abrir «Administración» | — | (indicador relacionado) | 14:20:43 · «Cobrado hoy **$435.00**», distinto de lo cobrado. Ver [CAL-184](https://linear.app/callejon19/issue/CAL-184) | [`C01-P03`](capturas/C01-P03-administracion-cobrado-hoy.png) |

**Observaciones.** Caja, historial del administrador y API coinciden entre sí y con el cobro de la SIT: 1 venta, $357.50. El indicador «Cobrado hoy» de la pantalla de Administración no coincide; no es parte del criterio («caja e historial»), pero se registra como defecto.

### C02 — La analítica del administrador refleja los mismos totales

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (`admin@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:20` / `2026-10-28 14:20` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Igual que C01.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir «Analítica», rango del día y «Buscar» | Desde 28/10/2026, hasta 28/10/2026 | Ventas del día $357.50; Efectivo $200.00 y Tarjeta $157.50 | 14:20:45 · «Ventas por día»: 28/10/2026 $357.50. «Métodos de pago»: Efectivo $200.00, Tarjeta $157.50. «Pareto»: Guacamole con totopos 58.46 %, Margarita 41.54 % | [`C02-P01`](capturas/C02-P01-analitica-del-dia.png) |
| 2 | Pedir la analítica del día | `GET /api/v1/analytics?from=2026-10-28&to=2026-10-28` | Mismos totales | **200**, `salesByDay: [{day: 2026-10-28, total: 357.5, count: 1}]`; `paymentMix`: CASH 200 (55.94 %), CARD 157.5 (44.06 %); `pareto`: Guacamole 190, Margarita 135 | [`C02-analitica-del-dia.txt`](respuestas/C02-analitica-del-dia.txt) |

**Observaciones.** El Pareto se calcula sobre el ingreso por producto ($325.00, sin propina); ventas por día y métodos de pago incluyen la propina ($357.50). Es coherente con lo que cada gráfica dice medir.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-184](https://linear.app/callejon19/issue/CAL-184) | C01 paso 5 | Media | «Cobrado hoy» de `/admin` suma el `total` de **todas** las órdenes abiertas hoy, incluidas las canceladas, y sin propina: marcó $435.00 cuando lo cobrado fue $357.50 | Abierto | — |

**Detalle.** `admin-view.tsx` calcula «Cobrado hoy» como la suma de `order.total` de las órdenes de `GET /api/v1/orders` cuyo `openedAt` es hoy, sin filtrar por estado. Ese día eran ([`C01-ordenes-que-suma-cobrado-hoy.txt`](respuestas/C01-ordenes-que-suma-cobrado-hoy.txt)): `ORD-261028201148` CANCELED $110.00, `ORD-261028201139` PAID $325.00 y tres canceladas en $0.00. Suma $435.00. **Esperado:** lo cobrado, es decir, las ventas del día con propina: $357.50, como en el historial. **Pasos:** cancelar una orden con productos ya enviada a cocina y cobrar otra con propina el mismo día; abrir Administración.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **Un solo cobro.** El día de la ejecución hubo un cobro en `pruebas`, el de CAL-141. Con una sola venta la comparación es exacta pero no prueba agregaciones de varias ventas; el pago dividido sí ejercita la mezcla de métodos.
- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No toca ventas ni analítica.
- **Solo web.** La app móvil no tiene ventas; no aplica.

**Conclusión del ejecutor.** Los dos criterios se cumplen sobre Azure: caja, historial del administrador y analítica muestran el mismo cobro de la SIT, $357.50 en efectivo y tarjeta. Se encontró un defecto fuera del criterio: el indicador «Cobrado hoy» de Administración suma órdenes canceladas y omite la propina, registrado como [CAL-184](https://linear.app/callejon19/issue/CAL-184).

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | `GET /sales` y `GET /analytics` con las sesiones de caja y del administrador |
| App móvil ↔ API | — | No aplica |
| API ↔ PostgreSQL (RLS en la base real) | C01, C02 | La venta escrita por caja en CAL-141 es la que agregan historial y analítica del mismo restaurante |
| API ↔ tiempo real (`/ws`) | — | No aplica |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora (banda de la captura automatizada) y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** `2026-10-28`
