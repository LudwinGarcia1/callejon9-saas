# Notificaciones push: seguimiento de solicitudes

Práctica de Desarrollo móvil. Responde a la pregunta del caso de estudio: **¿cómo recibe una app móvil información de un servicio en la nube sin que el usuario tenga que abrirla para consultar?** La respuesta es un servicio push: el servidor no se conecta al teléfono, sino que entrega el mensaje a un intermediario (Expo → FCM/APNs) que mantiene un canal abierto con el sistema operativo del dispositivo y lo identifica por un **token**.

La funcionalidad se integró en la app existente `mobile/` (React Native 0.88 + Expo SDK 58, *RestoHub Meseros*). Funciona en **Android y iOS**, en la pantalla **Avisos**.

## 1. Servicio utilizado

| Alternativa | Qué es | Cuándo conviene |
|---|---|---|
| **Expo Push Notifications** ✅ | API HTTP de Expo (`exp.host/--/api/v2/push/send`) que recibe un *Expo push token* y reenvía a FCM (Android) o APNs (iOS). | Proyectos Expo: un solo token y un solo formato para las dos plataformas, sin SDK en el servidor. |
| Firebase Cloud Messaging (FCM) | Servicio de Google que entrega a Android (y a iOS vía APNs). Se usa con el token nativo del dispositivo y una cuenta de servicio. | Backends que ya usan Firebase o que necesitan funciones avanzadas (temas, analítica). |
| APNs directo | Servicio de Apple, solo iOS. Requiere certificado o llave `.p8`. | Apps solo iOS sin intermediario. |
| Backend propio | El servidor de la empresa decide cuándo avisar y llama a uno de los anteriores. | Producción: el evento real (cambio de estado) nace ahí. |

**Elegido: Expo Push Notifications**, porque la app ya es Expo, el mismo código obtiene un token válido en Android e iOS y el envío es una sola petición HTTP. FCM no se descarta: es el transporte que Expo usa por debajo en Android, y por eso una build de Android necesita el `google-services.json` del proyecto de Firebase.

## 2. Cómo funciona

```mermaid
sequenceDiagram
    autonumber
    participant App as App móvil (expo-notifications)
    participant Expo as Servicio push de Expo
    participant FCM as FCM / APNs
    participant Remoto as Servicio remoto (send-push.mts)

    App->>App: Pide permiso de notificaciones
    App->>Expo: getExpoPushTokenAsync(projectId)
    Expo-->>App: ExponentPushToken[…]
    Note over App,Remoto: El token se entrega al servicio remoto<br/>(en la práctica: se copia; en producción: POST al backend)
    Remoto->>Remoto: Ocurre el evento (SOL-001 pasa a "en proceso")
    Remoto->>Expo: POST /push/send {to, title, body, data}
    Expo-->>Remoto: Ticket {status: ok, id}
    Expo->>FCM: Reenvía al token nativo
    FCM->>App: Entrega al sistema operativo
    App->>App: Muestra la notificación / la usuaria la toca
```

Corresponde paso a paso al diagrama del funcionamiento esperado: servicio en la nube → generación del evento → servicio push (Expo/FCM) → token del dispositivo → envío → dispositivo → recepción → mostrar notificación.

### El token de notificaciones push

El token (`ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]`) es la **dirección** de una instalación concreta de la app en un dispositivo concreto. No identifica a la persona ni contiene datos: le dice al servicio push a qué teléfono y a qué app entregar el mensaje.

- Lo genera el servicio push cuando la app lo pide, y solo después de que el usuario concede el permiso.
- Cambia si se reinstala la app o se borran sus datos; por eso se obtiene en cada arranque y no se guarda como dato permanente.
- Quien conoce el token puede enviar notificaciones a ese dispositivo. Por eso se trata como dato sensible (ver §6).
- Si el servicio responde `DeviceNotRegistered`, el token dejó de servir y el servidor debe olvidarlo.

## 3. Configuración realizada

| Archivo | Cambio |
|---|---|
| `mobile/package.json` | Dependencias `expo-notifications` y `expo-device` (instaladas con `expo install` para la versión del SDK) y script `push:send`. |
| `mobile/app.json` | Plugin `expo-notifications` (color e ícono del canal por defecto `solicitudes`), `android.package` e `ios.bundleIdentifier` = `com.callejon9.meseros`, necesarios para la build nativa. |
| `mobile/app.config.ts` | Agrega en tiempo de build el `projectId` de EAS (`EXPO_PUBLIC_EAS_PROJECT_ID`) y el `google-services.json` (`GOOGLE_SERVICES_JSON`) si existe. Ninguno se versiona. |
| `mobile/.env.example` | Documenta las dos variables nuevas. |
| `mobile/.gitignore` | Ignora `google-services.json` y `GoogleService-Info.plist`. |

