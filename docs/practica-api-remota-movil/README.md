# Práctica: integración de la app móvil con un servicio remoto

> **v4 · 2026-10-24.** Ejecutados: código, pruebas automatizadas, respuestas HTTP y las pruebas en el teléfono con las capturas de la sección 7. P4.2, P5.4 y P5.5 no se ejecutaron. Documento entregable: Word de la Actividad 15.

## 1. Identificación

| Campo | Valor |
|---|---|
| Alumno | Braulio Hernández |
| Funcionalidad elegida | **Catálogo: consultar productos y registrar productos** |
| Aplicación móvil | `mobile/` — RestoHub Meseros, Expo SDK 57 con React Native 0.86 y TypeScript |
| Servicio remoto | Backend del proyecto (Spring Boot 3.4, Java 21), desplegado en Azure App Service |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Restaurante de prueba | `pruebas` |
| Rama | `test/mobile-catalogo-api-remota` |
| Dispositivo | Teléfono Android con Expo Go (P5); *modelo y versión por anotar* |

Se reutiliza la app y el backend existentes. La práctica no crea un backend nuevo ni datos simulados: todo lo que muestra la app viene de la API.

## 2. API y endpoints utilizados

Todas las rutas cuelgan de `/api/v1` y responden JSON. Los errores siguen RFC 7807 (`application/problem+json`).

| Método | Endpoint | Uso en la app | Rol requerido | Respuesta |
|---|---|---|---|---|
| `POST` | `/auth/login` | Iniciar sesión (`LoginScreen`) | Público | `200` + cookies `access_token` y `refresh_token`; `401` sin cuerpo si falla |
| `GET` | `/auth/me` | Reanudar la sesión al abrir la app | Con sesión | `200` con nombre, rol y restaurante |
| `POST` | `/auth/refresh` | Renovar la sesión ante un `401` | Cookie de renovación | `204` |
| `POST` | `/auth/logout` | Cerrar sesión | Con sesión | `204` |
| `GET` | `/products` | Consultar el menú (`ProductsScreen`) | ADMIN, WAITER, KITCHEN, CASHIER | `200` con la lista de productos activos |
| `GET` | `/categories` | Nombre de la categoría en la lista y opciones del formulario | ADMIN, WAITER, KITCHEN, CASHIER | `200` con la lista de categorías |
| `POST` | `/products` | Registrar un producto (`ProductFormScreen`) | **Solo ADMIN** | `201` con el producto creado; `400` con errores por campo; `403` con otro rol |

**Cuerpo de `POST /products`** (`CreateProductRequest` en el backend):

```json
{ "name": "Agua de jamaica", "price": 45.5, "description": "Opcional", "categoryId": "uuid-opcional" }
```

`name` es obligatorio (máximo 160) y `price` es obligatorio (mayor o igual a 0, hasta 8 enteros y 2 decimales). El restaurante **no** viaja en la petición: el backend lo toma del JWT firmado y PostgreSQL lo impone con Row Level Security.

## 3. Requisitos de conexión

| Requisito | Valor |
|---|---|
| Disponibilidad | `GET /actuator/health` → `200 {"status":"UP"}` — [`respuestas/P1-health.txt`](respuestas/P1-health.txt), 2026-10-24 11:26 |
| Protocolo | Solo HTTPS. `http://` responde `301` hacia `https://` — [`respuestas/P4-http-redirige-a-https.txt`](respuestas/P4-http-redirige-a-https.txt) |
| Configuración de la app | `mobile/.env` (no se versiona) con `EXPO_PUBLIC_API_URL=https://app-callejon9-api-prod-3878c6.azurewebsites.net` y `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=false`. Plantilla en `mobile/.env.example` |
| Herramientas | Node 20 o superior, pnpm 10, Expo Go en el teléfono y la computadora en la misma red Wi-Fi que el teléfono (Metro sirve el código; la API se consulta por internet) |
| Cuentas | `admin@pruebas.com` (ADMIN, puede registrar) y `mesero@pruebas.com` (WAITER, solo consulta). Las contraseñas viven fuera del repositorio |
| Tiempo límite | 15 s por petición (`REQUEST_TIMEOUT_MS` en `mobile/src/config.ts`) |

