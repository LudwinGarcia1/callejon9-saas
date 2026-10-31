# Evidencia de prueba · CAL-152 — UAT · App móvil de meseros

> **Estado: preparada, sin ejecutar.** Este documento contiene el guion de la sesión y los casos listos para llenarse **durante** la ejecución. Ningún caso se ha ejecutado; los resultados obtenidos se dejan vacíos a propósito. El participante no lee este documento: recibe solo [`guion-participante.md`](guion-participante.md).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-152](https://linear.app/callejon19/issue/CAL-152) — UAT · App móvil de meseros |
| Historia o issue padre | [CAL-117](https://linear.app/callejon19/issue/CAL-117) — Ejecutar pruebas de aceptación (UAT) con usuarios por rol |
| Tipo de prueba | UAT (escenario UAT-07) |
| Módulo | App móvil de meseros (`mobile/`, CAL-114) |
| Entorno | Azure (API) · Expo Go en el teléfono (app) |
| URL del frontend | `https://restohub.azurewebsites.net` (solo el tablero de cocina, para C03) |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado | API `fb36760` (rama `fix/cal-169-hotfix-azure`, **no es de `main`**). Frontend `e6a6782`. App móvil: el SHA de `main` que sirva Metro, se anota el día de la sesión. |
| Registro de despliegue | Comentarios «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118): frontend del 2026-10-22 y API del 2026-10-28 10:52–10:56. La app no se despliega: la sirve Metro desde el repositorio |
| Ejecución de CI | No aplica (UAT manual) |
| Navegador / dispositivo | Teléfono del participante con Expo Go (SDK 57); modelo y sistema se anotan el día de la sesión. Cocina: navegador del facilitador |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v0 · 2026-10-28 · guion preparado, sin ejecutar |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba (facilitador) | Por definir | Sirve la app con Metro, abre la cocina web, cronometra, observa y anota; **no** opera el teléfono |
| Revisor | Por definir | **Distinto del ejecutor y del participante.** Llena la sección 10 |
| Participante P1 | Mesero, con su propio teléfono. Perfil por definir | Se identifica solo como P1; sin nombre ni datos personales en el repositorio |

**Participante.** `docs/pruebas/README.md` pide que el participante sea externo al equipo. El issue pide que use **su** teléfono, porque el criterio 2 se juzga con el agarre de cada quien. Si al final participa un integrante, o se usa un teléfono del equipo, se declara como desviación aquí y en la sección 8. Las salvaguardas son las mismas que en la sesión de caja de CAL-154:
- No construyó la app ni ejecutó la SIT CAL-139.
- No lee este documento.
- Los criterios se preguntan con las palabras exactas del issue.
- Toda ayuda queda anotada.

## 3. Fechas

Todas las horas en `America/Mexico_City`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Issue padre (CAL-117) | 2026-10-20 | 2026-10-26 | 2026-10-21 | | Abierto |
| Esta tarea (CAL-152) | | | | | En Linear está en In Progress. El formato anterior se rechazó el 2026-10-21: describía un aviso que la app no tiene y no identificaba al participante |
| Preparación del entorno y datos | 2026-10-28 | | 2026-10-28 | | Este guion |
| Ejecución de los casos | Por acordar | | | | |
| Re-prueba de defectos *(si hubo)* | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que un mesero, con su propio teléfono y sin ayuda, toma el pedido de una mesa en la app y lo envía a cocina. Además, que el pedido aparece en la cocina igual que uno de la web, que la app se puede usar con una mano y que, sin señal, avisa y deja reintentar.

**Fuera de alcance.**
- La integración app ↔ API: sesión en cookie nativa y paridad de datos con la web. La cubre la SIT [CAL-139](https://linear.app/callejon19/issue/CAL-139).
- El alta de productos desde la app ([CAL-160](https://linear.app/callejon19/issue/CAL-160)).
- Las notas por producto: la app no las tiene y este escenario no las pide. En la web las cubre CAL-151.
- Cancelar o cobrar la orden.
- La app compilada para tienda: se prueba en Expo Go, como en la SIT.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. El pedido llega a cocina igual que desde la web. | C02, C03 |
| 2. La app se usa con una mano y los botones son fáciles de tocar. | C04 (se observa durante C01 a C02 y se pregunta al final) |
| 3. Sin señal, la app avisa y permite reintentar. | C05 |

## 5. Preparación

**Dependencias**

- [x] SIT [CAL-139](https://linear.app/callejon19/issue/CAL-139) · App móvil de meseros aprobada (Done en Linear). Sin relaciones «Blocked by».
- [ ] Hay un comentario «Despliegue» en Linear con el SHA que corre en Azure. Si hubo un despliegue posterior al 2026-10-28, se actualiza la sección 1.

**Hallazgo de la preparación (aún sin ejecutar).** Al leer el código de la app aparece un punto que puede afectar el criterio 2. No se da por fallido: el caso se ejecuta y se anota lo que ocurra.

- Los botones **«−» y «+» de cada producto** miden 40 × 40 (`stepperButton` en `OrderScreen.tsx`) y no tienen área de toque extra. La guía de Android recomienda al menos 48 × 48 dp y la de Apple, 44 × 44 pt.
- Los botones grandes («Iniciar sesión», «Enviar pedido al servidor», «Enviar a cocina», «Reintentar») miden al menos 48 de alto (`minHeight: 48` en `ui/components.tsx`).
- En Expo Go, el botón flotante de herramientas de desarrollo tapa «Salir» (observación de la SIT CAL-139). No existe en una app compilada; no se cuenta como defecto.

**Precondiciones del entorno** *(se marcan el día de la sesión)*

- [ ] Backend encendido y `/actuator/health` en `UP` *(respuesta en `respuestas/health.txt`)*
- [ ] Commit de la API igual al del registro de despliegue más reciente en CAL-118
- [ ] Metro sirviendo la app desde `main` actualizado (se anota el SHA en la sección 1). `mobile/.env` debe tener `EXPO_PUBLIC_API_URL` apuntando a la API de Azure y `EXPO_PUBLIC_ALLOW_INSECURE_HTTP=false`. Comando: `npx expo start --lan`. La pantalla de login debe mostrar «Conexión cifrada (HTTPS)» con el host de Azure.
- [ ] Teléfono del participante con Expo Go (SDK 57) instalado y conectado a la **misma Wi-Fi** que la computadora de Metro
- [ ] Teléfono en modo «No molestar», para que ninguna notificación personal aparezca en las capturas
- [ ] El participante sabe tomar capturas en su teléfono y acepta compartirlas. Se pasan por cable, Drive o como **documento** en WhatsApp (`docs/pruebas/README.md`)
- [ ] En la computadora del facilitador, la cocina web (`/kitchen`) abierta con la cuenta de cocina, con la barra de direcciones y el reloj visibles
- [ ] Hay al menos **una mesa libre** en el restaurante `pruebas`; se anota su número
- [ ] La cuenta del mesero inicia sesión. El facilitador lo comprueba antes, sin fallar contraseñas.

**Datos de prueba**

| Dato | Valor | Cómo se obtuvo |
|---|---|---|
| Restaurante | `pruebas` | Restaurante de prueba de CAL-117 |
| Cuenta WAITER (teléfono) | `mesero@pruebas.com` | Cuenta de prueba de CAL-117 |
| Cuenta KITCHEN (cocina web del facilitador) | `cocina@pruebas.com` | Cuenta de prueba de CAL-117 |
| Mesa del pedido | | Se anota el día de la sesión |
| Precios del día | Arrachera 300 g: · Agua de horchata: · Flan napolitano: | Menú de la app, antes de empezar |
| Total esperado tras C02 | | 2 × arrachera + 2 × horchata, con los precios del día |
| Total esperado tras C05 | | El de C02 + 1 × flan |
| Orden de referencia de la web | | Folio de una orden enviada desde la web: la de C04 de CAL-151 si la sesión es la misma, o la que esté en el tablero al empezar |

> Las contraseñas las entrega el facilitador en persona. Nunca se escriben aquí, ni en capturas ni en el chat.

**Cuidado con el freno de intentos.** El backend bloquea 15 minutos una cuenta tras 5 contraseñas erróneas. La app llama directo a la API, así que el freno por IP cuenta los fallos de toda la Wi-Fi de la sesión: 20 fallos bloquean a todos los que la comparten ([CAL-169](https://linear.app/callejon19/issue/CAL-169)). El participante no prueba contraseñas.

**Para la sesión de caja.** La orden de este escenario se queda en cocina y no se cancela. Sirve como la segunda cuenta que pide UAT-09 ([CAL-154](https://linear.app/callejon19/issue/CAL-154)) para el caso de pago insuficiente. Su folio se anota en la sección 8.

## 6. Casos de prueba

Comportamiento esperado tomado del código de la app (`mobile/src`) y de la cocina desplegada (`e6a6782`):
- **Login** (`LoginScreen.tsx`): campos «Restaurante», «Correo» y «Contraseña», y el botón «Iniciar sesión». Al entrar se ve «Mesas» con el aviso «Autenticación exitosa».
- **Mesas** (`TablesScreen.tsx`):
  - Cada mesa muestra su número, «N personas» y su estado. Se recarga deslizando hacia abajo.
  - Si falla la red: «Error al consultar las mesas» y el botón «Reintentar».
- **Pedido** (`OrderScreen.tsx`):
  - En una mesa libre: «Comensales» con «−» y «+», y la lista de productos, cada uno con «−» y «+».
  - El pie muestra «N productos» y el total, con el botón «Enviar pedido al servidor».
  - En una mesa ocupada: la orden abierta, «Cuenta actual» y el botón «Agregar a la orden».
- **Resultado** (`ResultScreen.tsx`):
  - «Pedido registrado · El servidor respondió con el folio ORD-…», con el estado «Nueva (sin enviar a cocina)».
  - «Enviar a cocina» cambia a «Pedido enviado a cocina» y el estado a «Enviada a cocina». Después queda «Volver a mesas».
- **Sin red** (`api/client.ts`): «No se pudo conectar con el servidor. Revisa tu conexión a internet.». Al enviar el pedido aparece bajo el título «El pedido no se envió» y se conservan las cantidades elegidas.
- **Cocina web** (`kitchen-view.tsx`): consulta las órdenes cada 5 s. Cada tarjeta muestra el folio, «Mesa N» y los productos con su estado.

### C01 — El mesero inicia sesión en su teléfono

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 (observación) |
| Prioridad | Media |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** App abierta en Expo Go, sin sesión. Arranca el cronómetro al leer la situación 1 del guion.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | P1 escribe restaurante, correo y contraseña y toca «Iniciar sesión» | `pruebas`, `mesero@pruebas.com` | Pantalla «Mesas» con el aviso «Autenticación exitosa» | | `capturas/C01-P01-mesas.png` (del teléfono, contraseña no visible) |

### C02 — Toma el pedido de una mesa y lo envía a cocina

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Abrir una mesa libre e indicar los comensales | 2 comensales | Pantalla «Mesa N» con «Comensales» = 2 | | `capturas/C02-P01-mesa.png` |
| 2 | Elegir los productos | Pedido de la situación 1 | El pie muestra «4 productos» y el total esperado de la sección 5 | | `capturas/C02-P02-pedido.png` |
| 3 | Enviar el pedido | — | «Pedido registrado · El servidor respondió con el folio ORD-…»; estado «Nueva (sin enviar a cocina)» | | `capturas/C02-P03-registrado.png` |
| 4 | Enviarlo a cocina | — | «Pedido enviado a cocina»; estado «Enviada a cocina»; mismo folio y total | | `capturas/C02-P04-enviado.png` |

**Qué observar en C02.**
- Si P1 se detiene en el paso 3 creyendo que el pedido ya llegó a cocina, se anota literal. La app separa «Enviar pedido al servidor» de «Enviar a cocina».
- No se le indica el segundo botón. Si termina sin enviarlo a cocina, el caso queda ❌ Fallido para el criterio 1, con la ayuda anotada si se le dio.

### C03 — El pedido llega a la cocina igual que desde la web

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | Facilitador (cocina web) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Antes de C02, capturar el tablero de cocina con la orden de referencia de la web | Folio de referencia (sección 5) | Tarjeta con folio, «Mesa N» y productos con su estado | | `capturas/C03-P01-cocina-antes.png` |
| 2 | Tras el paso 4 de C02, observar el tablero **sin recargar** | Folio de C02 | En 5 s o menos aparece una tarjeta con el mismo folio, «Mesa N», 2 × Arrachera 300 g y 2 × Agua de horchata pendientes, con el mismo formato que la de referencia | | `capturas/C03-P02-cocina-recibe.png` |

Detener el cronómetro aquí: es la duración de la sección 9.

### C04 — Se usa con una mano y los botones son fáciles de tocar

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Media |
| Rol que ejecuta | WAITER (P1); observa el facilitador |
| Inicio / fin de ejecución | Durante C01 a C02 |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

El guion le pide a P1 sostener el teléfono con una mano, como si con la otra cargara una charola. El facilitador no corrige el agarre; solo anota.

| # | Qué se registra | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Si P1 tuvo que usar la otra mano o cambiar de agarre, y en qué pantalla | Completa C01 a C02 con una mano | | Notas del facilitador |
| 2 | Toques fallidos: botón equivocado o que no respondió al primer toque, y cuál botón (en especial «−» y «+» de los productos; ver la sección 5) | Ninguno o casi ninguno | | Notas del facilitador |
| 3 | Al final, preguntar literal: «¿Pudiste usar la app con una mano? ¿Los botones fueron fáciles de tocar?» | P1 responde que sí | | Respuesta literal en la sección 9 |

### C05 — Sin señal, la app avisa y permite reintentar

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P1) |
| Inicio / fin de ejecución | |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Precondición.** La mesa de C02 está ocupada y su orden, en cocina.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El facilitador lee la situación 2 del guion; P1 abre la mesa de C02 y elige el producto, todavía con señal | 1 × Flan napolitano | Se ve la orden abierta con «Cuenta actual» y el botón «Agregar a la orden» | | `capturas/C05-P01-mesa-ocupada.png` |
| 2 | Cuando el facilitador lo indica, P1 activa el modo avión y toca «Agregar a la orden» | — | «El pedido no se envió · No se pudo conectar con el servidor. Revisa tu conexión a internet.»; el flan sigue elegido | | `capturas/C05-P02-sin-senal.png` (con el ícono de modo avión visible) |
| 3 | P1 desactiva el modo avión y vuelve a intentarlo **sin ayuda** | — | «Productos agregados · Se agregaron a la orden ORD-… y ya aparecen en cocina como pendientes.»; el total es el esperado tras C05 | | `capturas/C05-P03-reintento.png` |
| 4 | Observar la cocina web | — | La tarjeta de la orden muestra el flan como pendiente | | `capturas/C05-P04-cocina.png` |

**Qué observar en C05.**
- Se anota, literal, qué entendió P1 del aviso y cómo decidió reintentar. En esta pantalla no hay un botón «Reintentar»: se reintenta tocando otra vez «Agregar a la orden».
- Al quitar el modo avión, el teléfono puede tardar unos segundos en recuperar la Wi-Fi. Si el primer reintento vuelve a fallar por eso, se anota y se cuenta como un intento más, no como defecto.
- Expo Go puede mostrar un aviso propio de que perdió la conexión con Metro. Es de la herramienta de desarrollo, no de la app.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | | | | | |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 5 | 0 | 0 | 0 | 5 |

**Desviaciones y limitaciones.**
- La sesión no se ha realizado. Faltan el participante, el facilitador y el revisor.
- La API desplegada (`fb36760`) no es un commit de `main`; está registrada como excepción en CAL-118.
- La app corre en Expo Go servida por Metro, no compilada para tienda.
- Hay un hallazgo de preparación (sección 5) que puede afectar el criterio 2.

**Folio que queda para UAT-09:** *se llena en la sesión.*

**Conclusión del ejecutor.** Sin ejecutar. Se llena solo después de la sesión real.

## 9. Anexo UAT — aceptación de usuario

| Campo | Valor |
|---|---|
| Perfil del participante | P1 — mesero; ¿conocía el sistema? |
| Teléfono | Modelo y sistema operativo; ¿es su teléfono? |
| Modalidad | Presencial |
| Facilitador | |
| Duración medida | *Cronómetro: de la situación 1 al paso 2 de C03* |
| Ayuda brindada | |
| Comentarios del participante | *Palabras literales, tomadas al momento* |
| Respuesta a «¿Pudiste usar la app con una mano? ¿Los botones fueron fáciles de tocar?» (C04) | |
| ¿Acepta el escenario? | Sí · No · Con observaciones |
| Hoja de aceptación firmada | Se adjunta en Linear a CAL-152 y a CAL-117; no se sube al repositorio |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas del teléfono muestran la barra de estado con la hora, y las de la web, la URL y el reloj; coinciden con los pasos
- [ ] Los mensajes coinciden con el código de la app y de la cocina
- [ ] El commit probado corresponde al registro de despliegue y al SHA que sirvió Metro
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies, notificaciones personales ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Se firma después de la ejecución real. A la fecha de esta versión (v0, 2026-10-28) ningún caso se ha ejecutado.

**Ejecutor:** — · **Fecha:** —
