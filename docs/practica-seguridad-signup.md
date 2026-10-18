# Práctica: límite de altas en el registro de restaurantes (CAL-103)

Módulo elegido: **alta de restaurantes** (`/signup` → `POST /api/v1/signup`). Es el único endpoint, aparte del login, que cualquiera puede usar sin sesión, y cada llamada exitosa crea datos permanentes.

## 1. Vulnerabilidad

| Campo | Detalle |
|---|---|
| Módulo | `backend/.../platform/tenant/web/SignupController.java`, `TenantOnboardingService.java`, `config/SecurityConfig.java` |
| Deficiencia | El alta es pública (`permitAll`) y no tenía ningún límite. Desde un mismo cliente se podían crear restaurantes sin freno. |
| Evidencia | `SignupController.signup` llamaba directo a `onboard`, que crea tenant, suscripción y usuario ADMIN y calcula un bcrypt. El único limitador del proyecto (`LoginAttemptLimiter`) solo protege el login. |
| Riesgo | **Disponibilidad**: cada alta escribe tres filas y gasta CPU en bcrypt. **Integridad**: un atacante acapara identificadores (slugs) que después nadie puede usar. **Confidencialidad**: repetir altas con slugs ajenos y leer el `409` sirve para enumerar qué restaurantes existen. |
| Prioridad | Media: se explota sin autenticación, pero no expone datos de otros restaurantes. |

## 2. Corrección

`SignupRateLimiter` limita cuántas altas puede intentar una misma IP dentro de una ventana deslizante.

| Decisión | Motivo |
|---|---|
| Cupo de **5 altas por IP por hora** (`SIGNUP_MAX_PER_IP`, `SIGNUP_WINDOW`) | Un restaurante se registra una vez; una cadena que registra varias sucursales desde la misma oficina cabe en el cupo. |
| La llave es la IP resuelta con `ClientIp`, la misma del login | Detrás de Next.js se usa la última entrada de `X-Forwarded-For` solo si la conexión viene de la red local. Desde una IP pública la cabecera se ignora, así que no se puede falsificar para evadir el límite. |
| Una IPv6 cuenta por su red `/64`, no por dirección | Un proveedor entrega una red `/64` completa a cada cliente, que puede estrenar una dirección en cada petición. Contar por dirección dejaba el límite sin efecto (hallazgo M1 de la revisión). Una IPv6 que encapsula una IPv4 (`::ffff:a.b.c.d`) cuenta como esa IPv4. Solo se interpretan literales, sin consultas DNS. |
| Cuenta toda alta que pasa la validación, termine en `201` o en `409` | Si el `409` no contara, el endpoint seguiría sirviendo para enumerar restaurantes sin freno. |
| Un `400` por formato inválido no cuenta | No llega al servicio: no crea nada ni calcula bcrypt. |
| Se revisa **antes** de crear nada | Un alta rechazada no toca la base ni gasta bcrypt. |
| Respuesta `429 Too Many Requests` con `Retry-After` | El mensaje es fijo («Se alcanzo el limite de registros de restaurantes desde esta conexion. Intenta de nuevo en N minutos.») y no repite el slug ni el correo enviados. El formulario de registro ya muestra el `detail` de este tipo de errores, así que el frontend no cambia. |
| Comprobación y registro en un solo paso, con candado por llave | Dos peticiones simultáneas de la misma IP no pueden ver ambas «queda un lugar» y entrar las dos. La purga de llaves vencidas usa el mismo candado, así que no puede perder un uso que se está registrando (hallazgo B1 de la revisión). |

`SlidingWindowRateLimiter` es genérico (no sabe qué es la llave ni qué operación cuenta), para que otros endpoints lo reutilicen. `LoginAttemptLimiter` no se migró a él en esta entrega.

**Limitaciones conocidas:** detrás de una NAT compartida (escuela, oficina, red móvil) todos comparten el cupo. Además, igual que el límite de login, el contador vive en la memoria del proceso. Con varias instancias del backend cada una contaría por su lado; habría que llevarlo a un almacén compartido (Redis o una tabla).

| Archivo | Cambio |
|---|---|
| `backend/.../shared/throttle/SlidingWindowRateLimiter.java` | Nuevo. Cupo por llave en ventana deslizante, atómico, con purga periódica de llaves vencidas. |
| `backend/.../shared/throttle/RateLimitExceededException.java` | Nueva. Lleva el título, el mensaje fijo y el tiempo de espera. |
| `backend/.../platform/tenant/throttle/SignupRateLimiter.java` | Nuevo. Aplica el cupo del alta por IP (IPv6 por red `/64`). |
| `backend/.../platform/tenant/web/SignupController.java` | Consume el cupo antes de llamar a `onboard`. |
| `backend/.../shared/error/GlobalExceptionHandler.java` | Traduce la excepción a `429` con `Retry-After` y ProblemDetail. |
| `backend/src/main/resources/application.yml` | `app.signup.rate-limit.max-per-ip` y `app.signup.rate-limit.window`. |

