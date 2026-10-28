# Evidencia de prueba · CAL-136 — SIT · Catálogo

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Validación de alta de producto, visibilidad en carta activa, desactivación y rechazo transaccional con HTTP 409 (`BusinessRuleException`). Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-136](https://linear.app/callejon19/issue/CAL-136) — SIT · Catálogo |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Catálogo (`ProductController`, `CategoryController`, `OrderService`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118), 2026-10-21 00:56 |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | curl.exe 8.10.1 en Windows 11 (64-bit) |
| Restaurante de prueba | `sit-val-10281430` |
| Versión de esta evidencia | v2 — 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-136) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Alta de categoría base y sesiones |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un producto creado por el administrador se publica en el catálogo activo para meseros, y que al desactivarlo deja de aparecer y el sistema rechaza su inclusión en comandas abiertas con HTTP 409 Conflict.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Producto creado por el administrador aparece en el menú activo | C01 |
| 2. Producto desactivado deja de aparecer y no se puede agregar a una comanda (409) | C02 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Inquilino `sit-val-10281430` con sesión de Administrador y Mesero iniciadas
- [x] Categoría de bebidas creada en el catálogo

## 6. Casos de prueba

### C01 — Alta de producto y visibilidad en catálogo activo

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `POST /api/v1/products` con Administrador | 201 Created con entidad de producto | HTTP 201 Created, `{"id":"...","name":"Agua Fresca ...","price":45.00,"active":true}` | [`C01-crear-producto.txt`](respuestas/C01-crear-producto.txt) |
| 2 | `GET /api/v1/products?categoryId=...` con Mesero | 200 OK con el producto en la lista | HTTP 200 OK, lista incluye el producto creado | [`C01-catalogo-activo.txt`](respuestas/C01-catalogo-activo.txt) |

### C02 — Desactivación de producto y rechazo transaccional en comanda

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `PATCH /api/v1/products/{id}` con `{"active":false}` | 200 OK con producto inactivo | HTTP 200 OK, `{"active":false}` | [`C02-desactivar-producto.txt`](respuestas/C02-desactivar-producto.txt) |
| 2 | `GET /api/v1/products?categoryId=...` con Mesero | 200 OK con lista vacía (no incluye inactivos) | HTTP 200 OK, `[]` | [`C02-catalogo-sin-inactivo.txt`](respuestas/C02-catalogo-sin-inactivo.txt) |
| 3 | `POST /api/v1/orders/{orderId}/items` intentando agregar el producto desactivado | 409 Conflict (`BusinessRuleException`) | HTTP 409 Conflict, ProblemDetail `El producto Agua Fresca ... esta inactivo.` | [`C02-rechazo-producto-inactivo-comanda.txt`](respuestas/C02-rechazo-producto-inactivo-comanda.txt) |

## 7. Defectos encontrados

Ninguno. El backend de Spring Boot valida la inactividad del producto en la capa de dominio (`OrderService.java:210`) respondiendo con código HTTP 409 y título `Regla de negocio`.

## 8. Resumen de ejecución y desviaciones

La prueba se ejecutó mediante peticiones HTTP reales sobre Azure. Todas las respuestas corresponden a Spring Boot y demuestran la protección integral contra venta de productos dados de baja.

## 9. Declaración de autenticidad

Declaro que las pruebas registradas en este documento y en los archivos adjuntos se ejecutaron contra el sistema bajo prueba en el entorno declarado, que las capturas y respuestas HTTP corresponden a resultados observados y que no han sido alteradas para ocultar defectos.

**Ejecutor:** García Gaytán Ludwin  
**Fecha:** 2026-10-28

## 10. Revisión independiente

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas y respuestas coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue en Azure
- [ ] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| *Pendiente* | | | Sometido a revisión independiente tras re-ejecución real en Azure. |
