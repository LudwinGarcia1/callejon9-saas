# Guion de la demostración de la UAT

Recorrido de los 13 escenarios de aceptación (CAL-117) contra producción en Azure, en el orden en que trabaja un restaurante: alta, preparación, servicio, cobro y cierre del día. Dura unos 25 minutos.

Sustituye a [`guion-demo.md`](guion-demo.md) cuando se presente la UAT: ese guion es del 31 de julio y describe una demostración local.

## Cómo presentarla con honestidad

Al 2026-11-19, en Linear, los escenarios están así:

| Estado | Escenarios |
|---|---|
| Aceptado en ensayo | UAT-01, 04, 06, 07, 10, 11 y 13 |
| En progreso | UAT-02, 03, 05 y 08 |
| Sin ejecutar | UAT-09 (cobro) y UAT-12 (ventas y analítica) |

Los cierres de UAT-01, 06, 07 y 13 vienen de ensayos que corrió el equipo, uno de ellos automatizado. [`docs/pruebas/README.md`](pruebas/README.md) exige que el participante de una UAT sea externo al equipo. Por eso, salvo que antes haya una sesión con un participante real, esto se presenta como **«ensayo de la UAT»** y no como «UAT aceptada». Si el jurado pregunta, la respuesta es esta misma tabla.

Los defectos que encontró el ensayo del 18 de noviembre ya están corregidos y desplegados: CAL-194 (botón Atrás), CAL-195 (permisos por URL) y el PR #111 (notas y quitar productos). Contarlo suma: la UAT encontró algo y el equipo lo cerró.

---

## Antes de empezar · con 30 minutos de margen

### 1. Encender y comprobar el entorno

```powershell
.\scripts\azure-backend-power.ps1 -Action Status
.\scripts\azure-backend-power.ps1 -Action Start   # si no está en B1 / Running / Ready
```

Desde el 27 de noviembre de 2026 el backend vive en `rg-callejon9-prod-cus-001` (Central US), en la misma suscripción **Azure for Students** que la web. Si el script responde que no existe el resource group, revisa la suscripción activa con `az account show` y cámbiala con `az account set --subscription <id>`.

Comprobaciones:

- `https://app-callejon9-api-prod-70af93.azurewebsites.net/actuator/health` responde `{"status":"UP",…}`.
- `https://restohub.azurewebsites.net/login` carga.
- `https://restohub.azurewebsites.net/api/v1/auth/me` responde `401`, lo que demuestra que el proxy llega a la API.

El frontend corre en el plan F1, que se duerme. Hay que abrir cada pantalla una vez antes de que entre el jurado (paso 4).

### 2. Dejar limpio el restaurante `pruebas`

- **Todas las mesas libres.** Si alguna orden quedó abierta, se cobra en `/cashier` o se cancela desde `/waiter`.
- **Turno de caja abierto** en `/cash-sessions`, con fondo inicial y con la cuenta `caja@pruebas.com`. Sin turno, el cobro responde `422`.
- Los restos de la SIT («SIT CAL-136 Tostada de prueba 1401» y «SIT CAL-143 Tortilla 1417») están dados de baja y no estorban.

### 3. Cuatro contextos de navegador aislados

Pestañas del mismo navegador no sirven: comparten la cookie, y el último en iniciar sesión desconecta a los demás. Usa ventanas de incógnito distintas o perfiles de Chrome separados:

| Ventana | Cuenta (restaurante `pruebas`) | Rol |
|---|---|---|
| A | `admin@pruebas.com` | ADMIN |
| B | `mesero@pruebas.com` | WAITER |
| C | `cocina@pruebas.com` | KITCHEN |
| D | `caja@pruebas.com` | CASHIER |

La contraseña es la de la credencial `c9-test-password` de Jenkins. No se escribe en este archivo ni en la pantalla compartida.

Para UAT-13 hace falta además la cuenta de administrador del restaurante «SIT Restaurante B» (`sit-restaurante-b-28101434`), en una quinta ventana o en la ventana A después de cerrar sesión.

