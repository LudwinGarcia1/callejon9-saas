# Evidencia de prueba · CAL-136 — SIT · Catálogo

> **v1.** C01 y C02 ejecutados y verificados contra el entorno de producción en Azure. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-136](https://linear.app/callejon19/issue/CAL-136) — SIT · Catálogo |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Catálogo de productos |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Laptop (Google Chrome 155, 64 bits) y dispositivo móvil Android (Chrome móvil PWA) |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 · 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Verificación en interfaz web y API |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-136) | 2026-10-21 | 2026-10-26 | 2026-10-26 21:50 | 2026-10-26 22:15 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Demostrar que la gestión del catálogo de productos opera consistentemente entre el administrador y los clientes de captura (web y móvil):
1. Un producto creado por el administrador en la web aparece de inmediato en el menú del mesero web y de la app móvil.
2. Un producto desactivado deja de mostrarse en los menús operativos de meseros y es rechazado con error `400 Bad Request` si se intenta enviar a una comanda (regla de integridad `CAL-10`).

---

## 5. Casos de Prueba Ejecutados

### C01 — Alta de Producto y Disponibilidad en Menú Operativo
* **Propósito:** Validar que el administrador crea un nuevo producto y este se sincroniza en la vista operativa de meseros.
* **Pasos:**
  1. El administrador inicia sesión y envía `POST /api/v1/products` con los datos del producto (ej. "Tacos al Pastor", precio `$95.00`, categoría activa).
  2. La API responde `201 Created` devolviendo el UUID generado y estado `active: true`.
  3. El mesero consulta el menú desde la web (`/waiter`) y desde la app móvil (`GET /api/v1/products`).
* **Resultado observado:** El producto aparece en la lista de platillos disponibles con precio e IVA desglosados en ambos clientes.
* **Evidencia:** `respuestas/C01-crear-producto.txt` y `respuestas/C01-catalogo-activo.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Desactivación de Producto e Invariante de Comanda
* **Propósito:** Validar que la inactivación de un producto lo retira del menú y bloquea su captura en pedidos.
* **Pasos:**
  1. El administrador inactiva el producto mediante `PATCH /api/v1/products/{id}` enviando `{"active": false}`.
  2. La API responde `200 OK` con `active: false`.
  3. El mesero consulta el catálogo disponible (`GET /api/v1/products` con `includeInactive=false` por defecto). El producto ya no se lista.
  4. Desde una orden activa, se envía `POST /api/v1/orders/{orderId}/items` intentando forzar la adición del producto inactivo.
* **Resultado observado:**
  * El catálogo operativo omite el producto.
  * La API rechaza la petición de comanda con `HTTP/1.1 400 Bad Request` y mensaje de validación: `"El producto se encuentra inactivo y no puede agregarse a una comanda"` (cumplimiento de `CAL-10`).
* **Evidencia:** `respuestas/C02-desactivar-producto.txt`, `respuestas/C02-catalogo-sin-inactivo.txt` y `respuestas/C02-rechazo-producto-inactivo-comanda.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Creación de producto y visibilidad en clientes web y móvil | ✅ **Aprobado** | Ninguno |
| **C02** | Desactivación de producto y rechazo estricto en comandas | ✅ **Aprobado** | Ninguno |

**Conclusión del ejecutor:** Los dos criterios de `CAL-136` se cumplen en Azure. La sincronización de catálogo refleja el ciclo de vida de los productos y protege las comandas contra platillos dados de baja.

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Las respuestas HTTP coinciden con el comportamiento real del código
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Verificación de catálogo y rechazo de productos inactivos conforme a CAL-10. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutor:** García Gaytán Ludwin · **Fecha:** 2026-10-26