### Código

| Archivo | Responsabilidad |
|---|---|
| `src/notifications/messages.ts` | Contrato compartido: estados, textos, formato del mensaje y validación de los datos recibidos. TypeScript puro, sin React Native. |
| `src/notifications/push.ts` | Permisos, canal de Android, token, presentación en primer plano y suscripción a recepción y toque. |
| `src/screens/NotificationsScreen.tsx` | Estado del permiso, token, detalle de la notificación tocada y lista de recibidas. |
| `App.tsx` | Registra el dispositivo al iniciar sesión y navega a **Avisos** cuando se toca una notificación. |
| `scripts/send-push.mts` | Servicio remoto mínimo que envía la notificación a la API de Expo. |
| `src/test/notifications.test.ts` | 16 pruebas unitarias del formato, la validación y la fecha. |

### Eventos del caso de estudio

| Estado | `eventType` | Título | Mensaje |
|---|---|---|---|
| `REGISTERED` | `REGISTERED` (registro) | Solicitud registrada | Tu solicitud fue registrada correctamente |
| `RECEIVED` | `STATUS_CHANGED` | Actualización de solicitud | Tu solicitud fue recibida por el personal responsable |
| `IN_PROGRESS` | `STATUS_CHANGED` | Actualización de solicitud | Tu solicitud se encuentra en proceso |
| `COMPLETED` | `STATUS_CHANGED` | Actualización de solicitud | Tu solicitud ha finalizado |

### Mensaje enviado

El ejemplo de la práctica (destinatario, título, mensaje, tipo de evento, identificador) se traduce así:

```json
{
  "to": "ExponentPushToken[…]",
  "title": "Actualización de solicitud",
  "body": "Tu solicitud se encuentra en proceso",
  "data": { "eventType": "STATUS_CHANGED", "status": "IN_PROGRESS", "requestId": "SOL-001" },
  "sound": "default",
  "priority": "high",
  "channelId": "solicitudes",
  "ttl": 3600
}
```

`title` y `body` son lo que muestra el sistema; `data` viaja oculto y es lo que la app lee para saber qué solicitud y qué evento son.

## 4. Recepción en la app

1. **Permisos.** Al iniciar sesión, `registerForPushNotifications()` crea el canal de Android `solicitudes` (Android 13+ solo muestra el diálogo si existe un canal), consulta el permiso y lo pide solo si el sistema todavía lo permite.
2. **Token.** Con permiso, pide el token a Expo. Si el entorno no lo permite (simulador de iOS, Expo Go en Android, falta `projectId`), la pantalla **Avisos** explica el motivo en lugar de fallar. Si el usuario concede el permiso desde Ajustes, la app lo detecta al volver a primer plano y se registra sola.
3. **Recepción.** `setNotificationHandler` hace que una notificación que llega con la app abierta también se muestre como banner; `addNotificationReceivedListener` la agrega a la lista de **Avisos**.
4. **Contenido.** Cada aviso muestra título, mensaje, estado, identificador y hora. La hora se normaliza con `toEpochMillis`: iOS la entrega en segundos y Android en milisegundos.
5. **Al tocarla.** `addNotificationResponseReceivedListener` (o `getLastNotificationResponse` si la app estaba cerrada) abre la pantalla **Avisos** con la notificación seleccionada y su detalle: título, mensaje, tipo de evento, estado, identificador y hora. Si no hay sesión, primero se pide el login y después se abre.

| Estado de la app | Qué ocurre al llegar | Qué ocurre al tocarla |
|---|---|---|
| Primer plano | Banner + se agrega a la lista | Se selecciona y muestra el detalle |
| Segundo plano | El sistema la muestra en la bandeja | La app vuelve al frente en **Avisos** con el detalle |
| Cerrada | El sistema la muestra en la bandeja | La app arranca, y tras la sesión abre **Avisos** con el detalle |
| Sin permiso | El sistema no la muestra | — (la app sigue funcionando; la pantalla ofrece pedir permiso o abrir ajustes) |

## 5. Cómo reproducir

### Preparación (una vez)