### 4. Calentar las rutas

Abre una vez `/login`, `/signup`, `/admin`, `/waiter`, `/kitchen`, `/cashier`, `/cash-sessions`, `/history`, `/inventory` y `/analytics`.

### 5. App móvil · UAT-07

Un teléfono con Expo Go y la app servida desde `mobile/` (`pnpm start`), con `EXPO_PUBLIC_API_URL` apuntando a la URL HTTPS de Azure que se usó en la SIT CAL-139. Inicia sesión una vez antes para descartar problemas de red.

---

## El recorrido

### Apertura · 1 min

> Vamos a recorrer un día del restaurante con cada rol. Cada escenario tiene criterios de aceptación escritos antes de probar; lo que vean es lo que la versión `main` hace hoy en Azure, no una maqueta.

### UAT-01 · Autenticación y sesiones · 2 min · CAL-146

En las ventanas B, C y D:

1. Intenta entrar con una contraseña equivocada. Aparece solo «Restaurante, correo o contraseña incorrectos.» y el campo de contraseña queda vacío.
2. Entra bien. Cada rol llega directo a su pantalla: mesero a Mesas, cocina a Cocina y caja a Caja.
3. En una ventana: cierra sesión y pulsa **Atrás**. Regresa a `/login`, sin pantalla de error.

> Este último paso falló en el ensayo del 18 de noviembre y se corrigió en CAL-194.

### UAT-02 · Alta de restaurantes y planes · 2 min · CAL-147 · en progreso

En una ventana aparte, en `/signup`: registra un restaurante nuevo y elige plan. Muestra que cada plan dice cuántos usuarios y mesas incluye. Al terminar, el restaurante está vacío y listo para configurar.

Criterio que se mide: terminar el alta en menos de 5 minutos sin ayuda. Anota cuánto tardó.

### UAT-03 · Usuarios y roles · 2 min · CAL-148 · en progreso

Ventana A → `/admin`, Usuarios:

1. Crea un mesero con una contraseña débil. El mensaje explica qué le falta.
2. Corrígela y créalo.
3. Desactiva a ese usuario. Si tenía sesión abierta, pierde el acceso de inmediato.

### UAT-04 · Catálogo · 2 min · CAL-149

Ventana A → `/admin`, Productos:

1. Crea una categoría y tres productos con precio.
2. En la ventana B aparecen en el menú del mesero con su precio.
3. Corrige el precio de uno. Desactiva otro: deja de aparecer para el mesero.

> Una orden anterior conserva el precio con el que se tomó; el catálogo no reescribe cuentas.

### UAT-05 · Mesas · 1 min · CAL-150 · en progreso

Ventana A: registra tres mesas con capacidad. Ventana B: aparecen libres, con número y capacidad, y el estado libre u ocupada se entiende de un vistazo.

### UAT-06 · Pedidos del mesero · 3 min · CAL-151

Ventana B:

1. Abre una mesa para cuatro comensales.
2. Captura el pedido con notas por producto, como «sin crema» o «sin cebolla».
3. Quita un producto antes de enviar: aparece «Producto quitado de la orden.» y el total baja.
4. Envía a cocina. Aparecen el folio `ORD-…` y el total, y la mesa queda ocupada.

> Las notas y quitar productos faltaban en el ensayo del 18 y se cerraron con el PR #111.

**Esta orden se cobra en UAT-09.** No la canceles.

### UAT-07 · App móvil de meseros · 2 min · CAL-152

En el teléfono, con una sola mano: entra como mesero, toma el pedido de **otra** mesa y envíalo a cocina. La app separa «Enviar pedido al servidor» de «Enviar a cocina»; son dos pasos.

Para el criterio sin señal: activa el modo avión, intenta enviar y la app avisa y ofrece «Reintentar». Quita el modo avión y reintenta.

### UAT-08 · Cocina y tiempo real · 3 min · CAL-153 · en progreso

**Momento central: las ventanas B y C visibles a la vez.**

