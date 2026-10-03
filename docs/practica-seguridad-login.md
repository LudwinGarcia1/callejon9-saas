# Práctica: mecanismos de seguridad en el inicio de sesión

Funcionalidad elegida: **inicio de sesión** (`/login` → `POST /api/v1/auth/login`). Es la pantalla que usan meseros, cocina y caja desde tablet o teléfono, y es la única que envía una credencial al servicio.

## 1. Flujo de información

```
Tablet / teléfono (Next.js)                API (Spring Boot)                  PostgreSQL 16
───────────────────────────                ─────────────────                  ─────────────
Formulario: slug, correo, contraseña
  │ validateLogin() ── si falla, no sale nada
  │
  │ POST /api/v1/auth/login  (ruta relativa;
  │ next.config.ts la reenvía al backend)
  └──────────────────────────────────────► @Valid LoginRequest ── 400 si falla
                                           AuthService.authenticate()
                                             ├─ busca tenant por slug ─────────► tenants
                                             ├─ set_config('app.tenant_id') ───► users (RLS)
                                             └─ bcrypt.matches()
  ◄────────────────────────────────────── 200 + Set-Cookie access_token
                                           (HttpOnly, SameSite=Strict, Secure*)
                                           cuerpo: id, nombre, rol
                                        ó  401 sin cuerpo
```

| Pregunta | Respuesta |
|---|---|
| Qué sale del dispositivo | Identificador del restaurante (slug), correo y contraseña en texto. |
| Qué recibe | Cookie `access_token` (JWT firmado) y un cuerpo con `userId`, `fullName`, `role`. Nunca el token ni el hash. |
| Qué es sensible | La contraseña (máxima), el JWT (equivale a la sesión) y el correo (dato personal). |
| Puntos de vulnerabilidad | (1) Entrada sin validar que llega a la BD y a bcrypt. (2) Mensajes de error que revelen qué dato falló o repitan la entrada. (3) Token accesible desde JavaScript o viajando por HTTP plano. (4) Contraseña que queda en el formulario tras un fallo. |

## 2. Mecanismos seleccionados y justificación

El proyecto ya tenía autenticación con JWT en cookie `HttpOnly`, autorización por rol y aislamiento por RLS. Sobre esa base se eligieron dos medidas que atacan los puntos 1, 2 y 3:

1. **Validación de entradas en cliente y servidor.** El servidor rechazaba solo campos vacíos; cualquier otra cadena (un slug con comillas, un correo sin `@`, una contraseña de megabytes) llegaba a la consulta y al cálculo de bcrypt. Se validan formato y longitud en ambos lados con las mismas reglas del registro. La validación del cliente mejora la experiencia y evita peticiones inútiles; la del servidor es la que protege, porque el cliente se puede saltar.
2. **Manejo seguro de errores y del token.** Un 401 no dice si falló el restaurante, el correo o la contraseña; los 400 dan mensajes fijos que no repiten lo que el usuario escribió; un JSON malformado ya no devuelve el mensaje interno de Jackson (que puede contener fragmentos del cuerpo, incluida la contraseña) y tampoco se escribe en el log. La contraseña se borra del formulario tras un intento fallido. El atributo `Secure` de la cookie, antes fijo en `false`, ahora se activa con `AUTH_SECURE_COOKIE=true` en cualquier entorno con HTTPS.

## 3. Implementación

| Archivo | Cambio |
|---|---|
| `backend/.../auth/web/dto/LoginRequest.java` | `@Pattern` para el slug, `@Email` y `@Size(max=180)` para el correo, `@Size(max=100)` para la contraseña, mensajes en español. Sin mínimo de contraseña para no revelar la política. |
| `backend/.../shared/error/GlobalExceptionHandler.java` | Nuevo manejador de `HttpMessageNotReadableException` con detalle fijo; solo registra el tipo de la causa. |
| `backend/.../auth/web/AuthController.java` | `Secure` configurable en login y logout; logout con los mismos atributos que login. |
| `backend/src/main/resources/application.yml` | `app.auth.secure-cookie: ${AUTH_SECURE_COOKIE:false}`. |
| `frontend/src/lib/login-validation.ts` | `validateLogin()`: recorta y normaliza el slug, recorta el correo (sin cambiar mayúsculas, porque el backend lo compara tal cual), no toca la contraseña. |
| `frontend/src/app/login/login-view.tsx` | Valida antes de enviar; si falla, la petición no sale. `noValidate` para que los mensajes sean los propios y no los del navegador. Borra la contraseña tras un error. |
| `frontend/src/components/shared/field-error.tsx` | Acepta un mensaje local además del que manda el backend. |

## 4. Pruebas

