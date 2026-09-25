# CAL-7 — etapa de pruebas

Estado: implementación local de la etapa de pruebas autorizada por la usuaria. Los permisos de producción no cambian. CAL-7 completa sigue dependiendo de CAL-5 y de la integración en Jenkins de CAL-87.

## Qué se entrega

| Archivo bajo `backend/src/test/java/` | Propósito |
|---|---|
| `com/callejon9/config/AuthorizationContractVerifier.java` | Descubre mappings del contexto Spring y evalúa decisiones de autorización |
| `com/callejon9/config/AuthorizationContractTest.java` | Prueba el verificador y contrasta sus resultados con peticiones HTTP a fixtures |
| `callejon9/contractfixtures/AuthorizationFixtures.java` | Controllers de prueba con reglas válidas y errores deliberados |
| `com/callejon9/config/AuthorizationApplicationAudit.java` | Auditoría estricta y explícita de los endpoints actuales; genera un informe antes de fallar |

Las fixtures están fuera de `com.callejon9`, el paquete escaneado por la aplicación, y solo se importan en `AuthorizationContractTest`. No se empaquetan en el artefacto de producción. La auditoría comprueba además que no estén en su contexto.

## Casos cubiertos

- Endpoint descubierto desde Spring, sin anotación ni matcher documentado: rechazado con controller, método Java, HTTP y ruta.
- Anotaciones `@PreAuthorize` de método, clase, interfaz y anotación compuesta: reconocidas por el evaluador de Spring Security, no por una búsqueda de texto.
- Anotación de método que reemplaza la de clase: se verifica su permiso efectivo.
- Matcher real `/api/v1/platform/**`: reconoce SUPER_ADMIN y rechaza el resto; una cadena debilitada en el test produce un fallo del verificador.
- Ruta parecida a plataforma: no recibe esa clasificación.
- Login y signup: lista pública exacta por método y ruta, con justificación.
- Endpoint público nuevo bajo `/auth/**`, o con `permitAll()` fuera de la lista: rechazado.
- `/auth/me` y `/auth/logout`: no se aceptan como públicos. Una cadena pública de prueba falla; una que exige sesión pasa. Esto no afirma que CAL-5 ya haya corregido la configuración real.
- Peticiones MockMvc con cookies JWT generadas solo para fixtures: verifican 200/403 para los cinco roles y 401 sin cookie.
- Condición `params=folio` de tickets y descubrimiento de mappings de Actuator y OpenAPI: preservados.

Los casos negativos afirman que el verificador lanza el fallo esperado. No se deshabilitan tests ni se exceptúan controllers reales para ocultar los pendientes.

### Ajustes de portabilidad en pruebas existentes

La primera ejecución completa encontró dos errores de Mockito al instrumentar `TransactionStatus` con Java 26 y un fallo porque PostgreSQL devolvió en español el rechazo RLS esperado.

- `TenantOnboardingServiceCompensationTest` utiliza ahora un `SimpleTransactionStatus` nuevo por transacción. Conserva los mocks de repositorios y las comprobaciones de compensación; evita instrumentar una interfaz que hereda tipos del JDK.
- `TenantIsolationTest` comprueba SQLSTATE `42501` en la causa SQL en vez de buscar texto en inglés. La preparación inserta correctamente dentro de cada tenant y el test comprueba que el intento cruzado no agregó filas al otro restaurante.

Son cambios exclusivamente de pruebas; no alteran transacciones ni políticas RLS de producción. Java 21 sigue siendo la versión del proyecto.

## Cómo ejecutar

Desde la raíz del repositorio, con PostgreSQL real preparado y credenciales locales disponibles:

```powershell
cd backend
$env:DB_PORT = '5432'
.\mvnw.cmd -B "-Dtest=AuthorizationContractTest" test
```

Se utiliza `callejon9_test`, configurada en `src/test/resources/application-test.yml`. No apuntar las pruebas a la base de uso diario. El puerto 5432 corresponde a esta máquina; ajustar para otros entornos.

El wrapper local presenta `Cannot start maven from wrapper` por un acceso a matriz nula. Mientras se corrige por separado, se puede usar el Maven instalado:

```powershell
mvn -B "-DDB_PORT=5432" "-Dtest=AuthorizationContractTest" test
mvn -B "-DDB_PORT=5432" verify
```

Los informes JUnit/Surefire quedan en `backend/target/surefire-reports/`.

## Auditoría de la aplicación y dependencia CAL-5

```powershell
# Desde backend; es normal que falle mientras existan huecos de CAL-5.
mvn -B "-DDB_PORT=5432" "-Dtest=AuthorizationApplicationAudit" test
```

