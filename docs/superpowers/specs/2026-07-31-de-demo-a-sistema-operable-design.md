# Diseño — De demo a sistema operable

- **Fecha:** 2026-07-31
- **Entrega:** sábado 2026-08-01 (defensa académica)
- **Estado:** aprobado por el usuario
- **Punto de partida:** el recorrido completo funciona y está verificado en navegador; 77 pruebas verdes; CI verde en `main`

## Context

El sistema hace el flujo completo —alta de restaurante, comanda, cocina, cobro, ticket— y lo hace bien. Pero se comporta como una demostración, no como software que un restaurante pueda operar. Tres carencias lo delatan, y no son de amplitud sino de profundidad:

**No existe forma de crear un usuario.** Los cinco roles están diseñados, protegidos con `@PreAuthorize` y documentados, pero solo puede existir la cuenta de administrador que el registro crea. Un mesero no puede entrar al sistema porque no se puede dar de alta un mesero. Todo el modelo de roles es, hoy, teoría.

**Nada se puede modificar.** Veinte endpoints, cero `PUT`, cero `DELETE`. No se corrige el nombre de un platillo, no se cambia un precio, no se da de baja una mesa, no se cancela una comanda equivocada. `OrderStatus.CANCELED` existe en el enum desde la primera migración y **ningún camino del sistema lo alcanza**.

**`PlanLimitService` está escrito y nadie lo llama.** Valida el tope de usuarios del plan, tiene su prueba, y no se invoca desde ningún endpoint de producción. Los planes cobran distinto y no limitan nada.

El objetivo de este incremento es cerrar esas tres cosas, para que el sistema se sostenga solo y para que la demostración muestre un restaurante funcionando en lugar de una persona haciendo clics.

### Decisiones tomadas

| Pregunta | Elección |
|---|---|
| ¿Profundidad o amplitud? | **Profundidad.** Que lo existente se comporte como producto, antes que agregar módulos |
| ¿Qué narra la demostración? | **Varios roles simultáneos**: tres ventanas, mesero, cocina y caja trabajando a la vez |

La segunda decisión es la que ordena el trabajo: exige usuarios reales, así que ese bloque va primero y los demás se acomodan alrededor.

---

## 1. Usuarios por rol

Es el único bloque que la narrativa elegida exige. Sin él no hay tres ventanas.

### Endpoints

| Método | Ruta | Rol | Comportamiento |
|---|---|---|---|
| `POST` | `/api/v1/users` | `ADMIN` | Crea usuario con rol. `409` si el correo ya existe en el restaurante; `409` si el plan no admite más |
| `GET` | `/api/v1/users` | `ADMIN` | Lista los usuarios del restaurante |
| `PATCH` | `/api/v1/users/{id}` | `ADMIN` | Activa o desactiva |

Cuerpo de creación: `{ email, fullName, role, password }`. El rol es uno de `ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`; **`SUPER_ADMIN` se rechaza con `400`** — pertenece al inquilino técnico de plataforma y no debe poder crearse desde un restaurante.

### Por qué es sencillo

`TenantFilter` ya dejó el inquilino en `TenantContext` antes de que el controlador corra, así que un `@Transactional` corriente basta y `TenantScopedEntity` asigna el `tenant_id` en su `@PrePersist`. No se repite el problema del alta de restaurante, que ocurría sin inquilino y obligó a partir la operación en dos transacciones.

La contraseña se cifra con el `PasswordEncoder` existente. No se fuerza cambio en el primer ingreso: no hace falta para esto y añade una pantalla.

### Límites de plan

`UserService` llama a `PlanLimitService.assertCanAddUser(...)` antes de guardar. Con eso el plan `FREE` topa de verdad en 3 usuarios y devuelve `409` con *"Alcanzaste el limite de 3 usuarios del plan FREE."*

Es la primera vez que los planes restringen algo. Vale como momento demostrable: un restaurante gratuito que no puede crear el cuarto usuario.

### Baja lógica, nunca física

Un usuario está referenciado por las comandas que tomó (`orders.waiter_id`) y las ventas que cobró (`sales.cashier_id`). Aunque ambas llaves son `ON DELETE SET NULL` y un borrado no rompería la integridad, sí borraría la autoría del histórico. Se marca `active = false`.

`AuthService` ya rechaza usuarios inactivos con `BadCredentialsException`, así que desactivar a alguien lo deja fuera de inmediato sin código nuevo.

### Dos protecciones de producto

- Un administrador **no puede desactivarse a sí mismo**.
- **No se puede desactivar al último administrador activo** del restaurante.