| Prueba | Acción | Resultado esperado | Resultado obtenido |
|---|---|---|---|
| P01 | Enviar campos vacíos (slug `""`, correo `"   "`, contraseña `""`) | La aplicación rechaza la información | Cliente: no envía la petición y muestra un mensaje por campo. API: `400 Validacion fallida` con `errors.slug`, `errors.email`, `errors.password`; sin cookie. |
| P02 | Introducir datos inválidos (slug `Login Test' OR '1'='1`, correo `no-es-correo`, contraseña de 101 caracteres; JSON truncado) | La aplicación muestra un mensaje controlado | `400` con mensajes fijos por campo; la respuesta no contiene la entrada ni la contraseña. El JSON truncado devuelve «El cuerpo de la solicitud no tiene un formato valido.» sin nombres de clases. |
| P03 | Consultar información autorizada (credenciales válidas) | El sistema permite la operación | `200`, cookie `access_token` `HttpOnly`; el cuerpo no contiene la contraseña ni el token. |
| P04 | Intentar acceder a información no autorizada (correo de otro restaurante; `GET /me` sin cookie) | El sistema rechaza la operación | `401` sin cuerpo en ambos casos; no se indica qué dato falló. |

Pruebas automatizadas que respaldan la tabla:

- `backend/src/test/java/com/callejon9/auth/AuthControllerTest.java` — P01, P02, P03 y P04 (login contra otro tenant), contra PostgreSQL real.
- `backend/src/test/java/com/callejon9/auth/AuthControllerMeTest.java` — P04 (`/me` sin cookie).
- `frontend/src/test/login-validation.test.ts` — P01 y P02 del lado del cliente.

Comandos:

```powershell
cd backend; .\mvnw.cmd -B test "-Dtest=AuthControllerTest,AuthControllerMeTest"
cd frontend; pnpm test
```

Para la evidencia visual: levantar backend y frontend según `README.md`, abrir `/login` en la vista móvil del navegador (DevTools → dispositivo) y capturar P01 y P02 con la pestaña *Network* abierta; en P01 no debe aparecer ninguna petición a `/api/v1/auth/login`.

## 5. Mecanismo adicional: política de contraseñas

El login autentica bien, pero hasta ahora el registro aceptaba cualquier contraseña de 8 caracteres o más, incluidas `12345678` y `password`. Una contraseña débil anula las demás defensas, porque basta con adivinarla. La política se aplica a **toda contraseña nueva**: alta de restaurante (`POST /api/v1/signup`) y alta de usuario (`POST /api/v1/users`). El login no la exige, para no revelar la política y para no dejar fuera a las cuentas creadas antes.

| Regla | Motivo |
|---|---|
| Mínimo 10 caracteres | La longitud es lo que más pesa en un ataque por fuerza bruta. |
| Máximo 72 bytes en UTF-8 | bcrypt ignora en silencio lo que pasa de 72 bytes: una contraseña más larga solo aparenta ser más fuerte. |
| Letras y números | Evita contraseñas de un solo tipo de carácter. |
| Al menos 5 caracteres distintos | Descarta `aaaaaaaaa1` o `abab1212abab`. |
| No figura en la lista de contraseñas comunes, ni siquiera quitando los dígitos y símbolos del final | `Password2024!` se reduce a `password`; la lista incluye términos en español y del dominio (`restaurante`, `mesero`, `callejon9`). |
| No contiene el nombre, la parte local del correo, el nombre del restaurante ni su identificador | Son los primeros datos que prueba un atacante que conoce a la víctima. |

Los mensajes son fijos, dicen solo la primera regla que falla y nunca repiten la contraseña ni el dato personal con el que coincidió.

| Archivo | Cambio |
|---|---|
| `backend/.../auth/password/PasswordPolicy.java` | Reglas y lista de contraseñas comunes; sin dependencias de Spring, para probarlas de forma unitaria. |
| `backend/.../auth/password/StrongPassword.java` y `StrongPasswordValidator.java` | Restricción de Bean Validation a nivel de clase. Necesita ver el correo y el nombre, pero reporta el error en `errors.password`. |
| `backend/src/main/resources/security/common-passwords.txt` | Lista de contraseñas comunes; solo vive en el servidor. |
| `SignupRequest.java`, `CreateUserRequest.java` | `@StrongPassword` sustituye a `@Size(min = 8, max = 100)`. |
| `frontend/src/lib/password-policy.ts` | Las mismas reglas, sin la lista, para dar respuesta inmediata. |
| `frontend/src/components/shared/password-requirements.tsx` | Lista de requisitos que se marca mientras se escribe. |
| `signup-view.tsx`, `create-user-dialog.tsx` | Validan antes de enviar y muestran la lista; `autoComplete="new-password"` para que el gestor de contraseñas proponga una. |