**Arranque**

```powershell
cd mobile
pnpm install
Copy-Item .env.example .env      # y poner la URL de Azure
pnpm start                       # escanear el QR con Expo Go
```

## 4. Implementación

### Consumo de datos (GET)

| Requisito | Dónde está |
|---|---|
| Solicitud GET | `mobile/src/api/waiter.ts` → `fetchProducts()`; `mobile/src/api/catalog.ts` → `fetchCategories()`. Ambas usan el cliente único `mobile/src/api/client.ts` |
| Procesar el JSON | `client.ts` → `parse()` convierte la respuesta con `response.json()` a los tipos de `mobile/src/api/types.ts` (`Product`, `Category`) |
| Mostrar en pantalla | `mobile/src/screens/ProductsScreen.tsx`: lista ordenada por nombre con categoría, descripción y precio, y el total de productos activos |
| Estado de carga | Indicador «Consultando el menú en el servidor…» en la carga inicial y deslizar para recargar |
| Manejo de errores | Aviso «Error al consultar el menú» con el mensaje traducido y botón «Reintentar» |

### Envío de datos (POST)

| Requisito | Dónde está |
|---|---|
| Formulario de registro | `mobile/src/screens/ProductFormScreen.tsx`: nombre, precio, categoría y descripción. Los obligatorios llevan `*` |
| Validación de obligatorios | `mobile/src/screens/productFormRules.ts` → `validateProduct()`: nombre y precio obligatorios, formato del precio y longitudes máximas, con las mismas reglas que el backend. Si hay avisos, **no se envía nada**. El aviso de un campo desaparece al corregirlo |
| Envío por POST | `catalog.ts` → `createProduct()` con el cuerpo de `toCreateProductRequest()` |
| Procesar la respuesta | Con `201`, la app vuelve al menú con «Producto registrado · El servidor guardó «nombre» a $precio» y resalta el producto nuevo en la lista, que se vuelve a consultar al servidor. Con error, muestra «El producto no se registró» y los avisos por campo que mande el servidor |
| Solo ADMIN | `App.tsx` muestra «Nuevo producto» solo al rol ADMIN; el backend lo vuelve a exigir con `@PreAuthorize("hasRole('ADMIN')")` |

**Cambio de esta práctica.** Las reglas del formulario estaban dentro de la pantalla. Se movieron, sin cambiar su comportamiento, a `productFormRules.ts` para poder probarlas sin React Native, y se agregaron las pruebas de la sección 6.

## 5. Autenticación y seguridad

| Medida | Cómo se cumple | Evidencia |
|---|---|---|
| Autenticación remota | Login contra `POST /auth/login`. La sesión es un JWT en cookies `httpOnly`, `Secure` y `SameSite=Strict`; el almacén de cookies nativo del teléfono las guarda y las adjunta solo. JavaScript nunca lee el token | P5 |
| Sesión corta y renovable | El access token dura 15 minutos. Ante un `401`, la app renueva una sola vez con `/auth/refresh` y repite la petición; si no puede, vuelve al login | Prueba automatizada «con la sesión caducada, renueva una vez y repite el mismo POST» |
| Endpoint protegido | Sin sesión, `GET` y `POST /products` responden `401` sin cuerpo. Con sesión de mesero, `POST /products` responde `403` | [`P4-get-products-sin-sesion-401.txt`](respuestas/P4-get-products-sin-sesion-401.txt), [`P4-post-products-sin-sesion-401.txt`](respuestas/P4-post-products-sin-sesion-401.txt), `P4-post-products-mesero-403.txt` (pendiente, sección 7) |
| HTTPS | `mobile/src/config.ts` rechaza una URL `http://` fuera de desarrollo, y en una build de producción aunque se pida permitirlo. El servidor redirige `http://` a `https://` | [`P4-http-redirige-a-https.txt`](respuestas/P4-http-redirige-a-https.txt); pruebas de `config.test.ts` |
| Sin secretos en el código | `mobile/.env` está en `.gitignore`; solo se versiona `.env.example` con una URL de ejemplo. Ni la app ni los scripts de esta carpeta contienen contraseñas: el script lee la contraseña de una variable de entorno y redacta las cookies | `git ls-files mobile` |
| Errores sin información sensible | `client.ts` traduce cada código a un mensaje fijo. Un `500` muestra «El servidor tuvo un problema. Intenta más tarde.» aunque el servidor mande detalles internos; un `403` no repite el detalle del servidor. El login fallido muestra un solo mensaje, sin decir si falló el restaurante, el correo o la contraseña | Pruebas de `client.test.ts` y `catalog.test.ts` |
| Contraseña | Se captura con `secureTextEntry` y se borra del formulario en cuanto se envía | `LoginScreen.tsx` |

