# Práctica: solo el servidor publica en el canal de cocina (CAL-104)

Módulo elegido: **canal en tiempo real** (WebSocket/STOMP en `/ws`). Es el que lleva los pedidos al tablero de cocina en vivo, y su único control de aislamiento era revisar a qué tópico se suscribe cada cliente.

## 1. Vulnerabilidad

| Campo | Detalle |
|---|---|
| Módulo | `backend/.../realtime/WebSocketConfig.java`, `TenantSubscriptionInterceptor.java` |
| Deficiencia | Cualquier cliente conectado podía publicar en el tópico de cocina. El canal solo se protegía en la suscripción, no en la publicación. |
| Evidencia | `enableSimpleBroker("/topic")` y un único interceptor de entrada que, ante cualquier comando distinto de `SUBSCRIBE`, hacía `return message`. El broker simple de Spring reenvía a los suscriptores todo `SEND` cuyo destino empieza con `/topic`. |
| Riesgo | **Integridad**: un usuario autenticado de cualquier rol (mesero, cajero) podía inyectar pedidos o cambios de estado falsos en el tablero de cocina de su restaurante. **Aislamiento**: conociendo el UUID de otro restaurante (que ya viaja en respuestas de la API), podía hacer lo mismo en la cocina ajena. Esto rompía la frontera que el interceptor dice proteger. |
| Prioridad | Alta: basta una sesión válida y cruza la frontera entre restaurantes. |

## 2. Corrección

`ClientSendInterceptor` revisa cada mensaje que un cliente intenta publicar, antes de que toque el broker.

| Decisión | Motivo |
|---|---|
| La regla se aplica al tipo de mensaje que el broker reenvía (`SimpMessageType.MESSAGE`), no al comando `SEND` | Para Spring, una trama `MESSAGE` (la que normalmente solo envía el servidor) escrita a mano por un cliente es del mismo tipo que un `SEND`, y el broker la reenvía igual. La primera versión revisaba solo `SEND` y la revisión de código demostró que una trama `MESSAGE` cruda seguía llegando a la cocina (hallazgo A1). |
| Un mensaje de cliente solo se acepta hacia `/app/**`. Todo lo demás se rechaza: `/topic/**`, cualquier otro destino y un `SEND` sin destino. | Es una lista de lo permitido, no de lo prohibido: si mañana se habilita `/queue` o `/user` en el broker, queda cerrado sin tocar este código. |
| `/app/**` no llega al broker | Ese prefijo solo se enruta a métodos `@MessageMapping`. Hoy no existe ninguno, así que en la práctica ningún `SEND` de cliente hace nada. Quien agregue uno debe validar el restaurante del usuario por su cuenta (queda anotado en el Javadoc). |
| Al rechazar: trama `ERROR` y cierre de la conexión | Es lo mismo que ya ocurre con una suscripción a otro restaurante. Un cliente legítimo nunca publica (el frontend no usa STOMP y solo el servidor publica), así que cerrar la conexión no afecta a nadie de buena fe. |
| Interceptor propio, separado de `TenantSubscriptionInterceptor` | Cada uno tiene una sola regla: uno valida a qué se suscribe el cliente y el otro, que no publique. Los dos se registran en el canal de entrada. |
| La publicación del servidor no cambia | `KitchenRealtimeEventPublisher` usa `SimpMessagingTemplate`, que entra al broker por otro canal y no pasa por estos interceptores. |

| Archivo | Cambio |
|---|---|
| `backend/.../realtime/ClientSendInterceptor.java` | Nuevo. Rechaza todo mensaje de cliente (`SEND` o `MESSAGE`) fuera de `/app/`. |
| `backend/.../realtime/WebSocketConfig.java` | Registra el nuevo interceptor junto al de suscripciones. El prefijo `/app` pasa a ser la constante `APPLICATION_DESTINATION_PREFIX`. |

## 3. Pruebas

Las pruebas P1–P3 levantan el servidor real en un puerto aleatorio, se conectan por WebSocket con la cookie de sesión de usuarios reales y corren contra PostgreSQL 16. En cada una, un tablero de cocina (usuario `KITCHEN`) queda suscrito al tópico de su restaurante. Antes de seguir, la prueba confirma la suscripción con una señal publicada por el servidor.

