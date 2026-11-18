# Integración de la app móvil con el servicio en la nube — Callejón 9

Issue: [CAL-186](https://linear.app/callejon19/issue/CAL-186) — Editar productos desde la app móvil y documentar su sincronización con la API en Azure. Relacionados: CAL-160 (consulta y registro) y CAL-114 (app móvil).

> **Estado: pruebas automatizadas ejecutadas; pruebas contra Azure pendientes.** Las 53 pruebas unitarias de la app (18 de ellas del catálogo) se ejecutaron y pasaron el 2026-11-04. Los casos M01–M07 contra la API desplegada en Azure **no se han ejecutado** y sus resultados están vacíos a propósito. Se llenan durante la sesión, con capturas del teléfono en `capturas/` y respuestas HTTP en `respuestas/`.

Actividad: *Ampliar un proyecto móvil existente mediante la integración con un servicio en la nube, permitiendo enviar información, consultar los registros remotos y controlar el acceso a las operaciones que requieran autenticación.*

## 1. Proyecto y funcionalidad elegida

**Proyecto integrador:** Callejón 9, un SaaS para restaurantes. La app móvil de meseros (`mobile/`, Expo + React Native) usa la misma API que la versión web.

**Entidad elegida: productos del menú** (tabla `products`).

| Qué | Datos | Quién puede hacerlo |
|---|---|---|
| **Se consulta** | Nombre, precio, descripción, categoría y si está activo. Las categorías se consultan aparte | Cualquier usuario autenticado del restaurante (ADMIN, WAITER, KITCHEN, CASHIER) |
| **Se envía (registro)** | Nombre\*, precio\*, descripción y categoría | Solo ADMIN |
| **Se actualiza** | PUT para todos los campos; PATCH solo para activar o desactivar | Solo ADMIN |
| **Nunca se envía** | El restaurante (`tenant_id`) | Lo pone el servidor a partir del JWT firmado |

\* Obligatorio.

**Situación que resuelve.** Si un administrador da de alta, corrige o retira un producto desde su teléfono, el cambio se guarda en PostgreSQL en la nube. Lo ven en ese momento los meseros, en la app y en la web. No hay copia local que se desactualice: cada pantalla vuelve a consultar el servidor al abrirse y al deslizar hacia abajo.

## 2. Servicio remoto y endpoints

| Elemento | Valor |
|---|---|
| Servicio | API REST propia (Java 21, Spring Boot 3.4) sobre PostgreSQL 16 |
| Alojamiento | Azure App Service |
| URL base | `https://app-callejon9-api-prod-3878c6.azurewebsites.net/api/v1` |
| Documentación navegable | `/swagger-ui.html` en la misma URL |
| Configuración en la app | `EXPO_PUBLIC_API_URL` en `mobile/.env` (`mobile/src/config.ts`) |

| Operación | Método y ruta | Cuerpo | Respuesta correcta | Rol | Código en la app |
|---|---|---|---|---|---|
| Iniciar sesión | `POST /auth/login` | `{slug, email, password}` | 200 + cookies `httpOnly` | — | `src/api/auth.ts` |
| Renovar sesión | `POST /auth/refresh` | — | 200 + cookies nuevas | Sesión vigente | `src/api/client.ts` |
| Consultar productos | `GET /products[?includeInactive=true]` | — | 200 `Product[]` | Cualquiera autenticado (los inactivos los pide solo ADMIN) | `fetchCatalog` en `src/api/catalog.ts` |
| Consultar categorías | `GET /categories` | — | 200 `Category[]` | Cualquiera autenticado | `fetchCategories` |
| **Registrar producto** | `POST /products` | `{name, price, description?, categoryId?}` | **201** `Product` | ADMIN | `createProduct` |
| **Editar producto** | `PUT /products/{id}` | `{name, price, description?, categoryId?}` | 200 `Product` | ADMIN | `updateProduct` |
| **Activar/desactivar** | `PATCH /products/{id}` | `{active}` | 200 `Product` | ADMIN | `setProductActive` |

Errores, todos en formato RFC 7807 (`application/problem+json`): 400 con errores por campo, 401, 403, 404, 409 (por ejemplo, un nombre repetido), 429 y 5xx.

## 3. Flujo de la información

```text
 Teléfono (Expo Go)                       Azure App Service                    PostgreSQL
 ───────────────────                      ─────────────────                    ──────────
 ProductFormScreen
   │ 1. validateProduct() en el cliente
   │    (si falla, no se envía nada)
   ▼
 catalog.ts → client.ts ──HTTPS──►  2. Spring Security valida el JWT
   fetch + cookie httpOnly             de la cookie y el rol
   tiempo límite                       (@PreAuthorize ADMIN → 403 si no)
                                     3. @Valid sobre el cuerpo (→ 400)
                                     4. ProductService: reglas de negocio
                                        (nombre repetido → 409)
                                     5. set_config('app.tenant_id', …) ──────► 6. RLS filtra y
                                        en la misma transacción                 comprueba el
                                                                                restaurante
 ◄─────────── JSON (201/200) o ProblemDetail (4xx/5xx) ─────────────────────────
   │ 7. client.ts traduce el error a un mensaje en español
   ▼
 ProductsScreen: aviso verde «Producto registrado / actualizado»
 o rojo con el motivo; la lista se vuelve a pedir al servidor.
```

Si la petición recibe un 401 porque el access token (15 minutos) expiró, `client.ts` llama una sola vez a `POST /auth/refresh` y repite la petición. Si la renovación también falla, la app vuelve al login.

## 4. Medidas de seguridad

| Medida | Cómo se cumple | Dónde |
|---|---|---|
| **HTTPS** | La app se niega a arrancar si la URL no es `https://`. Solo en desarrollo se admite `http://` con `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=true`. Azure sirve la API con TLS, y la API envía HSTS | `mobile/src/config.ts`, `config.test.ts`; `SecurityConfig.java` (CAL-183) |
| **Validación antes del envío** | Nombre y precio obligatorios. Nombre de hasta 160 caracteres, precio con hasta 8 enteros y 2 decimales (sin negativos), descripción de hasta 500. Con errores, el formulario no envía nada y marca cada campo | `mobile/src/validation/product.ts`, `catalog.test.ts` |
| **Validación en el servidor** | `@Valid` con `@NotBlank`, `@NotNull`, `@Size` y `@DecimalMin`. El servidor vuelve a validar aunque el cliente esté alterado | `CreateProductRequest`, `UpdateProductRequest` |
| **Autenticación remota real** | Login contra la API con BCrypt. El JWT va en una cookie `httpOnly`, `Secure`, `SameSite=Strict`, que guarda el almacén de cookies nativo: JavaScript nunca lee el token. La contraseña se borra del formulario al enviarla | `src/api/auth.ts`, `AuthController.java` |
| **Autorización en el servidor** | `@PreAuthorize("hasRole('ADMIN')")` en POST, PUT y PATCH. Ocultar los botones a un mesero es solo comodidad: si llama la API directamente, recibe 403 | `ProductController.java`, `ProductControllerTest#waiterCannotUpdateOrDeactivateProducts` |
| **Aislamiento entre restaurantes** | La app no envía el restaurante. El servidor lo toma del JWT firmado y PostgreSQL lo impone con Row Level Security: un ADMIN no puede ver ni modificar productos de otro restaurante | `TenantContext`, políticas RLS en `V2__data_plane.sql` |
| **Errores sin datos sensibles** | La app muestra mensajes fijos para 401, 403 y 5xx, y nunca enseña el detalle del servidor en esos casos. El backend no devuelve trazas ni errores de la base. Login fallido: el mismo 401 para cualquier dato incorrecto | `src/api/client.ts` (`toApiError`), `GlobalExceptionHandler.java` |
| **Rutas seguras** | El `id` del producto se codifica con `encodeURIComponent`, así que un valor como `../users` no puede alterar la ruta | `catalog.ts`, `catalog.test.ts` |
| **Freno a fuerza bruta** | 5 fallos por cuenta o 20 por IP en 15 minutos → 429 | `LoginAttemptLimiter.java` |

## 5. Matriz de pruebas

### 5.1 Automatizadas (ejecutadas)

`cd mobile; pnpm test` · 2026-11-04 · rama `feat/mobile-editar-productos` · **53/53 pasaron** (5 archivos). El servidor se simula con un `fetch` falso, así que estas pruebas comprueban el código de la app, no la red.

| # | Prueba de la actividad | Caso automatizado (`src/test/catalog.test.ts`) | Resultado |
|---|---|---|---|
| A1 | 1 · Consultar | `GET /products` solo activos; `?includeInactive=true` para ADMIN; no envía el restaurante | ✅ Pasó |
| A2 | 2 · Crear | `POST /products` con el cuerpo en JSON → producto con 201 | ✅ Pasó |
| A3 | Actualización | `PUT /products/{id}` con todos los campos; `PATCH` solo con `{active}`; id codificado | ✅ Pasó |
| A4 | 4 · Datos incompletos | La validación del cliente rechaza nombre o precio vacíos, precio inválido y textos largos; un 400 del servidor se convierte en errores por campo | ✅ Pasó |
| A5 | 5 · Falla de conexión | Sin red → `network`; sin respuesta → `timeout` (tiempo límite); 500 → mensaje genérico sin detalle interno | ✅ Pasó |
| A6 | Autorización | 403 → «Tu rol no tiene permiso para esta acción.»; 409 muestra el motivo; 404 | ✅ Pasó |

También pasaron `pnpm exec tsc --noEmit` y `pnpm exec expo export --platform android`, que confirma que el bundle compila. **No se ejecutaron** las pruebas del backend: `ProductControllerTest` cubre PUT, PATCH, 409, 404 y el 403 del mesero, pero requiere Docker y no estaba disponible. En este cambio no se tocó el backend.

### 5.2 Contra la API en Azure (pendientes de ejecutar)

**Preparación.**
- `mobile/.env` con `EXPO_PUBLIC_API_URL=https://app-callejon9-api-prod-3878c6.azurewebsites.net`.
- `pnpm start` y abrir con Expo Go.
- Restaurante `pruebas`, con una cuenta ADMIN y una WAITER.
- Las contraseñas no se escriben en este documento ni salen en las capturas.

| # | Prueba | Pasos | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| M01 | 1 · Consultar registros | Entrar como ADMIN → Mesas → «Menú» | Lista de productos del servidor, con categoría y precio. Los inactivos aparecen atenuados con «(inactivo)» | | `capturas/M01-menu.png` |
| M02 | 2 · Crear registro | «Nuevo producto» → nombre `Prueba móvil <hora>`, precio `55.50`, una categoría → «Registrar producto» | Aviso verde «Producto registrado» y el producto resaltado en la lista | | `capturas/M02-registrado.png` |
| M03 | 3 · Volver a consultar | Deslizar hacia abajo para recargar. Abrir el mismo restaurante en la web (`/admin`) o hacer `GET /products` | El producto de M02 sigue ahí, porque viene del servidor y no de la app | | `capturas/M03-web.png`, `respuestas/M03-get-products.txt` |
| M04 | Actualizar | Tocar el producto de M02 → cambiar el precio a `60` → «Guardar cambios». Luego «Desactivar» y «Activar» | Avisos «Producto actualizado», «Producto desactivado» y «Producto activado». Al recargar se conservan | | `capturas/M04-*.png` |
| M05 | 4 · Datos incompletos | «Nuevo producto» con el nombre vacío y el precio `abc` → «Registrar» | No sale ninguna petición. Se marcan «El nombre es obligatorio.» y «Escribe un precio válido…» | | `capturas/M05-validacion.png` |
| M05b | 4 · El servidor también valida | `curl` con un nombre vacío (ver abajo) | **400** `application/problem+json` con `errors.name` | | `respuestas/M05b-post-400.txt` |
| M06 | 5 · Error de conexión | Activar modo avión → «Registrar producto» con datos válidos | Aviso rojo «No se pudo conectar con el servidor. Revisa tu conexión a internet.» Al quitar el modo avión, reintentar funciona | | `capturas/M06-sin-red.png` |
| M07 | Autorización | Entrar como WAITER → «Menú». Luego `curl` de `POST /products` con la cookie del mesero | La app no muestra «Nuevo producto» ni «Desactivar». La API responde **403** | | `capturas/M07-mesero.png`, `respuestas/M07-post-403.txt` |

**Comandos de M05b y M07.** Antes de correrlos, escribe la contraseña en la variable `C9_PASSWORD` de tu sesión. Las respuestas guardadas no deben incluir la cabecera `Set-Cookie`.

```bash
API=https://app-callejon9-api-prod-3878c6.azurewebsites.net/api/v1
# Iniciar sesión (ADMIN para M05b, WAITER para M07) y guardar la cookie
curl -s -c cookies.txt -H 'Content-Type: application/json' \
  -d "{\"slug\":\"pruebas\",\"email\":\"<correo>\",\"password\":\"$C9_PASSWORD\"}" "$API/auth/login" -o /dev/null -w '%{http_code}\n'
# Registro incompleto (M05b → 400) o registro con el mesero (M07 → 403)
curl -s -i -b cookies.txt -H 'Content-Type: application/json' -d '{"name":"","price":10}' "$API/products"
rm cookies.txt
```

## 6. Cómo funciona la integración (explicación del equipo)

1. **Una sola puerta de red.** Todas las pantallas llaman funciones de `src/api/` (`catalog.ts`, `waiter.ts`, `auth.ts`), y estas pasan por `request()` en `client.ts`. Ahí se arma la URL `https://…/api/v1`, se adjunta la cookie de sesión, se pone un tiempo límite y se traduce cualquier error a un mensaje en español. Así, consulta, registro y actualización se comportan igual ante fallas.
2. **Consulta.** Al abrir «Menú», `ProductsScreen` pide productos y categorías en paralelo. Para un ADMIN pide también los inactivos. Deslizar hacia abajo repite la consulta, así que lo que se ve siempre es el estado del servidor.
3. **Registro y actualización.** `ProductFormScreen` sirve para las dos cosas: sin producto hace `POST`, con producto hace `PUT`. Antes de enviar, valida con las mismas reglas que el backend. El botón activar/desactivar del listado hace `PATCH` y reemplaza la fila con lo que devolvió el servidor.
4. **Respuesta al usuario.** Un éxito muestra un aviso verde con los datos que confirmó el servidor. Un error muestra un aviso rojo: el motivo de negocio en 400, 404 y 409, y un mensaje fijo en 401, 403, 5xx, sin red o sin respuesta.
5. **Seguridad en el servidor, no en la app.** La app oculta los botones de administración a los meseros, pero quien decide es el backend: valida el JWT, exige el rol ADMIN y PostgreSQL aísla cada restaurante con RLS.

## 7. Archivos de este cambio

| Archivo | Cambio |
|---|---|
| `mobile/src/api/client.ts` | Admite `PUT` y `PATCH` |
| `mobile/src/api/types.ts` | `UpdateProductRequest`, `UpdateProductStatusRequest`; `categoryId` puede ser `null` |
| `mobile/src/api/catalog.ts` | `fetchCatalog`, `updateProduct`, `setProductActive` |
| `mobile/src/validation/product.ts` | Validación del formulario, separada de la pantalla para poder probarla |
| `mobile/src/screens/ProductFormScreen.tsx` | Modo edición (PUT) además del registro (POST) |
| `mobile/src/screens/ProductsScreen.tsx` | Productos inactivos para ADMIN, edición al tocar una fila, activar/desactivar (PATCH), avisos de éxito y error |
| `mobile/App.tsx` | Navegación al formulario en modo edición |
| `mobile/src/test/catalog.test.ts` | 18 pruebas nuevas |