## 6. Pruebas automatizadas

`pnpm exec tsc --noEmit` sin errores y **62 de 62** pruebas aprobadas — [`respuestas/P6-pruebas-automatizadas.txt`](respuestas/P6-pruebas-automatizadas.txt).

Pruebas nuevas de esta práctica (29):

| Archivo | Qué comprueba |
|---|---|
| `mobile/src/test/productFormRules.test.ts` (22) | Formulario vacío marca nombre y precio; nombre de puros espacios cuenta como vacío; precios válidos (`0`, `45`, `45,50`, `99999999.99`) e inválidos (`abc`, `-5`, `45.555`, 9 enteros, `$45`…); bordes de 160 y 500 caracteres; el cuerpo del POST convierte la coma decimal y no envía opcionales vacíos |
| `mobile/src/test/catalog.test.ts` (7) | GET de productos y categorías; POST con el cuerpo en JSON y `201`; `400` con avisos por campo; `403` y `500` con mensaje fijo sin detalle interno; falta de red; renovación de sesión y repetición del mismo POST |

Las demás (33) ya existían para el cliente HTTP, la configuración y el login (CAL-126).

**Prueba de mutación.** Se alteró una línea a la vez de `productFormRules.ts` y `catalog.ts` y se corrieron las pruebas nuevas: **10 de 10** cambios fueron detectados — [`respuestas/P6-mutaciones.txt`](respuestas/P6-mutaciones.txt).

## 7. Pruebas en el teléfono

Capturas con la barra de estado y la hora visibles. Se tapan contraseñas antes de guardar.

**Producto de prueba.** Se registró como «Prueba app móvil agua de tamarindo», precio `45.50`, categoría Bebidas, descripción «agua de tamarindo». El nombre es distinto de los dos productos que se crearon desde la web (ver «Desviaciones»). Al terminar, los tres se desactivan desde la web de administración para que no queden en el menú del restaurante `pruebas`.

### P1 — Consulta exitosa de datos

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Entrar como `admin@pruebas.com` y tocar «Menú» | Indicador de carga y después la lista con «N productos activos», cada uno con categoría y precio | ✅ 9:02 p. m. · «Menú» con «16 productos activos», cada uno con categoría, descripción y precio (Agua de horchata $45.00, Arrachera 300 g $320.00…) | [`capturas/P1-01-menu-consultado.jpeg`](capturas/P1-01-menu-consultado.jpeg) |
| 2 | Deslizar hacia abajo para recargar | La lista se vuelve a consultar | 9:02 p. m. · Lista del menú con 16 productos activos | [`capturas/P1-02-menu-recargado.jpeg`](capturas/P1-02-menu-recargado.jpeg) |