1. Proyecto de Expo: `cd mobile; pnpm dlx eas-cli init --force`. Guarda el `projectId` en `app.json` (`extra.eas.projectId`); ya está hecho para `@octavioduarte/callejon9-mobile`, así que otro integrante solo necesita acceso a ese proyecto. `EXPO_PUBLIC_EAS_PROJECT_ID` permite usar otro.
2. **Firebase (Android):** crea un proyecto en el plan **Spark** (sin costo; no actives Blaze), agrega la app Android `com.callejon9.meseros` y descarga `google-services.json` a `mobile/` (ignorado por git; `app.config.ts` lo toma solo).
3. **Llave FCM V1:** en Firebase, *Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada*. Súbela en expo.dev → *Credentials* → Android → `com.callejon9.meseros` → *FCM V1 service account key*. Si el asistente web exige un keystore, sirve el de depuración (`android/app/debug.keystore`, contraseñas `android`, alias `androiddebugkey`); no sirve para publicar en Play Store. Sin esta llave, Expo responde `InvalidCredentials`.
4. **Android:** construye una development build e instálala en un teléfono o en un emulador con Google Play. Expo Go en Android **no** recibe push remotos desde el SDK 53.

   ```powershell
   cd mobile
   pnpm exec expo run:android      # o: pnpm dlx eas-cli build --profile development --platform android
   ```

5. **iOS (sin costo):** instala **Expo Go** del App Store en el iPhone, conéctalo a la misma Wi-Fi que la computadora y abre `exp://<IP-de-la-PC>:8081` desde Safari, con Metro corriendo (`pnpm exec expo start --go --lan`). Expo Go usa sus propias credenciales de APNs, así que no hace falta cuenta de Apple Developer. Requisitos que encontramos en la prueba:
   - El SDK del proyecto debe coincidir con el de Expo Go del App Store (por eso el proyecto pasó al SDK 58).
   - Una VPN o un filtro de DNS en el iPhone (por ejemplo AdGuard) impide que Expo Go llegue a la computadora; desactívalo durante la prueba.
   - En Expo Go, la notificación aparece con el ícono de Expo Go y el permiso lo pide "Expo Go".
   - Una build propia de iOS sí requiere Xcode y la cuenta de Apple Developer (99 USD al año) para la llave de APNs.

### Envío

Inicia sesión, abre **Avisos** y copia el token (botón *Compartir token* o la línea `[push] token del dispositivo` en la terminal de Metro). Luego:

```powershell
cd mobile
$env:EXPO_PUSH_TOKEN = "ExponentPushToken[…]"
pnpm push:send --status REGISTERED  --id SOL-001 --receipt
pnpm push:send --status IN_PROGRESS --id SOL-001 --receipt
pnpm push:send --status COMPLETED   --id SOL-001 --dry-run   # solo imprime el mensaje
```

El script valida el token, el estado y el identificador antes de enviar, imprime el mensaje con el token enmascarado, muestra el ticket de Expo y, con `--receipt`, el recibo de entrega.

## 6. Medidas de seguridad

- **Permiso explícito.** La app solo pide permiso si el sistema lo permite, nunca insiste; si se niega, el resto de la app sigue funcionando y se ofrece abrir los ajustes.
- **Datos validados al recibir.** Cualquiera con el token podría enviar un push, así que la app no confía en el contenido: `parseRequestNotificationData` solo acepta estados de una lista cerrada e identificadores `SOL-\d{3,8}`, descarta notificaciones que no cumplan y no copia campos extra (por ejemplo, una URL). La app nunca abre enlaces ni ejecuta acciones a partir del payload. Límite comprobado en D6: Android sí muestra el banner de un mensaje alterado, porque el SDK de Firebase lo pinta sin consultar a la app; la defensa real contra remitentes ajenos es la seguridad reforzada de Expo (siguiente punto).
- **Sin datos personales en el mensaje.** El push solo lleva un identificador y un estado. El contenido pasa por servidores de Expo, Google y Apple, así que nombres, teléfonos o direcciones nunca viajan en él.
- **Pantalla de bloqueo privada.** El canal de Android usa visibilidad `PRIVATE`: con bloqueo seguro, el sistema oculta el texto de la notificación.
- **Token como dato sensible.** Se muestra completo solo en builds de desarrollo; en producción se enmascara. El script lo enmascara al imprimir.
- **Envío autenticado.** El script admite `EXPO_ACCESS_TOKEN` (cabecera `Authorization: Bearer`) para activar *Enhanced Security for Push Notifications* en expo.dev; con ella, conocer el token ya no basta para enviar. El access token solo se lee del entorno, nunca de argumentos ni de archivos versionados.
- **Credenciales fuera del repositorio.** `google-services.json`, llaves `.p8`/`.p12` y `.env` están en `.gitignore`.
- **HTTPS en todo el trayecto.** La app → Expo, el script → Expo y Expo → FCM/APNs son conexiones TLS.
- **Vigencia corta.** `ttl: 3600`: un aviso de estado que no se pudo entregar en una hora se descarta en vez de llegar desfasado.
- **Buzón por sesión.** Al cerrar sesión se borran las notificaciones mostradas en **Avisos**.

