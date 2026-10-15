# Probes de vida y disponibilidad (CAL-22)

El backend expone dos probes para el orquestador. Sirven para que una caída de PostgreSQL saque a las instancias del balanceador sin reiniciar procesos sanos.

## Contrato

| Ruta | Pregunta que responde | Incluye | 200 `{"status":"UP"}` | 503 `{"status":"DOWN"}` |
|---|---|---|---|---|
| `GET /actuator/health/liveness` | ¿El proceso está vivo? | `livenessState` | El proceso responde HTTP | Solo si algo publica `LivenessState.BROKEN`; hoy ningún código lo hace (ver abajo) |
| `GET /actuator/health/readiness` | ¿Puede recibir tráfico? | `readinessState`, `db` | Terminó de arrancar y la base responde | Aún arrancando, apagándose o sin base; sacarlo del balanceador sin reiniciar |
| `GET /actuator/health` | Estado agregado | Todos los indicadores | Todo `UP` | Algún indicador `DOWN` |

* **Hoy, la vida equivale a «el proceso responde HTTP».** Pasa a `DOWN` solo si algo publica `LivenessState.BROKEN`, y el código no lo hace en ningún lugar. Un hilo bloqueado o un pool de conexiones agotado no la cambian mientras Tomcat atienda la petición. Si el proceso deja de responder del todo, el health check falla por timeout y el orquestador lo reinicia igual. No configures el despliegue esperando que la vida detecte esos casos sin agregar antes el código que publique `BROKEN`.
* **La vida no depende de PostgreSQL, a propósito.** Si dependiera, una caída de la base pondría todas las instancias en `DOWN` a la vez y el orquestador las reiniciaría en cadena sin arreglar nada.
* **Las respuestas solo traen el estado:** sin componentes, detalles, versiones ni mensajes de error (`show-details` y `show-components` en `never`). Con la base caída, la disponibilidad responde `{"status":"DOWN"}`, sin la URL ni el error de conexión.
* **Solo son públicas `GET /actuator/health/liveness` y `GET /actuator/health/readiness`, en su ruta exacta.** Cualquier otra ruta bajo `/actuator/health/**` y cualquier otro método (incluido `HEAD`) piden sesión y responden 401. El contrato de autorización de CAL-7 lo exige: las dos rutas están en `authorization-contract.json` y en la lista pública de `AuthorizationContractVerifier`.
* **Al empezar el apagado, Spring Boot pasa la disponibilidad a `REFUSING_TRAFFIC`.** El probe responde 503 mientras terminan las peticiones en curso. En ECS, el desregistro del target también saca la instancia del balanceador.

## Migraciones y esquema

No hace falta un probe de migraciones: si algo falla, el proceso no llega a arrancar.

* Flyway valida y aplica las migraciones al arrancar. Una migración inválida (por ejemplo, una versión duplicada) o una que falla detiene el arranque. En PostgreSQL, una migración que falla se revierte completa.
* Hibernate valida el esquema contra las entidades (`ddl-auto: validate`). Si falta una tabla o una columna, el arranque se detiene.
* En los dos casos el proceso termina con código de salida distinto de 0 y la disponibilidad nunca llega a `UP`.

Verificado el 2026-10-14:

* `StartupFailureTest` arranca la aplicación con una migración de versión duplicada y con un esquema sin tablas. Las dos fallan y la disponibilidad nunca llega a `ACCEPTING_TRAFFIC`.
* El jar real con la migración duplicada termina con `Found more than one migration with version 1` y **código de salida 1**, sin arrancar Tomcat.

## Configuración recomendada para el despliegue (CAL-53)

| Dónde | Probe | Valores sugeridos | Motivo |
|---|---|---|---|
| Health check del balanceador (target group) | `GET /actuator/health/readiness`, esperar 200 | intervalo 15 s, timeout 5 s, 2 respuestas sanas, 3 fallidas | Con la base caída, la instancia sale del balanceador en unos 45 s y vuelve sola cuando la base responde |
| Health check del contenedor | `GET /actuator/health/liveness` | intervalo 30 s, timeout 5 s, 3 reintentos, `startPeriod` 120 s | Solo reinicia si el proceso quedó roto, nunca por la base |
| Periodo de gracia del servicio (`healthCheckGracePeriodSeconds`) | — | 120–180 s | Cubre el arranque con migraciones pendientes antes de evaluar el balanceador |

Sobre los tiempos de arranque:

* En un contenedor local, con las migraciones ya aplicadas, el arranque tardó unos 18 s (2026-10-14).
* Una versión con migraciones nuevas tarda más, según cuánto modifiquen los datos. Ajusta `startPeriod` y el periodo de gracia midiendo el arranque real en el entorno.
* Con la base caída, la consulta de disponibilidad puede tardar hasta el `connection-timeout` de Hikari (30 s por defecto) en responder 503. Con un timeout de 5 s, el balanceador la cuenta como fallo antes, que es el resultado deseado.

`docker-compose.yml` y el `HEALTHCHECK` del `backend/Dockerfile` todavía usan `/actuator/health`, que incluye la base. Cambiarlos a los probes le corresponde a la configuración de contenedores y despliegue, no a CAL-22.

## Cómo comprobarlo

```powershell
curl.exe -s -w " %{http_code}`n" http://localhost:8080/actuator/health/liveness    # {"status":"UP"} 200
curl.exe -s -w " %{http_code}`n" http://localhost:8080/actuator/health/readiness   # {"status":"UP"} 200
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8080/actuator/health/db    # 401
```

Pruebas automatizadas:

* `HealthProbesTest`: cuerpos exactos y miembros de cada grupo.
* `HealthProbesDatabaseDownTest`: vida en 200 `UP` y disponibilidad en 503 `DOWN` con la base inalcanzable.
* `StartupFailureTest`: migración inválida y esquema incompatible.
* `AuthorizationMatrixTest`: rutas públicas y rutas que siguen cerradas.
* `AuthorizationContractTest`: el contrato rechaza rutas concretas mal declaradas.