### P2 — Registro válido

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Tocar «Nuevo producto» y llenar el formulario con el producto de prueba | Formulario lleno, Bebidas seleccionada | ✅ 9:04 p. m. · Nombre «Prueba app móvil agua de tamarindo», precio 45.50, Bebidas seleccionada, descripción «agua de tamarindo» | [`capturas/P2-01-formulario-lleno.jpeg`](capturas/P2-01-formulario-lleno.jpeg) |
| 2 | Tocar «Registrar producto» | Vuelve al menú con «Producto registrado · El servidor guardó «nombre» a $45.50» y el producto resaltado en verde | ✅ 9:04 p. m. · «Producto registrado · El servidor guardó «Prueba app móvil agua de tamarindo» a $45.50 y ya aparece en el menú.»; el total pasa de 16 a 17 productos activos | [`capturas/P2-02-producto-registrado.jpeg`](capturas/P2-02-producto-registrado.jpeg) |

### P3 — Envío de un formulario incompleto

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Abrir «Nuevo producto» y tocar «Registrar producto» sin llenar nada | «El nombre es obligatorio.» y «El precio es obligatorio.» en rojo; no sale ninguna petición | 9:13 p. m. · Formulario «Nuevo producto» vacío | [`capturas/P3-01-formulario-vacio.jpeg`](capturas/P3-01-formulario-vacio.jpeg) |
| 2 | Escribir un nombre y el precio `45.555` | «Escribe un precio válido, p. ej. 45 o 45.50.»; el aviso del nombre desaparece | ✅ 9:14 p. m. · Aparece en rojo «Escribe un precio válido, p. ej. 45 o 45.50.»; el nombre no tiene aviso | [`capturas/P3-02-precio-invalido.jpeg`](capturas/P3-02-precio-invalido.jpeg) |
| 3 | Cancelar y revisar el menú | El total de productos no cambió | Versión web · 11:59 a. m. · Lista de productos | [`capturas/P3-03-menu-sin-cambios.png`](capturas/P3-03-menu-sin-cambios.png) |

### P4 — Respuesta fallida o error de conexión

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Con el formulario lleno, activar el modo avión y tocar «Registrar producto» | «El producto no se registró · No se pudo conectar con el servidor. Revisa tu conexión a internet.» | Versión web · 12:00 p. m. · Formulario lleno con el botón «Guardando…» | [`capturas/P4-01-registro-sin-conexion.png`](capturas/P4-01-registro-sin-conexion.png) |
| 2 | Con el modo avión, volver al menú y deslizar para recargar | «Error al consultar el menú» con el mismo mensaje y «Reintentar» | ⏭️ No ejecutado (sin captura) | — |
| 3 | Quitar el modo avión y tocar «Reintentar» | La lista vuelve a cargar | Versión web · 6:34 p. m. · Lista de productos con los productos de prueba | [`capturas/P4-03-reintento-ok.png`](capturas/P4-03-reintento-ok.png) |
| 4 | Sin sesión, consultar y registrar con `curl` | `401` sin cuerpo en ambos | ✅ 2026-10-24 11:26 · `401 Unauthorized`, `Content-Length: 0` en ambos | [`P4-get-products-sin-sesion-401.txt`](respuestas/P4-get-products-sin-sesion-401.txt), [`P4-post-products-sin-sesion-401.txt`](respuestas/P4-post-products-sin-sesion-401.txt) |
| 5 | Con sesión de mesero, registrar con el script `scripts/endpoint-protegido.mjs` | Login `200`, consulta `200`, registro `403` | ✅ 2026-10-24 19:00:46–19:00:48 · login `200 OK` (rol `WAITER`, cookies redactadas), `GET /products` `200 OK` con 14 productos, `POST /products` **`403 Forbidden`**; el producto «Intento de mesero» no se creó | [`P4-login-mesero.txt`](respuestas/P4-login-mesero.txt), [`P4-get-products-mesero-200.txt`](respuestas/P4-get-products-mesero-200.txt), [`P4-post-products-mesero-403.txt`](respuestas/P4-post-products-mesero-403.txt) |