## 7. Matriz de pruebas

Las pruebas unitarias y de compilación se ejecutaron en esta rama.

### Android

D0 a D6 se ejecutaron el 2026-11-08 con el SDK 57 y D7 repite el flujo después de actualizar al SDK 58, en un emulador Android (API 37, imagen con Google Play) con la build de desarrollo `com.callejon9.meseros`, conectada al backend de producción en Azure con la cuenta `mesero@pruebas.com`. Las capturas están en [`capturas/`](capturas/).

El envío se hizo desde la Mac con `pnpm push:send` a través del servicio de Expo, con el proyecto de EAS `@octavioduarte/callejon9-mobile` y el proyecto de Firebase `restohub-push` (plan Spark, sin costo). Las salidas del script, con el ticket y el recibo de entrega, están en [`respuestas/`](respuestas/), con el token enmascarado.

| # | Prueba | Cómo | Resultado esperado | Resultado | Evidencia |
|---|---|---|---|---|---|
| U1 | Formato y validación | `pnpm test` | 69/69 pruebas pasan (16 nuevas de notificaciones) | ✅ Pasó | Salida de vitest |
| U2 | Tipos | `pnpm exec tsc --noEmit` | Sin errores | ✅ Pasó | — |
| U3 | Bundles | `pnpm exec expo export --platform android` y `--platform ios` | Compilan | ✅ Pasó | — |
| U5 | Dependencias del SDK | `pnpm dlx expo-doctor` | 20/20 comprobaciones | ✅ Pasó | — |
| U4 | Mensaje del servicio remoto | `pnpm push:send --status IN_PROGRESS --id SOL-001 --dry-run` | Imprime el JSON de §3 | ✅ Pasó | — |
| D0 | Sesión contra Azure | Iniciar sesión con `mesero@pruebas.com` en el restaurante `pruebas` | Lista de mesas del backend en la nube | ✅ Pasó | `C00-P01-sesion-azure.png` |
| D1a | Solicitud de permiso | Primer inicio de sesión | Android muestra el diálogo *Allow RestoHub Meseros to send you notifications?* | ✅ Pasó | `C01-P01-solicitud-permiso.png` |
| D1b | Token cuando el entorno no lo permite | Conceder el permiso sin `projectId` de EAS | Banner *Push remoto no disponible* con el motivo, sin error | ✅ Pasó | `C01-P02-permiso-concedido.png` |
| D1c | Token | Conceder el permiso con `projectId` y FCM configurados | Banner *Dispositivo registrado* y token visible | ✅ Pasó | `C01-P03-token.png` |
| D2 | Solicitud registrada | `pnpm push:send --status REGISTERED --id SOL-001 --receipt` con la app en segundo plano | Llega *Solicitud registrada* a la bandeja | ✅ Pasó (ticket y recibo `ok`) | `C02-P01-registrada-bandeja.png`, `respuestas/C02-envio-registrada.txt` |
| D3 | Cambio de estado | `pnpm push:send --status IN_PROGRESS --id SOL-001` con la app abierta en **Avisos** | Banner y aparece en la lista como *En proceso* | ✅ Pasó | `C03-P01-en-proceso-banner.png`, `C03-P02-en-proceso-lista.png`, `respuestas/C03-envio-en-proceso.txt` |
| D4a | Tocar la notificación (segundo plano) | Tocar la notificación de D2 en la bandeja | Abre **Avisos** con el detalle: *Registro de solicitud*, *Registrada*, *SOL-001* | ✅ Pasó | `C04-P01-detalle-segundo-plano.png` |
| D4b | Tocar la notificación (app cerrada) | Terminar el proceso de la app (`am kill`), enviar `COMPLETED --receipt` y tocarla | La app arranca, recupera la sesión y abre **Avisos** con *Cambio de estado*, *Finalizada*, *SOL-001* | ✅ Pasó | `C04-P02-finalizada-app-cerrada.png`, `C04-P03-detalle-app-cerrada.png`, `respuestas/C04-envio-finalizada.txt` |
| D5a | Sin permiso | Tocar *Don't allow* y abrir **Avisos** | Banner *Permiso denegado* y botón *Volver a pedir permiso*; el resto de la app sigue funcionando | ✅ Pasó | `C05-P01-permiso-denegado.png` |
| D5b | Sin permiso, rechazo definitivo | Volver a pedir y negar por segunda vez | Android ya no muestra el diálogo; la app ofrece *Abrir ajustes del sistema* | ✅ Pasó | `C05-P02-denegado-permanente.png` |
| D6 | Payload no válido | `POST` directo a la API de Expo, sin el script, con `status: "DELETED"`, `requestId: "<script>…"` y una `url` extra | Android muestra el banner (lo pinta el SDK de Firebase antes de consultar la app), pero la app no lo agrega a **Avisos** ni lo abre | ✅ Pasó | `C06-P01-payload-invalido-banner.png`, `C06-P02-payload-invalido-descartado.png`, `respuestas/C06-envio-payload-invalido.txt` |
| D7 | Regresión tras pasar al SDK 58 | Recompilar la build de Android con el SDK 58, abrir **Avisos** y enviar `IN_PROGRESS` a `SOL-002` | Sesión de Azure, token igual que antes (pedido una sola vez) y aviso en la lista con la hora correcta | ✅ Pasó | `C07-P01-regresion-sdk58.png`, `respuestas/C07-envio-regresion-sdk58.txt` |