Sin ellas, un clic deja el restaurante sin quien lo administre y sin forma de recuperarse desde la interfaz. Ambas devuelven `409` con un mensaje explícito.

### Frontend

Una pestaña *Usuarios* en `/admin`: lista con rol y estado, formulario de alta, e interruptor de activo.

No hace falta tocar la navegación: `app-sidebar.tsx` ya reparte las secciones por rol. En cuanto existan usuarios reales, un `WAITER` verá solo *Mesas* y un `KITCHEN` solo *Cocina*. Que los permisos sean visibles deja de ser una afirmación y pasa a ser algo que se observa.

---

## 2. Correcciones

Cierra la ausencia de operaciones de modificación.

| Método | Ruta | Rol | Qué resuelve |
|---|---|---|---|
| `POST` | `/orders/{id}/cancel` | `WAITER`, `ADMIN` | Cancela la comanda y libera la mesa |
| `PUT` | `/products/{id}` | `ADMIN` | Nombre, descripción, precio, categoría |
| `PATCH` | `/products/{id}` | `ADMIN` | Alta o baja del platillo |
| `PUT` | `/tables/{id}` | `ADMIN` | Capacidad |
| `PATCH` | `/tables/{id}` | `ADMIN` | Alta o baja de la mesa |
| `PUT` | `/categories/{id}` | `ADMIN` | Nombre y orden |

### La cancelación es la más importante

En una sola transacción: la comanda pasa a `CANCELED`, la mesa vuelve a `FREE`, y los renglones se conservan para el registro. Devuelve `409` si la comanda ya está `PAID` o `CANCELED`.

Es el primer camino del sistema que alcanza ese estado, y responde una pregunta que el jurado hará casi con certeza: *¿qué pasa si el mesero se equivoca de mesa?*

### Un efecto secundario que conviene mostrar

Editar el precio de un platillo **no altera las comandas ya tomadas**: el precio se copió al agregarse el renglón. Subir la arrachera de $289 a $320 y abrir después una comanda anterior, que sigue en $289, demuestra la inmutabilidad del ticket sin necesidad de explicarla.

Dar de baja un producto tampoco afecta a las comandas que ya lo incluyen; solo desaparece del selector.

### Frontend

Botones de editar y dar de baja en las pantallas que ya existen —productos, categorías y mesas en `/admin`—, y la cancelación desde la pantalla de comanda del mesero, con confirmación.

---

## 3. Historial de ventas

`GET /api/v1/sales` con filtro opcional de fechas, y una sección *Historial* con las ventas del día, el total acumulado y la búsqueda de un ticket por folio.

Responde la pregunta obvia que hoy no tiene respuesta: una vez cobrada, la comanda desaparece de la pantalla y no hay forma de volver a verla.

Es el bloque más barato de los tres y el primero que se sacrifica si el reloj aprieta.

---

## Orden de construcción

Cada bloque queda utilizable por sí solo, así que un corte a mitad de camino deja un sistema coherente y no uno roto.

1. **Usuarios, backend** — endpoints, límites de plan, protecciones
2. **Usuarios, frontend** — pantalla, y verificación en navegador de las tres ventanas simultáneas
3. **Correcciones, backend** — la cancelación primero, después el resto
4. **Correcciones, frontend** — editar y dar de baja en las pantallas existentes
5. **Historial** — endpoint y pantalla

Los bloques 1 y 2 no se negocian: entregan la narrativa elegida. Si el tiempo no alcanza, se cae el 5 primero y luego parte del 4. El aviso de que algo no entra se da en cuanto se detecta, no al final.

## Verificación

Como hasta ahora: prueba que falla primero, suite completa verde antes de cada commit, y comprobación en navegador real al cerrar cada bloque de frontend. La suite está en 77 pruebas y debe crecer con cada bloque.

Al terminar el bloque 2, la comprobación decisiva es abrir tres ventanas —mesero, cocina y caja, cada una con su propia cuenta— y confirmar que una comanda enviada en la primera aparece sola en la segunda, y que cada sesión solo ve su sección.

## Fuera de alcance

Sin cambios respecto al alcance anterior: delivery y rastreo, analítica y aprendizaje automático, sensores IoT, MercadoPago real, segundo factor TOTP, rotación de tokens de refresco, inventario con descuento por receta, notificaciones push, y el ETL de migración desde MongoDB.

Tampoco entra aquí: cambio de contraseña por el propio usuario, recuperación de contraseña, ni edición del perfil. Son de producto real, no de esta entrega.
