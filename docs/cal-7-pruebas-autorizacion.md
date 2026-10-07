# CAL-7 — contrato de autorización obligatorio

La implementación se desarrolla en `valeria`. CAL-5 ya está implementada en
`main`, cuyas dependencias se incorporaron. El contrato forma parte de la
selección habitual de Surefire: `verify` falla cuando un endpoint o sus permisos
cambian sin una decisión documentada. No modifica negocio, JWT, tenant ni RLS.

## Archivos y contrato

- `backend/src/test/resources/security/authorization-contract.json`: matriz
  versionada de 86 mappings, 56 API y 30 infraestructura, incluidos HEAD.
- `AuthorizationContractVerifier.java`: descubre mappings reales y evalúa
  filtros y anotaciones de método, clase, interfaz o anotación compuesta.
- `AuthorizationContractMatrix.java`: compara endpoints y permisos en ambas
  direcciones; la expectativa no se deriva de las anotaciones actuales.
- `AuthorizationApplicationContractTest.java`: puerta obligatoria sobre contexto,
  recursos estáticos y WebSocket.
- `AuthorizationContractTest.java`: casos positivos y negativos deliberados.

Las clases están en `backend/src/test/java/com/callejon9/config`. Las fixtures
están fuera del paquete escaneado y no se empaquetan en producción.
La matriz conserva método, ruta, params, headers, consumes, produces, roles y
justificación. `params=folio` y `application/pdf` no se pierden en la comparación.

## Garantías

- Endpoint nuevo sin decisión, decisión sin endpoint, duplicados y falta de
  justificación: fallo identificable con método, ruta y controller disponible.
- Roles ampliados o restringidos: fallo con permisos esperados y reales.
- Lista pública exacta para login, signup, health raíz, OpenAPI JSON/config y
  redirección Swagger; me, logout y otras rutas auth exigen sesión.
- Login y signup solo permiten acceso anónimo mediante POST. GET, HEAD, PUT,
  PATCH, DELETE y OPTIONS, así como sufijos y subrutas parecidos, exigen sesión.
- HEAD conserva permisos de GET. Las sondas reproducen condiciones positivas
  de parámetros, cabeceras y medios declarados por cada mapping.
- Infraestructura contrastada con matriz: error, enlaces Actuator, health
  descendiente y OpenAPI YAML conservan los filtros actuales. No se amplía la
  lista pública para conseguir un resultado verde.
- Cuatro patrones de recursos están inventariados exactamente, incluido
  swagger-initializer.js. Swagger admite GET/HEAD sin sesión; rutas parecidas
  y otros assets mantienen autenticación.
- El único handler adicional inventariado es `/ws`: requiere sesión. Las pruebas
  de realtime existentes validan handshake, JWT y suscripciones por tenant.

Los casos negativos pasan cuando detectan el error deliberado. No se deshabilitan
pruebas ni se toleran endpoints de negocio sin decisión.

## Ejecución

Desde `backend`, con PostgreSQL real y `callejon9_test` preparada:

```powershell
.\mvnw.cmd -B "-Dtest=AuthorizationApplicationContractTest,AuthorizationContractTest" test
.\mvnw.cmd -B verify
```

El wrapper Windows falla antes de iniciar Maven en esta máquina; el equivalente
usado es Maven instalado, con PostgreSQL local en 5432:

```powershell
mvn.cmd -B -DDB_PORT=5432 "-Dtest=AuthorizationApplicationContractTest,AuthorizationContractTest" test
mvn.cmd -B -DDB_PORT=5432 verify
```

Los resultados están en `target/surefire-reports/`; el contrato genera
`target/authorization-contract-audit.txt` y
`target/authorization-resource-inventory.txt`. Son artefactos ignorados por Git.
Al añadir endpoints, actualiza y justifica la matriz y sus condiciones; una API
pública nueva también necesita una entrada exacta en la lista del verificador.

## CI y entrega a CAL-87

El contrato se ejecuta automáticamente con `verify`, incluido el workflow actual.
Jenkinsfile y servidor corresponden a CAL-87/CAL-86; no se afirma una ejecución
Jenkins que todavía no ocurrió. CAL-87 debe ejecutar `./mvnw -B verify` contra
PostgreSQL real, conservar su código de salida y publicar los XML de Surefire
y los informes de contrato. Debe demostrar un resultado correcto y un fallo
inducido que deje el pipeline en rojo. Los casos negativos locales demuestran
la detección, pero no sustituyen esa evidencia operativa.

## Revalidación de autenticación — 2026-10-07

Corrección sobre `valeria@fe6161d`: `SecurityConfig` limita las excepciones
públicas de `/api/v1/auth/login` y `/api/v1/signup` a POST. Se añadieron 16 casos
de regresión para otros métodos, subrutas y sufijos; todos devuelven 401 sin
cookie. POST con cuerpo inválido sigue llegando al controller y devuelve 400.
`me` y `logout` siguen exigiendo sesión. Se actualizó `TenantFilterTest`, cuya
expectativa anterior permitía GET sobre login y provocó el único fallo de la
primera ejecución completa.

- Pruebas focalizadas: `mvn.cmd -B -DDB_PORT=5432
  -Dtest=AuthorizationMatrixTest,AuthorizationApplicationContractTest,AuthorizationContractTest test`:
  126 pruebas, sin fallos, errores ni omisiones.
- Puerta final: `mvn.cmd -B -DDB_PORT=5432 verify`: BUILD SUCCESS;
  346 pruebas, sin fallos, errores ni omisiones, finalizada a las 11:03:06
  (America/Mexico_City).
- `TenantIsolationTest`: 5 pruebas aprobadas dentro de la suite completa.
- Entorno: Java 26.0.2, Maven 3.9.16 y PostgreSQL 18.6 real, `callejon9_test`.
- El wrapper Windows falló antes de iniciar Maven; se usó Maven instalado.
- Frontend: no modificado ni reejecutado en esta corrección. Su evidencia del
  5 de octubre se conserva como validación anterior.

Evidencia de la corrección preparada para entrega en el PR #21, basada en la
ejecución local descrita arriba. No representa una nueva ejecución de CI ni
de Jenkins.

## Límites

Se prueban permisos declarativos de identidades con un rol; no todas las
combinaciones de negocio ni propiedad de objetos. RLS tiene su suite separada.
Las condiciones personalizadas de mapping se rechazan sin ampliar el modelo.
Filtros futuros dependientes de condiciones negativas, dispatcher u otras entradas
necesitan sondas adicionales: las actuales representan peticiones REQUEST.
Las cabeceras incorporadas de headers están descritas en
[cabeceras-seguridad.md](cabeceras-seguridad.md); HSTS requiere HTTPS en navegador.