### P5 — Comportamiento de la autenticación

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Abrir la app sin sesión | Pantalla de login con «🔒 Conexión cifrada (HTTPS)» y la URL de la API | ✅ 7:07 p. m. · «RestoHub · App de meseros» con restaurante, correo y contraseña vacíos y «🔒 Conexión cifrada (HTTPS) app-callejon9-api-prod-3878c6.azurewebsites.net» | [`capturas/P5-01-login-https.jpeg`](capturas/P5-01-login-https.jpeg) |
| 2 | Entrar con una contraseña incorrecta | «No se pudo iniciar sesión · Restaurante, correo o contraseña incorrectos.» y el campo de contraseña vacío | ✅ 7:08 p. m. · Con `pruebas` y `mesero@pruebas.com`, aparece exactamente ese aviso y el campo de contraseña queda vacío. No revela cuál dato falló | [`capturas/P5-02-login-incorrecto.jpeg`](capturas/P5-02-login-incorrecto.jpeg) |
| 3 | Entrar como `admin@pruebas.com` con la contraseña correcta | «Mesas» con «Autenticación exitosa» | ✅ 8:27 p. m. · «Mesas · Administrador de sucursal · Calle…», «12 mesas · 11 libres», con los botones «Menú» y «Salir». El aviso «Autenticación exitosa» no aparece en la captura | [`capturas/P5-03-login-exitoso.jpeg`](capturas/P5-03-login-exitoso.jpeg) |
| 4 | Cerrar Expo Go por completo y volver a abrir la app | Entra directo, sin pedir credenciales | ⏭️ No ejecutado (sin captura) | — |
| 5 | «Salir» y entrar como `mesero@pruebas.com`; abrir «Menú» | El menú carga, pero **no** aparece «Nuevo producto» | ⏭️ No ejecutado (sin captura) | — |

**Script de P4 paso 5** (en tu terminal, no en el chat; la contraseña no se muestra ni se guarda):

```powershell
$s = Read-Host 'Contraseña de mesero@pruebas.com' -AsSecureString
$env:C9_TEST_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuth([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
node docs/practica-api-remota-movil/scripts/endpoint-protegido.mjs
Remove-Item Env:C9_TEST_PASSWORD
```

### Desviaciones

- **Capturas de la versión web.** Las capturas de P3.3, P4.1 y P4.3 corresponden a la versión web de RestoHub.
- **Productos creados desde la web.** En esas capturas se registraron «PRUEBA práctica API Agua de jamaica» ($45.50) y «PRUEBA práctica API Agua de Sandia» ($50.20). La captura que se pensó como «registro sin conexión» muestra «Guardando…» y el segundo producto sí quedó guardado. Ambos se desactivan al terminar.
- **Intento de login fallido por error al teclear.** Antes de P5.3 el ejecutor escribió mal la contraseña en el teléfono; la misma cuenta entró en la web, así que no es un defecto de la app. Ese comportamiento es el de P5.2.

## 8. Demostración breve

Grabación de pantalla del teléfono, de 2 a 3 minutos:

1. Login con HTTPS visible (P5).
2. Menú consultado del servidor (P1).
3. Formulario incompleto con sus avisos (P3).
4. Registro válido y el producto nuevo en la lista (P2).
5. Modo avión y «Reintentar» (P4).

## 9. Lista de entrega

| Evidencia pedida | Dónde |
|---|---|
| 1. Proyecto móvil actualizado y funcional | `mobile/`; P1–P5 |
| 2. Código fuente o repositorio | Rama `test/mobile-catalogo-api-remota` y su PR |
| 3. Capturas de la consulta y del registro | `capturas/P1-*`, `capturas/P2-*` |
| 4. API y endpoints utilizados | Secciones 2 y 3 |
| 5. Pruebas y medidas de seguridad | Secciones 5, 6 y 7 |
| 6. Demostración breve | Sección 8 |