1. Sin recargar, los pedidos de UAT-06 y UAT-07 ya están en el tablero de cocina, ordenados por llegada y con su tiempo de espera.
2. Avanza cada renglón: Pendiente → En preparación → Listo. Solo se ofrece el siguiente paso válido.
3. Al marcar el último, la ventana del mesero lo ve sin recargar.

> El tablero recibe los cambios por el canal en tiempo real `/ws` (CAL-172), no recargando la página.

### UAT-09 · Cobro y pagos · 3 min · CAL-154 · sin ejecutar

Ventana D → `/cashier`, la orden de UAT-06:

1. Agrega una propina.
2. Registra un pago parcial con tarjeta, menor que el total, e intenta cerrar. El sistema explica que falta dinero y no cierra la cuenta.
3. Completa el pago con efectivo por encima de lo que falta. El cambio es correcto.
4. Cierra la venta. En la ventana B, la mesa vuelve a estar libre.

Anota el folio del ticket para UAT-10.

### UAT-10 · Tickets · 1 min · CAL-155

Ventana D: descarga el ticket. Muestra folio, partidas, propina, pagos y total. Después, en `/history`, búscalo por su folio.

### UAT-11 · Inventario · 2 min · CAL-156

1. Ventana C → `/inventory`: registra una salida de insumos de fin de turno.
2. Ventana A: la existencia coincide con los movimientos. Haz un ajuste por conteo y señala qué insumos están por debajo del mínimo.

### UAT-12 · Ventas, historial y analítica · 2 min · CAL-157 · sin ejecutar

1. Ventana D → `/history`: las ventas del día. El total coincide con el cobro de UAT-09 más lo que ya hubiera hoy, que conviene anotar antes de empezar.
2. Ventana A → `/analytics`: ¿cuánto se vendió hoy y qué fue lo más vendido? El participante lo responde sin ayuda.
3. Cierre de turno en `/cash-sessions`, con el efectivo contado.

### UAT-13 · Aislamiento y permisos · 2 min · CAL-158

1. Ventana B: escribe `/admin` y `/cashier` en la barra de direcciones. Aparece «No tienes permiso para ver esta información.» con un enlace «Ir a Mesas».
2. Ventana D: abre `/admin`. Ventana C: abre `/waiter`. Mismo mensaje, cada uno con su enlace.
3. Entra como administrador de «SIT Restaurante B»: no ve mesas, pedidos, ventas ni usuarios de `pruebas`.

> No ve nada del otro restaurante y no es porque la pantalla lo filtre: lo impone PostgreSQL con Row Level Security. La SIT CAL-145 lo prueba directamente contra la API.

### Cierre · 1 min

Vuelve a la tabla de estados del principio: qué está aceptado, qué falta y cuándo se ejecuta.

---

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| Una pantalla tarda la primera vez | El plan F1 se durmió. Espera; para la próxima, calienta las rutas |
| El login no responde | Revisa `/actuator/health`. Si no está `UP`, ejecuta `azure-backend-power.ps1 -Action Start`; el arranque en frío tarda unos minutos |
| Las ventanas se desconectan entre sí | Comparten cookie. Usa incógnitos o perfiles distintos |
| El cobro responde `422` | La cajera no tiene turno abierto en `/cash-sessions` |
| El cobro responde `409` | La orden ya se cobró. Usa otra |
| Una mesa aparece ocupada | Cobra su orden en `/cashier` o cancélala desde `/waiter` |
| La app móvil no conecta | Revisa `EXPO_PUBLIC_API_URL` (HTTPS) y la red del teléfono. Si no se arregla, muestra UAT-07 desde la evidencia de la SIT CAL-139 |

## Después de la demostración

- Registra en cada issue el resultado de cada criterio, con hora, folio y captura, siguiendo [`docs/pruebas/README.md`](pruebas/README.md).
- Apaga el backend para no gastar crédito: `.\scripts\azure-backend-power.ps1 -Action Stop`.