| Prueba | Acción | Resultado esperado | Resultado obtenido |
|---|---|---|---|
| P05 | Registrar un restaurante con `Password2024!` | Se rechaza y no se crea nada | `400` con `errors.password` = «Esa contrasena es demasiado comun.»; la respuesta no repite la contraseña; no se crea el tenant. |
| P06 | Registrar con una contraseña que contiene el apellido del administrador | Se rechaza | `400` con el mensaje de datos personales. |
| P07 | Dar de alta un usuario con `qwerty123456` | Se rechaza | `400`; el usuario no queda creado. |
| P08 | Registrar con `Mantel-Azul-47` | Se acepta | `201` y el restaurante queda creado. |

Respaldo automatizado: `PasswordPolicyTest` (reglas), `SignupPasswordPolicyTest` (P05, P06, P08), `UserControllerTest` (P07) y `frontend/src/test/password-policy.test.ts`.

## 6. Mecanismo adicional: límite de intentos de login

Sin límite, un atacante podía probar contraseñas sin freno contra una cuenta, o una contraseña común contra muchas cuentas (*password spraying*). `LoginAttemptLimiter` cuenta los intentos fallidos (los `401`) en una ventana deslizante de 15 minutos con dos llaves independientes:

| Llave | Límite | Qué frena |
|---|---|---|
| Cuenta: restaurante + correo, sin distinguir mayúsculas | 5 fallos | Muchas contraseñas contra la misma persona, aunque el atacante cambie de IP. |
| IP del cliente | 20 fallos | Una contraseña común contra muchas cuentas. Es más alto porque varias tablets de un restaurante comparten red. |

Decisiones de diseño:

- **Se revisa antes de bcrypt.** Mientras dure el bloqueo, ni la contraseña correcta entra, y no se gasta cómputo en un intento ya rechazado.
- **`429 Too Many Requests` con `Retry-After`.** El mensaje es fijo («Demasiados intentos fallidos. Intenta de nuevo en N minutos.») y no dice si el bloqueo es por cuenta o por IP.
- **Una cuenta inexistente se bloquea igual que una real.** La llave se arma con lo que se envió, así que el límite no sirve para descubrir correos válidos.
- **Un acierto limpia el contador de la cuenta, no el de la IP.** Así un atacante con cuenta propia no puede reiniciarlo intercalando logins válidos.
- **Un `400` por formato inválido no cuenta**, porque nunca llega a probar una contraseña.
- **IP real detrás de Next.js.** Todas las peticiones llegan desde el servidor de Next, que agrega la IP del navegador al final de `X-Forwarded-For`. Esa cabecera solo se cree cuando la conexión viene de la red local (loopback o privada) y se toma la última entrada; si no, cualquiera podría inventarse una IP.
- **Bloqueo temporal, no permanente.** Un atacante no puede dejar a un mesero sin acceso indefinidamente; como mucho lo retrasa 15 minutos.

Configuración en `application.yml`: `AUTH_LOGIN_MAX_FAILURES_ACCOUNT` (5), `AUTH_LOGIN_MAX_FAILURES_IP` (20) y `AUTH_LOGIN_WINDOW` (15m).

**Limitación conocida:** el contador vive en la memoria del proceso. Con una sola instancia del backend es suficiente. Con varias, cada una contaría por su lado y habría que llevarlo a un almacén compartido (Redis o una tabla).

| Archivo | Cambio |
|---|---|
| `backend/.../auth/throttle/LoginAttemptLimiter.java` | Contadores por cuenta y por IP con ventana deslizante; purga periódica de llaves vencidas. |
| `backend/.../auth/throttle/ClientIp.java` | Resuelve la IP real detrás del proxy de Next sin confiar en cabeceras de origen externo. |
| `backend/.../auth/web/AuthController.java` | Revisa el límite antes de autenticar, registra fallos y aciertos y responde `429` con `Retry-After`. |
| `frontend/src/app/login/login-view.tsx` | Título «Demasiados intentos» para el `429`; el detalle viene del backend. |

| Prueba | Acción | Resultado esperado | Resultado obtenido |
|---|---|---|---|
| P09 | 5 contraseñas incorrectas y luego la correcta | Se bloquea | `429`, `Retry-After`, sin cookie; sigue bloqueada desde otra IP o variando mayúsculas en el correo. |
| P10 | 5 fallos contra una cuenta inexistente | Mismo comportamiento que una real | `429` con el mismo título. |
| P11 | 20 cuentas distintas desde una IP | Se bloquea esa IP | `429` desde esa IP incluso para una cuenta sin fallos; desde otra IP entra con `200`. |

Respaldo automatizado: `LoginAttemptLimiterTest` (ventana y llaves con reloj controlado), `ClientIpTest` (cabecera de proxy) y `LoginRateLimitTest` (P09–P11, acierto que reinicia el contador y `400` que no cuenta), contra PostgreSQL real.
