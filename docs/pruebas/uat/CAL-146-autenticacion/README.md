# Evidencia de prueba · CAL-146 — UAT · Autenticación y sesiones

> **Estado: preparada, sin ejecutar.** Este documento contiene el guion de la sesión y los casos listos para llenarse **durante** la ejecución. Ningún caso se ha ejecutado; los resultados obtenidos se dejan vacíos a propósito.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-146](https://linear.app/callejon19/issue/CAL-146) — UAT · Autenticación y sesiones |
| Historia o issue padre | [CAL-117](https://linear.app/callejon19/issue/CAL-117) — Ejecutar pruebas de aceptación (UAT) con usuarios por rol |
| Tipo de prueba | UAT (escenario UAT-01) |
| Módulo | Autenticación y sesiones |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado | Frontend `e6a6782` (de `main`). API `fb36760` (rama `fix/cal-169-hotfix-azure`, **no es de `main`**). Se confirma el día de la sesión. |
| Registro de despliegue | Comentarios «Despliegue» en [CAL-118](https://linear.app/callejon19/issue/CAL-118): frontend del 2026-10-22 y API del 2026-10-28 10:52–10:56 |
| Ejecución de CI | No aplica (UAT manual) |
| Navegador / dispositivo | Por definir el día de la sesión |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v0 · 2026-10-28 · guion preparado, sin ejecutar |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba (facilitador) | Por definir | Integrante que conduce la sesión y toma capturas; no es participante |
| Revisor | Por definir | **Distinto del ejecutor.** Llena la sección 10 |
| Participantes (UAT) | P1 — administrador · P2 — mesero · P3 — cocinero · P4 — cajero | **Externos al equipo** (`docs/pruebas/README.md`). Perfiles por definir; sin nombres ni datos personales en el repositorio |

## 3. Fechas

Todas las horas en `America/Mexico_City`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Issue padre (CAL-117) | 2026-10-20 | 2026-10-26 | 2026-10-21 | | Abierto; la UAT está bloqueada por CAL-169 (ver sección 5) |
| Esta tarea (CAL-146) | 2026-10-22 | 2026-10-23 | | | Sin iniciar: bloqueada por CAL-169 |
| Preparación del entorno y datos | 2026-10-28 | | 2026-10-28 | | Este guion |
| Ejecución de los casos | Tras la re-prueba de CAL-169 | | | | |
| Re-prueba de defectos *(si hubo)* | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que una persona de cada rol del restaurante `pruebas` entra a su pantalla de trabajo, entiende el aviso cuando se equivoca de contraseña y no puede regresar a la sesión cerrada con el botón «atrás».

**Fuera de alcance.**
- La renovación del token y el freno de intentos (429); los cubre la SIT [CAL-133](https://linear.app/callejon19/issue/CAL-133).
- El freno por IP, que corrige [CAL-169](https://linear.app/callejon19/issue/CAL-169).
- El súper administrador (`/platform`).
- La app móvil, que cubre CAL-152.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Cada rol llega directo a su pantalla tras iniciar sesión. | C01, C02, C03, C04 |
| 2. Con una contraseña equivocada ve un mensaje claro y la contraseña no queda en pantalla. | C05 |
| 3. Tras cerrar sesión no puede volver con el botón «atrás». | C06 |

## 5. Preparación

**Bloqueo vigente.** La guía de pruebas dice que un defecto crítico o alto bloquea la fase hasta su re-prueba, y que una UAT no se ejecuta antes de aprobar la SIT del mismo módulo. La SIT de este módulo (CAL-133) encontró el defecto crítico CAL-167, ahora [CAL-169](https://linear.app/callejon19/issue/CAL-169). Su corrección está desplegada en la API desde el 2026-10-28, pero:
- Falta su re-prueba en Azure (fases A a C de CAL-133 C05).
- Falta configurar `APP_TRUSTED_PROXIES` con las IP de salida de restohub.

Hasta entonces todos los casos quedan ⛔ Bloqueados.

**Precondiciones del entorno** *(se marcan el día de la sesión)*

- [ ] CAL-169 re-probado en Azure y la SIT de autenticación sin defectos críticos abiertos
- [ ] Backend encendido y `/actuator/health` en `UP` *(respuesta en `respuestas/health.txt`)*
- [ ] `https://restohub.azurewebsites.net/login` accesible
- [ ] Commits desplegados iguales a los del registro de despliegue más reciente en CAL-118; si hubo otro despliegue, se actualiza la sección 1
- [ ] Las cuatro cuentas de la tabla siguiente inician sesión. El facilitador lo comprueba antes de la sesión, sin fallar contraseñas.
- [ ] Ventana de navegador nueva (sin sesión previa) por participante, con la barra de direcciones y el reloj del sistema visibles

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Restaurante | `pruebas` | Restaurante de prueba de CAL-117 |
| Cuenta ADMIN | `admin@pruebas.com` | Cuenta de prueba de CAL-117 |
| Cuenta WAITER | `mesero@pruebas.com` | Cuenta de prueba de CAL-117 |
| Cuenta KITCHEN | `cocina@pruebas.com` | Cuenta de prueba de CAL-117 |
| Cuenta CASHIER | `caja@pruebas.com` | Cuenta de prueba de CAL-117 |

> Las contraseñas las entrega el facilitador en persona. Nunca se escriben aquí, ni en capturas ni en el chat.

**Cuidado con el freno de intentos.** El backend bloquea 15 minutos una cuenta tras 5 contraseñas erróneas. Mientras `APP_TRUSTED_PROXIES` no esté configurado, también bloquea a **todos** los que entran por restohub tras 20 fallos entre todos. En C05 se hace **un solo** intento fallido por cuenta.

## 6. Casos de prueba

Comportamiento esperado tomado del código desplegado (`e6a6782`):
- **Pantalla de cada rol** (`ROLE_LANDING_PATH` en `frontend/src/hooks/use-session.ts`): ADMIN → `/admin`, WAITER → `/waiter`, KITCHEN → `/kitchen`, CASHIER → `/cashier`.
- **Login** (`frontend/src/app/login/login-view.tsx`): el botón dice «Ingresar» («Ingresando…» mientras espera). Ante una contraseña errónea: título «No se pudo iniciar sesión» y texto «Restaurante, correo o contraseña incorrectos.». El campo de contraseña se vacía tras el intento fallido.
- **Cerrar sesión:** el botón «Cerrar sesión» (`app-topbar.tsx` y `app-sidebar.tsx`) llama a `POST /api/v1/auth/logout`, borra los datos en caché y lleva a `/login`.

### C01 — El administrador llega a su pantalla al iniciar sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (P1) |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

**Precondición.** Ventana nueva sin sesión, en `https://restohub.azurewebsites.net/login`.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Escribir restaurante, correo y contraseña; pulsar «Ingresar» | `pruebas`, `admin@pruebas.com` | Entra sin pasos intermedios a `/admin` | | `capturas/C01-P01-admin.png` |
| 2 | Abrir una sección de su pantalla y volver | — | Navega sin que se le pida iniciar sesión de nuevo | | |

### C02 — El mesero llega a su pantalla al iniciar sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | WAITER (P2) |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión | `pruebas`, `mesero@pruebas.com` | Entra sin pasos intermedios a `/waiter`, la pantalla del mesero | | `capturas/C02-P01-mesero.png` |

### C03 — El cocinero llega a su pantalla al iniciar sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | KITCHEN (P3) |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión | `pruebas`, `cocina@pruebas.com` | Entra sin pasos intermedios a `/kitchen`, con el título «Cocina» | | `capturas/C03-P01-cocina.png` |

### C04 — El cajero llega a su pantalla al iniciar sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (P4) |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión | `pruebas`, `caja@pruebas.com` | Entra sin pasos intermedios a `/cashier`, la pantalla de caja | | `capturas/C04-P01-caja.png` |

### C05 — Con una contraseña equivocada ve un mensaje claro y la contraseña no queda en pantalla

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | Cada participante, con su propia cuenta |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

**Precondición.** En `/login`, sin sesión. **Un solo intento fallido por cuenta** (ver sección 5).

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Escribir su restaurante y correo con una contraseña equivocada que dicte el facilitador; pulsar «Ingresar» | Contraseña errónea | Aviso «No se pudo iniciar sesión · Restaurante, correo o contraseña incorrectos.»; sigue en `/login` | | `capturas/C05-P01-aviso.png` |
| 2 | Observar el formulario | — | El campo de contraseña quedó vacío; restaurante y correo se conservan | | `capturas/C05-P02-contrasena-vacia.png` |
| 3 | Preguntar al participante qué entendió del aviso y qué haría | — | Explica con sus palabras que algo de lo escrito está mal y vuelve a intentarlo | | Comentario en la sección 9 |
| 4 | Escribir la contraseña correcta | — | Entra a su pantalla (C01 a C04) | | |

### C06 — Tras cerrar sesión no puede volver con el botón «atrás»

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | Cada participante |
| Inicio / fin de ejecución | |
| Estado | ⛔ Bloqueado (CAL-169) |
| Defecto vinculado | — |

**Precondición.** Sesión iniciada en la pantalla de su rol.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Pulsar «Cerrar sesión» | — | Llega a `/login` | | `capturas/C06-P01-login.png` |
| 2 | Pulsar el botón «atrás» del navegador | — | No ve los datos de su pantalla anterior; termina en `/login` | | `capturas/C06-P02-atras.png` |
| 3 | Escribir en la barra de direcciones la ruta de su rol (p. ej. `/waiter`) | — | Lo lleva a `/login` | | `capturas/C06-P03-ruta-directa.png` |

**Qué observar en C06.** El paso 2 depende de cómo el navegador muestra una página del historial. Si la pantalla anterior aparece aunque sea un instante, con o sin datos, se anota literalmente y con captura; no se reintenta hasta que pase.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | | | | | |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 6 | 0 | 0 | 6 | 0 |

**Desviaciones y limitaciones.**
- La sesión no se ha realizado: está bloqueada por CAL-169 (sección 5).
- La API desplegada (`fb36760`) no es un commit de `main`; está registrada como excepción en CAL-118.
- Faltan los participantes externos, el facilitador y el revisor.

**Conclusión del ejecutor.** Sin ejecutar. Se llena solo después de la sesión real.

## 9. Anexo UAT — aceptación de usuario

Se llena una fila por participante durante la sesión.

| Campo | P1 (ADMIN) | P2 (WAITER) | P3 (KITCHEN) | P4 (CASHIER) |
|---|---|---|---|---|
| Perfil; ¿conocía el sistema? | | | | |
| Modalidad | | | | |
| Facilitador | | | | |
| Duración medida (cronómetro) | | | | |
| Ayuda brindada | | | | |
| Comentarios del participante (literales) | | | | |
| ¿Acepta el escenario? | | | | |

Hoja de aceptación firmada: se adjunta en Linear a CAL-146 y a CAL-117; no se sube al repositorio.

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL, la fecha y hora del sistema y coinciden con los pasos
- [ ] Los mensajes y rutas coinciden con el código desplegado
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Se firma después de la ejecución real. A la fecha de esta versión (v0, 2026-10-28) ningún caso se ha ejecutado.

**Ejecutor:** — · **Fecha:** —
