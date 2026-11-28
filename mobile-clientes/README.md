# Callejón 9 · App de comensales (Expo)

App Android para los clientes del restaurante: ver el menú, armar un pedido para su mesa, enviarlo a cocina y llamar al mesero. Funciona **sin conexión** una vez que el menú se descargó la primera vez.

Usa solo las rutas públicas del autoservicio por QR (`PublicMenuController`), las mismas que la vista web `/m/{slug}/{mesa}`. No inicia sesión, no guarda cookies y no pide datos personales.

| Acción | Petición | Sin conexión |
|---|---|---|
| Ver el menú | `GET /api/v1/public/{slug}/catalog` | Muestra la última copia guardada y la fecha en que se descargó |
| Enviar el pedido | `POST /api/v1/public/{slug}/tables/{mesa}/orders` | Se guarda en el teléfono y se envía solo al volver la red |
| Llamar al mesero | `POST /api/v1/public/{slug}/tables/{mesa}/call-waiter` | Deshabilitado: un aviso tardío no sirve |

## Cómo funciona sin conexión

- **Menú.** Cada descarga se guarda en AsyncStorage (`catalog:{slug}`). Si la red falla o el servidor no responde a tiempo, se usa la copia guardada. Un 404 la borra porque el restaurante ya no existe. La primera vez sí se necesita conexión.
- **Carrito.** Se guarda por restaurante y mesa (`cart:{slug}:{mesa}`). Al llegar un menú nuevo se quitan los productos que ya no se venden y se actualizan los precios.
- **Bandeja de salida.** Los pedidos viven en `outbox`. La app los envía en cuanto NetInfo detecta red de nuevo, al volver al primer plano o cuando el comensal desliza para actualizar.
- **Sin duplicados.** El reintento automático solo ocurre cuando la petición no llegó al servidor (error de red). Si el servidor tarda demasiado, el pedido pudo haberse registrado: queda **sin confirmar** y el comensal decide si reenviarlo después de preguntarle al mesero.
- **Pedidos viejos.** Un pedido guardado hace más de 30 minutos (`OUTBOX_AUTO_SEND_WINDOW_MS`) no se envía solo, porque el comensal pudo haberse ido. Queda como **vencido** y la app pide confirmación.
- **Rechazos.** Si el backend rechaza el pedido (por ejemplo, porque un producto se agotó o la mesa está inactiva), el carrito se conserva para corregirlo y se muestra el motivo.
- **Precios.** El total que ve el comensal es estimado: el backend cobra el precio vigente cuando recibe el pedido.
- **Pedidos enviados.** Cuando el servidor confirma un pedido, se guarda en `history` con el folio, la hora y las líneas que pidió esa persona. "Mi pedido" los muestra por mesa, también sin conexión. Se guarda lo del comensal, no la orden completa, porque el backend junta en una sola orden lo que pide toda la mesa. Se conservan las últimas 24 horas, con un máximo de 30 pedidos. No hay estado en vivo de cocina: la API pública no lo expone.

## Entrar a una mesa

La forma principal es **escanear el QR de la mesa** con la cámara (`ScannerScreen`, con `expo-camera`). Es el mismo QR que imprime el administrador desde la web (`https://…/m/{slug}/{mesa}`), así que quien no tenga la app lo abre en el navegador. La cámara solo se usa mientras la pantalla de escaneo está abierta y no se guarda ninguna imagen; el micrófono está bloqueado.

También se puede escribir el código del restaurante (slug) y el número de mesa, o pegar el enlace del QR. La app abre además enlaces `restohub://m/{slug}/{mesa}` y recuerda la última mesa usada.

Escanear funciona sin conexión si el menú de ese restaurante ya está guardado en el teléfono.

Permisos de Android: `INTERNET`, `ACCESS_NETWORK_STATE` y `CAMERA`.

## Errores en Sentry

Con `EXPO_PUBLIC_SENTRY_DSN` en `.env` la app reporta sus errores de JavaScript y sus fallos nativos al proyecto `restohub-clientes` de Sentry; sin esa variable no envía nada. Después de cambiar `.env`, reinicia Metro con `--clear`. Una build nativa de release sin `SENTRY_AUTH_TOKEN` necesita `SENTRY_DISABLE_AUTO_UPLOAD=true`. Detalle en [`docs/operacion-sentry.md`](../docs/operacion-sentry.md).

## Dónde está cada cosa

- `src/config.ts`: URL del servidor (solo HTTPS) y ventana de envío automático.
- `src/api/`: cliente HTTP sin credenciales y rutas públicas.
- `src/offline/`: almacén, copia del menú y bandeja de salida.
- `src/domain/`: carrito y lectura del enlace de la mesa.
- `src/screens/`: mesa, escaneo del QR, menú y pedido.

## Ejecutar y generar el APK

Requisitos: Node 20 o superior, pnpm, JDK 17 o superior y el SDK de Android (`ANDROID_HOME`).

```bash
cd mobile-clientes
pnpm install
cp .env.example .env          # y edita EXPO_PUBLIC_API_URL (https://)
pnpm test
pnpm exec tsc --noEmit
pnpm apk                      # prebuild + gradlew assembleRelease
```

El APK queda en `android/app/build/outputs/apk/release/app-release.apk`. La URL del servidor se incrusta al compilar: cambiar `.env` exige volver a generar el APK.

Por ahora el APK de release se firma con la llave de depuración que genera Expo. Sirve para instalarlo directamente en un teléfono, pero no para publicarlo en Google Play: para eso hace falta un keystore propio que nunca se versiona (`*.jks` está en `.gitignore`).
