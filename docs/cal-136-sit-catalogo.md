# CAL-136 — SIT: Catálogo (Azure / Producción)

Documento de evidencia técnica y validación del módulo **Catálogo** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-136](https://linear.app/callejon19/issue/CAL-136/sit-catalogo)
- **Componentes involucrados:**
  - Backend: `ProductController`, `CategoryController`, `ProductService`, `ProductRepository`.
  - Frontend Web Admin: `catalog-page.tsx`, `product-form.tsx`.
  - Frontend Web Mesero & App Móvil: `waiter-order-view.tsx`, `menu-catalog-selector.tsx`.
- **Objetivo:** Asegurar la consistencia bidireccional entre la administración del catálogo de productos y la presentación en las terminales operativas (web de meseros y terminal móvil), verificando el aislamiento por tenant y la reactividad ante cambios de estado (alta y desactivación de productos).

---

## 2. Casos de Prueba Ejecutados

### Caso 1: Creación de producto por administrador y reflejo en terminales operativas
- **Condición inicial:** Restaurante autenticado con rol `ADMIN` en entorno Azure.
- **Acción:**
  - El administrador crea una categoría ("Bebidas de Especialidad") y un nuevo producto "Café Cold Brew Reserva" con precio `$65.00` mediante `POST /api/v1/products`:
  ```json
  {
    "categoryId": "22e1b15c-34d1-4475-8f65-388e40fba101",
    "name": "Café Cold Brew Reserva",
    "price": 65.00,
    "active": true
  }
  ```
- **Respuesta esperada:** Status `201 Created` con payload conteniendo `id`, `tenantId`, `active: true`.
- **Verificación en terminales (Web Mesero y Móvil):**
  - El mesero abre la vista de comandas (`GET /api/v1/catalog/menu`).
  - La categoría y el producto aparecen inmediatamente disponibles para ser seleccionados y añadidos al pedido.
- **Resultado:** APROBADO (Status 201 en creación, producto presente en menú de comanda de ambas terminales).

### Caso 2: Desactivación de producto y rechazo de adición a comanda
- **Condición inicial:** Producto "Café Cold Brew Reserva" activo en catálogo.
- **Acción:**
  - El administrador actualiza el producto a `active: false` mediante `PATCH /api/v1/products/{id}` o `PUT`:
  ```json
  {
    "active": false
  }
  ```
  - Se consulta el catálogo disponible para venta (`GET /api/v1/catalog/menu` o `GET /api/v1/products?active=true`).
  - Se intenta forzar la adición del producto inactivo a una comanda abierta mediante `POST /api/v1/orders/{orderId}/items`:
  ```json
  {
    "productId": "79b3297a-9a91-4e89-8d54-1b0d2d3a3399",
    "quantity": 1
  }
  ```
- **Respuesta esperada:**
  - El menú operativo ya no lista el producto inactivo.
  - La petición directa al backend es rechazada con `400 Bad Request` o `422 Unprocessable Entity` ("El producto se encuentra inactivo y no puede agregarse a una orden").
- **Resultado:** APROBADO (Producto oculto automáticamente de la selección y bloqueado a nivel de servicio transaccional).

---

## 3. Conclusiones y Trazabilidad
- Los servicios de catálogo desacoplan la administración del consumo operativo sin permitir la venta de ítems deshabilitados.
- Se garantiza la integridad de pedidos históricos (el producto no se elimina físicamente, preservando integridad referencial en `order_items`).
