# Guion del video demostrativo · revisión final

Requisito 2.4 de la revisión del proyecto integrador: un video con el funcionamiento del sistema, sus procesos de negocio principales y las funcionalidades implementadas, grabado **sobre la versión desplegada**. El video complementa la demostración en vivo; no la sustituye.

Duración objetivo: 9 a 11 minutos. Se graba con pantalla compartida y la cámara encendida de quien narra. Narran los tres expositores de la presentación; el cuarto integrante opera el teléfono.

| Quién | Papel en el video |
|---|---|
| Octavio (Scrum Master) | Apertura, cierre y la app de comensales |
| Líder técnico | Recorrido del restaurante: administración, mesero, cocina y caja |
| Desarrollador del bloque 3 | Aislamiento entre restaurantes, nube y monitoreo |
| Cuarto integrante | Opera los teléfonos con las apps y avisa si algo falla |

Asignen nombres a los dos papeles técnicos antes de grabar y escríbanlos aquí.

---

## Antes de grabar · con 30 minutos de margen

### 1. Entorno

- [ ] La API responde: `https://app-callejon9-api-prod-70af93.azurewebsites.net/actuator/health` devuelve `{"status":"UP",…}`. Si el plan se apagó para ahorrar crédito: `.\scripts\azure-backend-power.ps1 -Action Start` (grupo `rg-callejon9-prod-cus-001`).
- [ ] La web abre: `https://restohub.azurewebsites.net/login`. La web está en plan F1: si estuvo inactiva, la primera carga tarda. Ábranla 5 minutos antes.
- [ ] Las cuentas de prueba del restaurante `pruebas` (`admin@`, `mesero@`, `cocina@` y `caja@pruebas.com`) usan la contraseña del secreto `demo-password`:

  ```sh
  az keyvault secret show --vault-name kv-c9-prod-70af93 -n demo-password --query value -o tsv
  ```

  **Nunca se muestra en pantalla.** Inicien sesión antes de grabar, cada rol en su propia ventana del navegador (o en ventanas privadas distintas).

### 2. Datos del restaurante `pruebas`

- [ ] **Turno de caja abierto** con la cuenta `caja@pruebas.com` en `/cash-sessions`. Sin turno, el cobro responde `422`.
- [ ] Al menos una mesa libre para el pedido del comensal y otra para el del mesero.
- [ ] Los datos de demostración ya traen ventas, comandas en cocina en distintos estados, comandas abiertas y una cancelada.

### 3. Teléfonos

- [ ] **App de clientes**: instalar el APK de release (`mobile-clientes/android/app/build/outputs/apk/release/app-release.apk`, generado el 27 de noviembre contra la API nueva). Abrirla una vez con red para que guarde el menú.
- [ ] **App de meseros**: build de desarrollo o Expo Go, con `EXPO_PUBLIC_API_URL` apuntando a la API nueva. Iniciar sesión como `mesero@pruebas.com`.
- [ ] Imprimir o mostrar en otra pantalla el QR de una mesa: en la web, **Administración → Mesas → Imprimir QR**.
- [ ] Modo avión a mano para la escena sin conexión.

### 4. Pestañas abiertas

- [ ] Portal de Azure en el grupo `rg-callejon9-prod-cus-001` (API, PostgreSQL, Key Vault, Application Insights).
- [ ] Sentry: `https://restohub.sentry.io/issues/` con el entorno `production`.
- [ ] Linear: el ciclo actual.
- [ ] GitHub: la pestaña **Actions** con la última corrida en verde de `main`.

---

## Escenas

### 1. Apertura · Octavio · 45 s

- Quiénes son, qué es RestoHub y la tesis en una frase: el aislamiento entre restaurantes lo impone PostgreSQL, no el código.
- Qué se va a ver: una comanda de punta a punta y la prueba de que otro restaurante no ve nada.
- **En pantalla:** la portada de la presentación y luego `restohub.azurewebsites.net/login`.

### 2. Alta de un restaurante nuevo · Líder técnico · 1 min

- En `/signup`, dar de alta el restaurante `demo-video` con un plan. Explicar que el plan fija límites de usuarios y mesas que el sistema aplica de verdad.
- Este restaurante se usa en la escena 8 para demostrar el aislamiento.
- **En pantalla:** el formulario de alta y la entrada al panel del restaurante nuevo, vacío.

### 3. Administración del restaurante `pruebas` · Líder técnico · 1 min