Genera `backend/target/authorization-contract-audit.txt` con todos los mappings descubiertos y luego falla si la API tiene huecos. Ese archivo es diagnóstico local ignorado por Git, no la matriz aprobada.

**La clase termina en `Audit`, no en `Test`: durante esta etapa se ejecuta explícitamente y no forma parte de la selección predeterminada de Surefire.** Por tanto, que `verify` pase demuestra la suite habitual y el verificador con fixtures; no demuestra que todos los endpoints de producción cumplan CAL-7. No usar esta etapa para cerrar el issue.

La auditoría no tiene una lista de huecos tolerados. Cualquier endpoint de `/api/v1/` sin decisión reconocida falla, incluidos los existentes. Los mappings de infraestructura se incluyen en el informe, pero su aprobación y clasificación completa siguen pendientes: `/error`, recursos estáticos de Swagger y condiciones especiales no se consideran automáticamente APIs públicas.


El campo `filtros/anotaciones` del informe no equivale a una respuesta HTTP final. Por ejemplo, `/auth/me` actualmente rechaza anónimos manualmente en su controller; el hallazgo señala que falta la regla declarativa esperada, no que la identidad se filtre a anónimos.

## Límites explícitos del verificador

- Reconoce `@PreAuthorize`, la lista pública documentada, las dos rutas de sesión y el matcher de plataforma verificado con la cadena real. Otras formas de autorización deben incorporarse con pruebas antes de aceptarlas.
- Prueba identidades sintéticas con un rol cada una. No ejecuta métodos de negocio ni demuestra autorización dependiente de argumentos, propiedad de objetos o combinaciones de roles.
- Conserva las condiciones de mapping en el inventario. Las sondas del filtro usan método y ruta concretizada; reglas futuras dependientes de parámetros/cabeceras necesitarán sondas específicas y deben ampliar los tests.
- El evaluador de método utiliza la configuración estándar de Spring Security del proyecto actual. Si se introduce un expression handler personalizado, habrá que conectarlo y probarlo.
- Las rutas de sesión son excepciones documentadas de autenticación, no excepciones públicas. El verificador comprueba su comportamiento, pero no introspecciona internamente el matcher que lo produjo.
- HEAD se normaliza a GET al consultar la lista pública; aún falta probar los métodos implícitos y recursos no representados como `HandlerMethod` para cerrar todo el contrato.
- Todavía no compara roles con una matriz aprobada: [la propuesta CAL-5](propuesta-matriz-autorizacion-cal-5.md) sigue siendo un borrador.

## Qué falta para cerrar CAL-7 completa

1. Aprobar e implementar CAL-5 sin ampliar artificialmente la lista pública.
2. Convertir la matriz aceptada a datos verificables y comparar en ambas direcciones endpoints y permisos, incluidas condiciones de mapping.
3. Completar la clasificación de infraestructura y pruebas de métodos implícitos.
4. Pasar la auditoría estricta y activarla por defecto en la suite de contrato; integrar esa puerta en el Jenkinsfile de CAL-87.
5. Conservar evidencia de la suite completa con PostgreSQL y del aislamiento RLS.

No se requiere cambiar frontend, migraciones, permisos RLS ni `SecurityConfig` para ejecutar esta etapa de pruebas.

## Evidencia local del 2026-09-24

Rama: `valeria`. Entorno observado: Maven 3.9.16, Java 26.0.2 (compilación con release 21), PostgreSQL 18.6, base `callejon9_test` en puerto 5432. Esta ejecución no sustituye una validación con Java 21 en CI. Flyway advierte que PostgreSQL 18 es más reciente que las versiones que declara soportadas, aunque la ejecución local finalizó correctamente.

| Comprobación | Resultado |
|---|---|
| `AuthorizationContractTest` | 21 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Pruebas focalizadas de contrato, compensación y RLS | 28 pruebas, todas pasan |
| `mvn -B "-DDB_PORT=5432" verify` | BUILD SUCCESS; 204 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Auditoría explícita | Descubre 40 mappings de API y 63 totales; rechaza 14 rutas pendientes, sin exceptuarlas |
| RLS automatizado | Las 5 pruebas de `TenantIsolationTest` pasan |
| Metadatos PostgreSQL como `callejon9_app` | 14 tablas por restaurante con ENABLE/FORCE RLS; ninguna propiedad de app; superusuario y BYPASSRLS en falso |

Logs locales ignorados por Git: `backend/target/cal7-focused.log`, `backend/target/cal7-verify.log`, `backend/target/cal7-audit.log` y `backend/target/authorization-contract-audit.txt`. El fallo de la auditoría explícita es independiente del resultado verde de `verify`: la auditoría todavía no es una puerta obligatoria y CAL-7 completa permanece pendiente.