### iOS

Ejecutadas el 2026-11-08 en un iPhone físico con **Expo Go (SDK 58)**, conectado al mismo backend de Azure y con el mismo script de envío; solo cambia el token. Las capturas están en [`capturas/ios/`](capturas/ios/) y se tomaron con Duplicación del iPhone, salvo `C02-P01`, que es una captura del teléfono recortada para mostrar solo las notificaciones. Duplicación del iPhone no muestra los banners ni el Centro de notificaciones, por eso la recepción en segundo plano se documenta con esa captura.

| # | Prueba | Cómo | Resultado esperado | Resultado | Evidencia |
|---|---|---|---|---|---|
| I0 | App en el iPhone | Abrir `exp://<IP>:8081` en Expo Go | Pantalla de login conectada a Azure por HTTPS | ✅ Pasó | `ios/C00-P00-login-expo-go.png` |
| I1a | Solicitud de permiso | Iniciar sesión con `mesero@pruebas.com` | iOS muestra *Expo Go quiere enviarte notificaciones* | ✅ Pasó | `ios/C01-P01-solicitud-permiso.png` |
| I5 | Sin permiso | Tocar *No permitir* y abrir **Avisos** | iOS solo pregunta una vez: banner *Permiso denegado* y botón *Abrir ajustes del sistema* | ✅ Pasó | `ios/C05-P01-permiso-denegado.png` |
| I1b | Permiso desde Ajustes y token | Activar las notificaciones de Expo Go en Ajustes y volver a la app | La app se registra sola al volver y muestra el token | ✅ Pasó | `ios/C01-P02-token.png` |
| I2 | Solicitud registrada y cambio de estado en segundo plano | `push:send --status REGISTERED` y `--status IN_PROGRESS` con la app en segundo plano | Ambas llegan al Centro de notificaciones | ✅ Pasó (recibo `ok`) | `ios/C02-P01-centro-notificaciones.png`, `respuestas/ios-C02-envio-en-proceso.txt` |
| I3 | Cambio de estado con la app abierta | `push:send --status RECEIVED` con **Avisos** abierto | Entra en la lista como *Recibida*, con la hora correcta | ✅ Pasó | `ios/C03-P01-recibida-app-abierta.png`, `respuestas/ios-C03-envio-recibida.txt` |
| I4 | Tocar la notificación con la app cerrada | Cerrar Expo Go, enviar `COMPLETED` y tocarla en la pantalla bloqueada | Abre **Avisos** con *Cambio de estado · Finalizada · SOL-001* | ✅ Pasó | `ios/C04-P01-detalle-app-cerrada.png`, `respuestas/ios-C04-envio-finalizada.txt` |
| I6 | Payload no válido | `POST` directo con `status: "DELETED"` y `requestId: "<script>…"` | La app no lo agrega a **Avisos** | ✅ Pasó | `ios/C06-P01-payload-invalido-descartado.png`, `respuestas/ios-C06-envio-payload-invalido.txt` |

Defectos encontrados en iOS y corregidos en la misma rama: la hora salía como enero de 1970 (iOS entrega segundos; ahora se normaliza y está cubierto por pruebas) y, tras conceder el permiso en Ajustes, la app no se enteraba hasta reiniciarse (ahora se vuelve a registrar al pasar a primer plano).

No marques una prueba D como pasada sin su captura: la regla de [`docs/pruebas/README.md`](../README.md) aplica también aquí.
