# Evidencia de prueba · CAL-138 — SIT · Pedidos del mesero

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-138](https://linear.app/callejon19/issue/CAL-138/sit-pedidos-del-mesero) — SIT · Pedidos del mesero |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Pedidos del mesero (web, `/waiter`; backend `order`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`), el `main` actual al ejecutar |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-21 00:56 |
| Commit del frontend | *Sin registro de despliegue* (ver sección 8) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Google Chrome 155 automatizado con Playwright 1.56.1 en Windows 11, ventana de 1440 × 900 |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Script de Playwright de esta carpeta |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | |
| Esta tarea (CAL-138) | 2026-10-20 | 2026-10-21 | 2026-10-21 12:41 | | |
| Preparación del entorno y datos | 2026-10-21 | 2026-10-21 | 2026-10-21 12:42 | 2026-10-21 12:49 | La API estaba apagada; se encendió (`UP` a las 12:48) |
| Ejecución de los casos | 2026-10-21 | 2026-10-21 | 2026-10-21 12:49 | 2026-10-21 12:51 | — |
| Re-prueba de defectos | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el flujo de pedidos del mesero en la web publicada funciona contra la API de Azure, y que las reglas de seguridad de las órdenes están activas en el backend desplegado: un mesero no puede operar la orden de otro (CAL-111) y una línea no admite más de 99 unidades (CAL-112).

**Fuera de alcance.** La app móvil (CAL-139), el cobro (CAL-141) y la cancelación por el administrador de órdenes ya enviadas (CAL-113).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Abrir orden, agregar productos y enviar a cocina: folio y total calculados por la API | C01 |
| 2. Un segundo mesero intenta modificar o cancelar la orden del primero: 403 (CAL-111) | C02 |
| 3. Cantidad de 100 en una línea: 400 (tope de CAL-112) | C03 |
| Criterio de entrada: el backend desplegado corresponde al `main` actual, con CAL-111 a 113 | Sección 1 y 5 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-21 12:48
- [x] Commit desplegado verificado con el registro de despliegue: `ea9b5c1`, igual a `origin/main` al iniciar
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Segundo mesero dado de alta por el administrador desde la web: `POST /api/v1/users` → **201**, 12:49 — [`PREP-01`](capturas/PREP-01-alta-segundo-mesero.png), [`PREP-02`](capturas/PREP-02-usuarios-del-restaurante.png), [`PREP-01-alta-segundo-mesero.txt`](respuestas/PREP-01-alta-segundo-mesero.txt)
- [x] Dos mesas libres para C01 y para la orden de C02 y C03: la 3 y la 4

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Mesero 1 | `mesero@pruebas.com` (WAITER) | Alta de usuarios de prueba del 2026-10-16 |
| Mesero 2 | `mesero2@pruebas.com` (WAITER), «Mesero de prueba 2» | Alta desde la web del administrador en esta preparación |
| Administrador | `admin@pruebas.com` (ADMIN) | Alta de usuarios de prueba del 2026-10-16 |
| Contraseña | La de las cuentas de prueba, también para el mesero 2 | Vive fuera del repositorio |

## 6. Casos de prueba

Las peticiones que hizo la interfaz se registraron desde el navegador con su código y cuerpo literales; las que no tienen botón en la web (C02 paso 5 y C03 paso 2) se enviaron desde la misma sesión por el proxy `/api/v1/*`.

### C01 — El mesero abre una orden, agrega productos y la envía a cocina

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero@pruebas.com`) |
| Inicio / fin de ejecución | 2026-10-21 12:49 / 2026-10-21 12:50 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesión web del mesero 1; mesas 3 y 4 libres.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir «Mesas» | — | Mesas con su estado | 12:49:52 · Mesas libres: 3, 4, 5 y 12 | [`C01-P01`](capturas/C01-P01-mesas-antes.png) |
| 2 | Tocar la mesa 3 e indicar comensales | 2 comensales | Diálogo «Abrir mesa 3» | 12:49:53 · Diálogo con «Número de comensales» = 2 | [`C01-P02`](capturas/C01-P02-abrir-mesa.png) |
| 3 | Confirmar y agregar productos al carrito | 2× Guacamole con totopos ($95), 1× Margarita ($135) | La API abre la orden en «Nueva»; el carrito muestra el subtotal | 12:49:54 · `POST /api/v1/orders` → **201**, folio **ORD-261021184954**, `status: NEW`, `total: 0`. Carrito con 2 líneas | [`C01-P03`](capturas/C01-P03-carrito.png), [`C01-abrir-agregar-enviar.txt`](respuestas/C01-abrir-agregar-enviar.txt) |
| 4 | Tocar «Agregar a la orden» | — | La API calcula el total con sus precios | 12:50:00 · `POST …/items` → **200**, `total: 325` (2 × 95 + 135) | [`C01-P04`](capturas/C01-P04-productos-agregados.png) |
| 5 | Tocar «Enviar a cocina» | — | Orden en «Enviada a cocina» con el mismo folio y total | 12:50:01 · `POST …/send-to-kitchen` → **200**, `status: SENT`, `total: 325`. Pantalla: «Enviada a cocina», «Total de la orden $325.00», aviso «Orden enviada a cocina.» | [`C01-P05`](capturas/C01-P05-enviada-a-cocina.png) |

### C02 — Un segundo mesero intenta operar la orden del primero

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (`mesero2@pruebas.com`) sobre una orden de `mesero@pruebas.com` |
| Inicio / fin de ejecución | 2026-10-21 12:50 / 2026-10-21 12:50 |
| Estado | ✅ Aprobado |
| Defecto vinculado | [CAL-166](https://linear.app/callejon19/issue/CAL-166) (mensaje y formato del 403; la regla se cumple) |

**Precondición.** El mesero 1 abre la mesa 4 con 1× Sopa de tortilla ($85) y la deja en «Nueva»: orden **ORD-261021185004**. Se usa una orden «Nueva» para que el 403 de la cancelación venga de CAL-111 y no de CAL-113, que solo deja cancelar órdenes enviadas al administrador.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El mesero 1 deja su orden en «Nueva» | Mesa 4, 1× Sopa de tortilla | Orden «Nueva», $85.00 | 12:50:10 · ORD-261021185004, «Nueva», total $85.00 | [`C02-P01`](capturas/C02-P01-orden-del-mesero-1.png) |
| 2 | El mesero 2 abre esa orden por su URL | `/waiter/order/{id}` | Puede verla (ver no está restringido) | 12:50:15 · Ve la orden de la mesa 4 con su contenido | [`C02-P02`](capturas/C02-P02-mesero-2-abre-la-orden.png) |
| 3 | El mesero 2 agrega 1× Agua de horchata y toca «Agregar a la orden» | — | **403** | 12:50:16 · `POST …/items` → **403**. La web muestra «Ocurrio un error al comunicarse con el servidor.» (ver CAL-166) | [`C02-P03`](capturas/C02-P03-mesero-2-agregar-rechazado.png), [`C02-mesero-2-desde-la-interfaz.txt`](respuestas/C02-mesero-2-desde-la-interfaz.txt) |
| 4 | El mesero 2 toca «Cancelar orden» y confirma | — | **403** | 12:50:17 · `POST …/cancel` → **403**, mismo aviso genérico | [`C02-P04`](capturas/C02-P04-mesero-2-cancelar-rechazado.png) |
| 5 | El mesero 2 envía la orden a cocina por la API | — | **403** | 12:50:17 · `POST …/send-to-kitchen` → **403** | [`C02-mesero-2-enviar-a-cocina.txt`](respuestas/C02-mesero-2-enviar-a-cocina.txt) |
| 6 | El mesero 1 vuelve a abrir su orden | — | Sin cambios | 12:50:20 · Sigue «Nueva», 1× Sopa de tortilla, $85.00 | [`C02-P05`](capturas/C02-P05-orden-sin-cambios.png) |

**Observaciones.** Los tres 403 llegan con el cuerpo genérico de Spring (`application/json` con `timestamp`, `status`, `error`, `path`) en lugar de `application/problem+json`: `GlobalExceptionHandler` no atiende `AccessDeniedException` y el mensaje de `OrderService` se pierde. Por eso la web muestra un aviso de error de comunicación. Se registró como [CAL-166](https://linear.app/callejon19/issue/CAL-166), severidad media. La web también muestra «Enviar a cocina» y «Cancelar orden» a un mesero que no es dueño de la orden.

### C03 — Una línea con cantidad 100 se rechaza

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Media |
| Rol que ejecuta | WAITER (`mesero@pruebas.com`, dueño de la orden) |
| Inicio / fin de ejecución | 2026-10-21 12:50 / 2026-10-21 12:50 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Orden ORD-261021185004 del mesero 1, en «Nueva» (C02). Se prueba con el dueño para que el rechazo venga del tope de cantidad y no de CAL-111.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Agregar Flan napolitano y tocar «Agregar una unidad» hasta 100 veces | Flan napolitano ($75) | La interfaz no pasa de 99 | 12:50:23 · El carrito se detiene en **99** (subtotal $7,425.00) | [`C03-P01`](capturas/C03-P01-interfaz-tope-99.png) |
| 2 | Enviar a la API una línea con cantidad 100 | `{"items":[{"productId":…,"quantity":100}]}` | **400** | 12:50:25 · **400** `application/problem+json`, «Validacion fallida», `errors["items[0].quantity"]`: «debe ser menor que o igual a 99» | [`C03-cantidad-100.txt`](respuestas/C03-cantidad-100.txt) |
| 3 | Recargar la orden | — | Sin cambios | 12:50:27 · Sigue con 1× Sopa de tortilla, $85.00 | [`C03-P02`](capturas/C03-P02-orden-sin-cambios.png) |

**Observaciones.** El tope existe en dos capas: la interfaz (`MAX_LINE_QUANTITY = 99`) y la validación del backend (`@Max(99)`), que es la que importa porque no depende del cliente.

### Limpieza

12:51:02 · El mesero 1 cancela ORD-261021185004, que seguía en «Nueva»: `POST …/cancel` → **200**, `status: CANCELED`, y la mesa 4 queda libre ([`LIMPIEZA-cancelar-orden-b.txt`](respuestas/LIMPIEZA-cancelar-orden-b.txt), [`LIMPIEZA-mesas-al-final`](capturas/LIMPIEZA-mesas-al-final.png)). La orden de C01, ORD-261021184954 (mesa 3), queda en cocina para la SIT de cobro.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-166](https://linear.app/callejon19/issue/CAL-166) | C02 | Media | El 403 de una orden ajena no es RFC 7807 y la web muestra «Ocurrio un error al comunicarse con el servidor.» | Abierto | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 3 | 3 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- El frontend no tiene registro de despliegue, así que no se sabe qué commit sirve `restohub.azurewebsites.net`. Lo que verifican los criterios (folio, total, 403 y 400) lo responde la API, cuyo commit sí está registrado (`ea9b5c1`).
- C02 paso 5 y C03 paso 2 se enviaron por la API desde la sesión del navegador, porque la interfaz no los permite: no hay botón para que otro mesero envíe a cocina por URL, y el carrito no pasa de 99.
- El segundo mesero (`mesero2@pruebas.com`) queda activo en el restaurante `pruebas` para futuras pruebas.

**Conclusión del ejecutor.** Los tres criterios de CAL-138 se cumplen contra `ea9b5c1` en Azure. La API calcula folio y total al abrir, agregar y enviar a cocina. Un mesero no puede agregar, cancelar ni enviar la orden de otro: los tres intentos responden 403 y la orden no cambia. Una línea con cantidad 100 responde 400. Queda el defecto CAL-166, que afecta el mensaje y el formato del 403, no la regla de seguridad.

## 9. Anexo SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02, C03 | Respuestas literales registradas desde el navegador |
| API ↔ PostgreSQL (reglas de órdenes en la base real) | C01, C02, C03 | Folio y total calculados; 403 y 400 sin cambios en la orden |

**Reproducir la prueba.** El script está en [`playwright/sit-pedidos.mjs`](playwright/sit-pedidos.mjs). Necesita Playwright y Google Chrome, y lee la contraseña de `C9_TEST_PASSWORD`. Se ejecuta en tres etapas: `preparacion`, `casos` y `limpieza`.

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora (o la banda de la captura automatizada) y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** 2026-10-21