## 3. Pruebas

Todas corren contra PostgreSQL 16 real. Cada prueba usa IPs públicas propias del rango de documentación `203.0.113.0/24`, porque el limitador es compartido por todo el contexto de pruebas.

### P1. Alta normal

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que el límite no afecta el uso legítimo. |
| Condiciones iniciales | La IP `203.0.113.11` no tiene altas recientes. |
| Procedimiento | `POST /api/v1/signup` con datos válidos y plan `FREE`. |
| Resultado esperado | `201`, la respuesta trae el slug y el restaurante existe en la base. |
| Resultado obtenido | `201`; existe exactamente un tenant con ese slug. Pasaba antes y después de la corrección. |

### P2. Rechazo al exceder el límite

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que una IP no puede registrar más restaurantes que su cupo. |
| Condiciones iniciales | La IP `203.0.113.12` ya hizo 5 altas válidas en la última hora. |
| Procedimiento | Sexto `POST /api/v1/signup` desde la misma IP con un slug nuevo. |
| Resultado esperado | `429` con `Retry-After` mayor que cero, título «Demasiadas altas», sin repetir slug ni correo, y sin crear el restaurante. |
| Resultado obtenido | **Antes:** `201`, el sexto restaurante se creó (vulnerabilidad confirmada). **Después:** `429`, `Retry-After` positivo, título correcto, sin datos de la petición en el cuerpo; no existe ningún tenant con ese slug. |

### P3. Aislamiento entre clientes y cabecera falsificada

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que el bloqueo es por cliente y que no se evade inventando la IP. |
| Condiciones iniciales | La IP pública `203.0.113.13` agotó su cupo. |
| Procedimiento | (a) Desde `203.0.113.13`, alta con `X-Forwarded-For: 198.51.100.7`. (b) Desde `203.0.113.14`, alta válida. |
| Resultado esperado | (a) `429` y no se crea nada, porque desde una IP pública la cabecera se ignora. (b) `201`. |
| Resultado obtenido | **Antes:** (a) `201`, la cabecera no importaba porque no había límite. **Después:** (a) `429`, sin tenant creado; (b) `201`. |

### Pruebas complementarias

| Prueba | Qué comprueba | Resultado obtenido |
|---|---|---|
| Un `400` no consume cupo | 6 altas con slug inválido y luego una válida desde la misma IP | `400` × 6 y después `201` |
| Un `409` sí consume cupo | 5 altas con un slug ocupado y luego otra | **Antes:** `409` sin fin. **Después:** `409` × 5 y luego `429` |
| Rotar direcciones IPv6 no evade el límite | 5 altas desde 5 direcciones de `2001:db8:1:2::/64`, una sexta desde otra dirección de esa red y una desde otra red `/64` | **Sin la llave por `/64`:** la sexta da `201`. **Con ella:** `429` y la otra red `201` |
| `SignupRateLimiterTest` | Llave de IPv4 tal cual, IPv6 agrupada por `/64` (sin importar mayúsculas ni abreviaturas), IPv4 encapsulada, valores que no son IP | 5/5 en verde |
| `SlidingWindowRateLimiterTest` (reloj controlado) | Tiempo de espera exacto, recuperación al expirar la ventana, los rechazos no alargan el bloqueo, llaves independientes, 20 hilos simultáneos no superan el cupo, la purga conserva las llaves vigentes y quita las vencidas, configuración inválida rechazada | 7/7 en verde |

Pruebas automatizadas:

- `backend/src/test/java/com/callejon9/platform/SignupRateLimitTest.java` — P1, P2, P3 y las complementarias de `400`, `409` e IPv6.
- `backend/src/test/java/com/callejon9/platform/tenant/throttle/SignupRateLimiterTest.java` — llave de cupo.
- `backend/src/test/java/com/callejon9/shared/throttle/SlidingWindowRateLimiterTest.java` — reglas de la ventana.

Comando:

```powershell
cd backend; .\mvnw.cmd -B "-Dtest.database.mode=external" -DDB_PORT=5432 "-Dtest=SignupRateLimitTest,SignupRateLimiterTest,SlidingWindowRateLimiterTest" test
```

Para la evidencia visual: levantar backend y frontend según `README.md`, abrir `/signup` y registrar seis restaurantes seguidos; el sexto muestra el aviso «No se pudo completar el registro» con el mensaje del límite, y en la pestaña *Network* la respuesta es `429` con la cabecera `Retry-After`.
