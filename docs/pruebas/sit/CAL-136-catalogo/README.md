# Evidencia de prueba · CAL-136 — SIT · Catálogo

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID, `Authorization: Bearer`, códigos y mensajes que el código no produce) y registraba una revisión que no ocurrió. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo, completa, sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-136](https://linear.app/callejon19/issue/CAL-136/sit-catalogo) — SIT · Catálogo |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Catálogo (web: `/admin` pestaña «Productos» y `/waiter/order/{id}`; backend `catalog` y `order`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Brave 156.1.98.60 (Chromium 156.0.8078.48) automatizado con Playwright 1.56.1 en Garuda Linux, ventana de 1440 × 900 |
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
| Esta tarea (CAL-136) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:01 | 2026-10-28 14:01 | — |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:01 | 2026-10-28 14:01 | — |
| Re-prueba de defectos | | | | | Sin defectos |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el alta y la baja de un producto, hechas por el administrador en la web, llegan al menú del mesero a través de la API de Azure, y que la API impide agregar a una comanda un producto dado de baja aunque el mesero ya lo tuviera en el carrito.

**Fuera de alcance.** La app móvil de meseros: por decisión del responsable, esta re-ejecución cubre solo el cliente web. La parte «y de la app móvil» de los criterios 1 y 2 queda **no ejecutada** (ver sección 8). La app consume el mismo `GET /api/v1/products` que se registra en C01 y C02, pero eso no se cuenta como prueba en el dispositivo.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Producto creado por el administrador en la web aparece en el menú del mesero web y de la app móvil. | C01 (web). App móvil: ⏭️ No ejecutado |
| 2. Producto desactivado deja de aparecer en ambos y no se puede agregar a una comanda. | C02 (web). App móvil: ⏭️ No ejecutado |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:01
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Al menos una mesa libre para la orden de prueba: la 1

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Administrador | `admin@pruebas.com` (ADMIN) | Alta de usuarios de prueba del 2026-10-16 |
| Mesero | `mesero@pruebas.com` (WAITER) | Alta de usuarios de prueba del 2026-10-16 |
| Producto | «SIT CAL-136 Tostada de prueba 1401», $79.00, categoría «Entradas», `c80c1f56-9313-4de5-a2aa-8663a34afc2b` | Alta desde la web en C01 |
| Orden | `885a7244-31ec-4303-b446-2c285571b7aa`, folio `ORD-261028200124`, mesa 1 | Abierta por el mesero desde la web en C01 |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-catalogo.mjs`](playwright/sit-catalogo.mjs) (`casos` y después `limpieza`) con dos sesiones de navegador simultáneas, una por rol. Las peticiones que hizo la interfaz se registraron desde el navegador con su código y cuerpo literales; las que no tienen botón en la web (C01 paso 5, C02 pasos 5 a 7) se enviaron desde la misma sesión del mesero por el proxy `/api/v1/*`. Cada captura lleva una banda roja con la URL y la hora.

### C01 — El producto que crea el administrador aparece en el menú del mesero

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 (cliente web) |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (`admin@pruebas.com`) y WAITER (`mesero@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:01` / `2026-10-28 14:01` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesiones web del administrador y del mesero en el restaurante `pruebas`; el producto no existe; la mesa 1 está libre.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Administrador: abrir «Administración» → pestaña «Productos» | — | Lista del catálogo | 14:01:16 · Lista de productos sin el de prueba | [`C01-P01`](capturas/C01-P01-productos-antes.png) |
| 2 | Tocar «Nuevo producto» y llenar el formulario | Nombre «SIT CAL-136 Tostada de prueba 1401», precio 79, categoría «Entradas» | Formulario lleno | 14:01:17 · Diálogo «Nuevo producto» con los datos | [`C01-P02`](capturas/C01-P02-formulario-nuevo-producto.png) |
| 3 | Tocar «Crear producto» | — | La API crea el producto activo | 14:01:18 · `POST /api/v1/products` → **201**, `active: true`, `price: 79`. La fila aparece con la insignia «Activo» | [`C01-P03`](capturas/C01-P03-producto-activo-en-la-lista.png), [`C01-alta-desde-la-interfaz.txt`](respuestas/C01-alta-desde-la-interfaz.txt) |
| 4 | Mesero: abrir la mesa 1 con 2 comensales y mostrar la categoría «Entradas» | 2 comensales | El producto nuevo aparece en el menú del mesero | 14:01:25 · `POST /api/v1/orders` → **201**, folio `ORD-261028200124`, `status: NEW`. 14:01:26 · En «Entradas» aparece «SIT CAL-136 Tostada de prueba 14…», $79.00, con su botón «+» | [`C01-P04`](capturas/C01-P04-menu-del-mesero-con-el-producto.png) |
| 5 | Pedir el menú activo con la sesión del mesero | `GET /api/v1/products` | El producto está en el menú | 14:01:27 · **200**; la lista incluye `c80c1f56-…` con `active: true` | [`C01-menu-del-mesero.txt`](respuestas/C01-menu-del-mesero.txt) |

**Respuesta HTTP literal**

Completa en [`respuestas/C01-alta-desde-la-interfaz.txt`](respuestas/C01-alta-desde-la-interfaz.txt). Extracto:

```text
POST /api/v1/products
Content-Type: application/json

{"name":"SIT CAL-136 Tostada de prueba 1401","description":"Producto creado por la SIT de catálogo","price":79,"categoryId":"ba57…"}

HTTP 201
Content-Type: application/json
… "active": true
```

**Observaciones.** Ninguna.

### C02 — El producto dado de baja deja de aparecer y no entra a la comanda

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 (cliente web) |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (`admin@pruebas.com`) y WAITER (`mesero@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:01` / `2026-10-28 14:01` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** El producto de C01 está activo; la orden `ORD-261028200124` está en «Nueva» y vacía.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Mesero: tocar «+» del producto de prueba, sin confirmar | 1 unidad | El producto queda en «Por agregar» | 14:01:28 · «Por agregar · 1 línea»: el producto, $79.00 | [`C02-P01`](capturas/C02-P01-producto-en-el-carrito.png) |
| 2 | Administrador: en «Productos», tocar «Dar de baja» en la fila del producto | — | El producto queda inactivo | 14:01:30 · `PATCH /api/v1/products/c80c1f56-…` con `{"active":false}` → **200**, `active: false`. Insignia «Inactivo», botón «Reactivar» y aviso «Producto … desactivado.» | [`C02-P02`](capturas/C02-P02-producto-dado-de-baja.png), [`C02-baja-y-comanda-desde-la-interfaz.txt`](respuestas/C02-baja-y-comanda-desde-la-interfaz.txt) |
| 3 | Mesero: tocar «Agregar a la orden» con el producto en el carrito | — | La API rechaza la comanda | 14:01:31 · `POST /api/v1/orders/885a7244-…/items` → **409** «El producto SIT CAL-136 Tostada de prueba 1401 esta inactivo.» La pantalla muestra ese aviso y la comanda sigue vacía, $0.00 | [`C02-P03`](capturas/C02-P03-comanda-rechazada.png), [`C02-baja-y-comanda-desde-la-interfaz.txt`](respuestas/C02-baja-y-comanda-desde-la-interfaz.txt) |
| 4 | Mesero: recargar la orden y mostrar «Entradas» | — | El producto ya no aparece | 14:01:33 · «Entradas» sin el producto de prueba (0 botones «Agregar SIT CAL-136…») | [`C02-P04`](capturas/C02-P04-menu-sin-el-producto.png) |
| 5 | Pedir el menú activo con la sesión del mesero | `GET /api/v1/products` | El producto no está | 14:01:34 · **200**; la lista ya no incluye `c80c1f56-…` | [`C02-menu-del-mesero.txt`](respuestas/C02-menu-del-mesero.txt) |
| 6 | Enviar el producto directo a la API, sin pasar por el menú | `{"items":[{"productId":"c80c1f56-…","quantity":1}]}` | Rechazo | 14:01:34 · **409**, mismo mensaje | [`C02-agregar-producto-inactivo-api.txt`](respuestas/C02-agregar-producto-inactivo-api.txt) |
| 7 | Consultar la orden | `GET /api/v1/orders/885a7244-…` | Sin cambios | 14:01:35 · **200**, `status: NEW`, `items: []`, `total: 0` | [`C02-orden-sin-cambios.txt`](respuestas/C02-orden-sin-cambios.txt) |

**Respuesta HTTP literal**

```text
# El mesero envía el producto dado de baja directo a la API, sin pasar por el menú
# 2026-10-28 14:01:34 America/Mexico_City · por el proxy https://restohub.azurewebsites.net
POST /api/v1/orders/885a7244-31ec-4303-b446-2c285571b7aa/items
Content-Type: application/json

{
  "items": [
    {
      "productId": "c80c1f56-9313-4de5-a2aa-8663a34afc2b",
      "quantity": 1
    }
  ]
}

HTTP 409
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Regla de negocio",
  "status": 409,
  "detail": "El producto SIT CAL-136 Tostada de prueba 1401 esta inactivo.",
  "instance": "/api/v1/orders/885a7244-31ec-4303-b446-2c285571b7aa/items"
}
```

**Observaciones.** El menú del mesero no se actualiza solo: entre la baja (paso 2) y la recarga (paso 4), la pantalla abierta seguía mostrando el producto (ver [`C02-P03`](capturas/C02-P03-comanda-rechazada.png)). No compromete el criterio porque la API rechaza la comanda, pero el mesero solo se entera al intentar confirmar. No se registra como defecto; se deja como posible mejora (refrescar el catálogo al recibir un 409 de producto inactivo).

**Limpieza.** 14:01:48 · el mesero cancela la orden de prueba: `POST …/cancel` → **200**, `status: CANCELED`, y la mesa 1 queda libre — [`LIMPIEZA-cancelar-orden.txt`](respuestas/LIMPIEZA-cancelar-orden.txt), [`LIMPIEZA-mesas-al-final`](capturas/LIMPIEZA-mesas-al-final.png). El producto queda dado de baja en el catálogo de `pruebas`: no hay borrado de productos.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Sin defectos | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **App móvil no ejecutada.** Por urgencia, el responsable decidió cubrir solo el cliente web. La mitad «app móvil» de los criterios 1 y 2 sigue pendiente y debe probarse en un teléfono con Expo Go, como en [CAL-139](../CAL-139-app-movil/README.md).
- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No toca catálogo ni órdenes, pero el código probado no es exactamente el de `main`.
- **Producto residual.** «SIT CAL-136 Tostada de prueba 1401» queda inactivo en el restaurante `pruebas`.

**Conclusión del ejecutor.** En el cliente web, los dos criterios se cumplen sobre Azure: el alta del administrador llega al menú del mesero, y la baja lo retira del menú y la API la hace cumplir con un 409 aun con el producto en el carrito. El issue no puede cerrarse completo mientras falte la verificación en la app móvil.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01, C02 | `POST /products` 201, `PATCH /products/{id}` 200, `POST /orders/{id}/items` 409, registrados desde el navegador |
| App móvil ↔ API | — | No ejecutado (ver sección 8) |
| API ↔ PostgreSQL (RLS en la base real) | C01, C02 | El producto creado por el administrador es el que recibe el mesero del mismo restaurante; su baja se refleja en la siguiente lectura |
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
