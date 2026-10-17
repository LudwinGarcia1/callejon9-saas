# Callejón 9 · App de meseros (Expo)

Cliente móvil del SaaS Callejón 9. Toma el flujo del mesero —ver mesas, capturar un pedido y enviarlo— y lo conecta al mismo backend Spring Boot que usa la versión web. No guarda datos locales ni simulados: todo viene de la API `/api/v1`.

## Qué hace

| Parte | Pantalla | Petición |
|---|---|---|
| Autenticación remota | `LoginScreen` | `POST /api/v1/auth/login` (restaurante, correo, contraseña) |
| Consumo de datos | `TablesScreen`, `OrderScreen` | `GET /api/v1/tables`, `GET /api/v1/products` |
| Envío de datos | `OrderScreen`, `ResultScreen` | `POST /api/v1/orders`, `POST /api/v1/orders/{id}/items`, `POST /api/v1/orders/{id}/send-to-kitchen` |
| Consulta del catálogo | `ProductsScreen` | `GET /api/v1/products`, `GET /api/v1/categories` |
| Registro de productos (solo ADMIN) | `ProductFormScreen` | `POST /api/v1/products` (nombre y precio obligatorios) |

## Dónde está cada cosa

- `src/config.ts`: URL del servidor y regla de solo HTTPS.
- `src/api/client.ts`: transporte único con tiempo límite, renovación de sesión y traducción de errores HTTP a mensajes.
- `src/api/auth.ts`: login, sesión actual y logout.
- `src/api/waiter.ts`: mesas, menú y pedidos.
- `src/api/catalog.ts`: categorías y registro de productos.
- `src/screens/`: las seis pantallas.

## Seguridad

- **Solo HTTPS.** La app se niega a arrancar contra una URL `http://`. La excepción `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=true` solo aplica en desarrollo.
- **El token no toca JavaScript.** El backend emite el JWT en cookies `httpOnly`, `Secure` y `SameSite=Strict`. El almacén de cookies nativo (OkHttp en Android, NSHTTPCookieStorage en iOS) las guarda y las adjunta solo.
- **Sesión corta y renovable.** El access token dura 15 minutos. Ante un 401 la app llama una sola vez a `POST /auth/refresh`, que rota el refresh token. Si la renovación falla, la app vuelve al login.
- **La contraseña se borra del formulario** en cuanto se envía. Siempre se captura con `secureTextEntry`.
- **No hay enumeración de cuentas.** El backend responde 401 sin cuerpo cuando el usuario no existe, cuando la contraseña es incorrecta y cuando el restaurante no coincide, y la app muestra un solo mensaje para los tres casos.
- **Freno a fuerza bruta.** Tras 5 fallos por cuenta o 20 por IP en 15 minutos, el backend responde 429 y la app muestra el tiempo de espera.
- **Aislamiento entre restaurantes.** La app nunca envía el restaurante en las peticiones de datos: el backend lo toma del JWT firmado y PostgreSQL lo impone con RLS.
- **Autorización por rol.** Solo entran `WAITER` y `ADMIN`, y el backend vuelve a exigirlo en cada endpoint (`@PreAuthorize`).

## Ejecutar

Requisitos: Node 20+, pnpm y la app **Expo Go** en el teléfono.

```powershell
cd mobile
pnpm install
Copy-Item .env.example .env   # y edita EXPO_PUBLIC_API_URL
pnpm start                    # escanea el QR con Expo Go
```

### Exponer el backend por HTTPS

El backend corre en `http://localhost:8080`. Para que el teléfono llegue a él por una conexión cifrada, publícalo con un túnel HTTPS, por ejemplo:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Pon la URL `https://….trycloudflare.com` que imprime en `EXPO_PUBLIC_API_URL`. Con HTTPS, el backend puede emitir las cookies con `Secure` (el valor por defecto, `AUTH_COOKIE_SECURE=true`).

Solo para pruebas en la red local, sin cifrado:

```
EXPO_PUBLIC_API_URL=http://192.168.x.x:8080
EXPO_PUBLIC_ALLOW_INSECURE_HTTP=true
```

En ese caso el backend debe arrancar con `AUTH_COOKIE_SECURE=false` (`scripts/run-dev.ps1` ya lo hace); si no, las cookies `Secure` no se envían por HTTP.

### Credenciales de prueba

`scripts/seed-demo.ps1` crea un restaurante con el usuario `mesero@<slug>.com`. La contraseña es la del script; no la escribas en la app ni en capturas.

## Verificación

```powershell
pnpm exec tsc --noEmit
pnpm exec expo export --platform android   # comprueba que el bundle compila
```