- Como `admin@pruebas.com`: carta con categorías y productos, mesas y personal por rol (administración, mesero, cocina y caja).
- Mostrar que desactivar un producto lo saca de la carta sin borrarlo: el historial lo sigue referenciando.
- **En pantalla:** `/admin`.

### 4. El comensal pide desde su mesa · Octavio · 1.5 min

- Escanear con la app de clientes el QR de una mesa. Se abre el menú del restaurante.
- Armar un pedido y enviarlo a cocina: aparece el folio.
- **Sin conexión:** activar el modo avión, armar otro pedido y enviarlo. La app avisa que lo guardó. Quitar el modo avión: el pedido sale solo y llega el folio.
- **En pantalla:** el teléfono, con la cámara o con su pantalla compartida.

### 5. El mesero toma una comanda · Líder técnico · 1 min

- En la app de meseros: abrir una mesa libre, agregar productos con notas y enviar a cocina.
- **En pantalla:** el teléfono del mesero.

### 6. Cocina en tiempo real · Líder técnico · 1 min

- En la web como `cocina@pruebas.com`: las comandas de las escenas 4 y 5 aparecen **sin recargar la página**.
- Avanzar productos de pendiente a en preparación y a listo. El semáforo muestra cuánto lleva cada comanda.
- **En pantalla:** `/kitchen`.

### 7. Cobro y ticket · Líder técnico · 1.5 min

- En la web como `caja@pruebas.com`: cobrar la comanda del mesero con **propina y pago dividido** (efectivo y tarjeta). Mostrar el cambio calculado.
- Descargar el ticket en PDF.
- Mostrar que la orden cobrada ya no acepta cambios y que la mesa quedó libre.
- Cerrar con el historial de ventas (`/history`) y la analítica (`/analytics`).
- **En pantalla:** `/cashier`, el PDF y `/history`.

### 8. Aislamiento entre restaurantes · Desarrollador del bloque 3 · 1 min

- Iniciar sesión en el restaurante `demo-video` de la escena 2: no ve ninguna mesa, comanda ni venta de `pruebas`.
- Explicar en una frase por qué: la cookie firmada fija el restaurante, la API lo publica en PostgreSQL en cada transacción y las políticas RLS de las 17 tablas filtran las filas, aunque el código tuviera un error.
- Mencionar la prueba automatizada `TenantIsolationTest`, que lo demuestra en cada pull request.
- **En pantalla:** el panel vacío de `demo-video` junto al de `pruebas`.

### 9. Nube y monitoreo · Desarrollador del bloque 3 · 1.5 min

- Portal de Azure: App Service de la web y de la API, PostgreSQL Flexible Server, Key Vault e Application Insights. Decir que todo se crea con Terraform.
- Contar la recuperación del 27 de noviembre: la suscripción original se quedó sin crédito y la API se recreó con el mismo Terraform en otra suscripción y región.
- Sentry: un error real de producción con su traza, el entorno `production` y la versión del commit; una traza que une el navegador con la API; la alerta de issue nuevo en producción.
- GitHub Actions: la corrida en verde con sus cinco trabajos.
- **En pantalla:** portal de Azure, Sentry y Actions.

### 10. Cierre · Octavio · 30 s

- Qué quedó entregado y qué sigue en curso: el cobro recurrente con Mercado Pago (CAL-29), comprometido para el 11 de diciembre.
- Repetir la tesis y agradecer.

---

## Lo que no se muestra y por qué

- **Contraseñas, tokens y el DSN de Sentry:** nunca en pantalla.
- **Llamar al mesero:** ya avisa en tiempo real a la vista de mesas del mesero, con un aviso emergente y la mesa marcada «Llama al mesero». Se muestra solo si el cambio ya está en `main` y desplegado; si no, sigue fuera del video.
- **Mercado Pago:** está en curso (CAL-29). Si ya está listo cuando se grabe, agregar una escena entre la 2 y la 3 con la suscripción en el sandbox.
- **Datos personales reales:** todas las cuentas y restaurantes son de prueba.

## Después de grabar

- [ ] Borrar el restaurante `demo-video` o dejarlo documentado como dato de prueba.
- [ ] Revisar en Sentry que los errores provocados para la escena 9, si los hubo, queden resueltos.
- [ ] Subir el video y pegar la URL en la plataforma. Responsable de subirlo: [nombre].
