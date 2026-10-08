# CAL-88 — PostgreSQL reproducible en pruebas

Trabajo en `feat/cal-88-testcontainers`, creada desde `valeria@9ac132b`.
CAL-7 se integró mediante el PR #21. La rama incorpora
`origin/main@5bae83e` mediante avance directo y su diff contra `main`
contiene exclusivamente la entrega de CAL-88 y su trazabilidad.

## Integración

`TestDatabaseInitializer`, registrado exclusivamente en los recursos de pruebas
mediante `META-INF/spring.factories`, configura todos los contextos Spring con
perfil `test`. No exige modificar cada clase ni se empaqueta en producción.
El holder estático arranca PostgreSQL 16 una sola vez por JVM; mantiene el
contenedor mientras existan contextos y Testcontainers lo limpia al finalizar.
No se activa la reutilización persistente entre ejecuciones.

El bootstrap crea dos roles separados y sin privilegios elevados, con
contraseñas aleatorias efímeras. Flyway recibe el propietario; el datasource
recibe el rol restringido. No se modifican migraciones ni políticas RLS.

La prueba `TestDatabaseSecurityTest` verifica la identidad del datasource,
ausencia de superusuario/BYPASSRLS/creación de roles y bases, ausencia de
propiedad de tablas públicas y las 14 tablas con RLS habilitada y forzada.
`TenantIsolationTest` sigue demostrando los cinco casos de aislamiento.

## Configuración y CI

Por defecto, `./mvnw -B verify` requiere un motor Docker accesible.
Con `-Dtest.database.mode=external`, el inicializador conserva la conexión
externa y no consulta Docker. También admite `TEST_DATABASE_MODE` y
`TEST_DATABASE_URL`; las credenciales externas conservan sus variables actuales.
Un modo desconocido falla explícitamente.

El workflow actual elimina su servicio PostgreSQL y bootstrap manual: el mismo
comando utiliza Testcontainers. No existe Jenkinsfile en este checkout;
CAL-87 debe ejecutar verify en un agente con Docker y publicar Surefire.

## Evidencia y pendientes

El 2026-10-07 las seis pruebas focalizadas de seguridad y aislamiento pasaron
contra PostgreSQL externo 18.6 en 46,434 s, incluida descarga de dependencias.
La ejecución por defecto alcanzó Testcontainers 1.20.6 (versión administrada por
Spring Boot) y falló porque no encontró un entorno Docker válido. No se omitió
la prueba ni se sustituyó el contenedor por la base externa.

La referencia previa es `verify` de CAL-7: 346 pruebas en 71 s, con PostgreSQL
externo 18.6. Esta referencia no mide el coste de arranque de un contenedor.
La puerta final de CAL-88, `mvn.cmd -B "-Dtest.database.mode=external"
-DDB_PORT=5432 verify`, pasó 350 pruebas sin fallos, errores ni omisiones en
62 s (12:15:53, America/Mexico_City). Incluye tres pruebas de selección del
modo, una prueba del contrato de roles/RLS y los cinco casos de aislamiento.
Los tiempos son ejecuciones individuales y no demuestran una mejora de rendimiento.
### Validación con Docker

El 2026-10-07 se ejecutó `mvn.cmd -B verify` con Docker Desktop 29.8.2,
Java 26.0.2 y Maven 3.9.16. Ambas ejecuciones terminaron con `BUILD SUCCESS`:

| Imagen | PostgreSQL | Pruebas | Fallos / errores / omitidas | Tiempo Maven |
|---|---|---|---|---|
| Fría, incluida descarga | 16.15 (`postgres:16-alpine`) | 350 | 0 / 0 / 0 | 1 min 57 s |
| Descargada, contenedor nuevo | 16.15 (`postgres:16-alpine`) | 350 | 0 / 0 / 0 | 1 min 12 s |

Pasaron `TestDatabaseSecurityTest` y los cinco casos de `TenantIsolationTest`:
el datasource usa el rol restringido, sin propiedad de tablas ni privilegios
para eludir RLS, y las 14 tablas mantienen ENABLE/FORCE. Flyway y el bootstrap
de roles funcionaron sobre la base nueva de cada ejecución. Los tiempos son
mediciones individuales, no un benchmark. Logs locales en
`backend/target/cal-88-container-first.log` y `backend/target/cal-88-container-warm.log`.

Estas dos mediciones usaron Maven instalado porque el wrapper Windows fallaba
antes de arrancar Maven. La integración final corrige la comprobación del
destino de enlace de `.m2`: una carpeta normal no tiene `Target` y no puede
indexarse. No se ejecutó CI remoto ni se comprobó Java 21 en esta máquina.

La dependencia de CAL-7 quedó resuelta con el merge `5bae83e` del PR #21.
La validación local con Docker está completada; no se realizó commit, push
ni merge remoto de CAL-88.

### Puerta final tras integrar main

`.\mvnw.cmd -B verify` terminó con código 0 y
`BUILD SUCCESS` el 2026-10-07 a las 23:59:03 (America/Mexico_City):
350 pruebas, cero fallos, errores u omisiones, en 1 min 20 s. Incluye
el contrato de CAL-7, `TestDatabaseSecurityTest` y los cinco casos RLS.
Se utilizó PostgreSQL 16 en un contenedor nuevo y el wrapper Windows corregido.
Log local: `backend/target/cal-88-integrated-verify.log`.

El entorno local sí tiene PostgreSQL instalado, pero esta ejecución utilizó
la URL y el puerto dinámico del contenedor, sin apuntar al servicio local.
La demostración literal en una máquina sin PostgreSQL instalado y la puerta
remota Java 21 quedan pendientes de CI. No se modificó frontend.