### P1. Un usuario del mismo restaurante no puede publicar en su cocina

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que un usuario sin permiso no puede inyectar mensajes en la cocina de su propio restaurante. |
| Condiciones iniciales | Restaurante A con un usuario `KITCHEN`, cuyo tablero está suscrito a `/topic/tenant.{A}.kitchen`, y un usuario `WAITER` conectado. |
| Procedimiento | El mesero hace `SEND` a `/topic/tenant.{A}.kitchen` con un pedido falso. Después el servidor publica un mensaje de control en el mismo tópico. |
| Resultado esperado | El tablero no recibe el pedido falso. El mesero recibe un `ERROR` y su conexión se cierra. El tablero sigue conectado y el primer mensaje que recibe es el de control. |
| Resultado obtenido | **Antes:** falla con «el mensaje falso no debe llegar a la cocina»: el pedido falso llegó al tablero (vulnerabilidad confirmada). **Después:** el mesero recibe `ERROR` y se desconecta; el tablero solo recibe el mensaje de control. |

### P2. Un usuario de otro restaurante no puede publicar en la cocina ajena

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que no se puede cruzar la frontera entre restaurantes publicando en el tópico de otro. |
| Condiciones iniciales | Tablero de cocina del restaurante A suscrito a su tópico. Un `ADMIN` del restaurante B, conectado, que conoce el UUID de A. |
| Procedimiento | El usuario de B hace `SEND` a `/topic/tenant.{A}.kitchen` con un pedido falso. Después el servidor publica un mensaje de control. |
| Resultado esperado | Igual que P1: el pedido falso no llega a la cocina de A, el emisor recibe `ERROR` y se desconecta, y el tablero sigue recibiendo lo que publica el servidor. |
| Resultado obtenido | **Antes:** falla con «el mensaje falso no debe llegar a la cocina»: el pedido de B apareció en la cocina de A. **Después:** `ERROR` y desconexión del emisor; el tablero de A solo recibe el control. |

### P3. La cocina sigue recibiendo los eventos del servidor

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que la corrección no rompe el uso legítimo del canal. |
| Condiciones iniciales | Tablero de cocina del restaurante A suscrito a su tópico. |
| Procedimiento | El servidor publica un cambio de estado de un producto («Tacos al pastor», `PREPARING`) con `KitchenRealtimeEventPublisher`, el mismo componente que lo hace en producción. |
| Resultado esperado | El tablero recibe el evento con el producto y su estado, y sigue conectado. |
| Resultado obtenido | El evento llega con «Tacos al pastor» y `PREPARING`; la conexión sigue abierta. Pasaba antes y después de la corrección. |

### Pruebas complementarias

| Prueba | Qué comprueba | Resultado obtenido |
|---|---|---|
| Una trama `MESSAGE` escrita a mano no llega a la cocina | Un mesero abre un WebSocket sin librería STOMP, se conecta y manda una trama `MESSAGE` (no `SEND`) con el pedido falso al tópico de su cocina | **Con la primera versión, que solo revisaba `SEND`:** falla con «el mensaje falso no debe llegar a la cocina» (hallazgo A1). **Con la corrección:** el emisor recibe `ERROR` y se desconecta; la cocina solo recibe el control. |
| `ClientSendInterceptorTest` (tramas armadas a mano) | Rechaza `SEND` a `/topic/tenant.{id}.kitchen`, `/topic/cualquiera`, `/queue/x`, `/user/queue/x`, `/app` (sin barra), `/application/x`, destino vacío y sin destino. Rechaza una trama `MESSAGE` de cliente. Deja pasar `SEND` a `/app/...`. No interviene en `SUBSCRIBE` ni `DISCONNECT`. | 11/11 en verde |
| Pruebas existentes del canal | `TenantSubscriptionInterceptorTest`, `RevokedSessionSweeperTest`, `RevokedSessionSweeperUnitTest`, `WebSocketOriginTest` | 11/11 en verde |

El caso «sin destino» va en la prueba unitaria porque el cliente STOMP de Spring se niega a enviar un `SEND` sin destino; un cliente escrito a mano sí podría.

Pruebas automatizadas:

- `backend/src/test/java/com/callejon9/realtime/KitchenTopicPublishTest.java`: P1, P2, P3 y la trama `MESSAGE` cruda.
- `backend/src/test/java/com/callejon9/realtime/ClientSendInterceptorTest.java`: reglas del interceptor.

Comando:

```powershell
cd backend; .\mvnw.cmd -B "-Dtest.database.mode=external" -DDB_PORT=5432 "-Dtest=KitchenTopicPublishTest,ClientSendInterceptorTest,TenantSubscriptionInterceptorTest" test
```

En los registros del backend aparece `Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]` una vez por cada rechazo. Es el registro esperado: Spring anota así el `AccessDeniedException` antes de mandar el `ERROR` al cliente.
