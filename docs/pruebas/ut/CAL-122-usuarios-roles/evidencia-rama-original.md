> Registro historico de la ejecucion en `chore/cal-122-user-limits-ut` antes de integrar main. Las fechas y resultados corresponden a esa ejecucion; no representan una nueva verificacion ni sustituyen la evidencia de main.

# Evidencia UT · CAL-122 — Usuarios y roles

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-122 — UT · Usuarios y roles](https://linear.app/callejon19/issue/CAL-122/ut-usuarios-y-roles) |
| Issue padre | [CAL-119 — UT · Cerrar los huecos de pruebas unitarias por módulo](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) |
| Tipo | UT · backend |
| Rama | `chore/cal-122-user-limits-ut` |
| Base | `origin/main` en `d31fd0c` |
| Ejecución focalizada | 2026-10-22, JDK 25.0.2 |
| Estado de Linear | No se cambió desde este trabajo |

## 2. Alcance implementado

Se completaron las tres reglas del issue:

- Cambio de rol de una cuenta existente; revoca sesiones anteriores y conserva la regla del último administrador activo. El login emite el rol vigente de la cuenta bloqueada en la transacción.
- Cambio de contraseña validado contra los datos guardados del usuario y del restaurante. Una contraseña aceptada se almacena con el codificador configurado y revoca sesiones previas.
- Alta de usuarios dentro, en el límite y por encima del cupo del plan.

Se añadieron rutas backend `PATCH /api/v1/users/{id}/role` y `PATCH /api/v1/users/{id}/password`, y se actualizaron la guía API, OpenAPI y la matriz de autorización. No se implementó UI ni se agregaron pruebas SIT.

## 3. Casos ejecutados

| Clase | Cobertura | Resultado |
|---|---|---:|
| `PlanLimitServiceTest` | Hay cupo, cupo exacto y exceso del límite | 3/3 |
| `UserServiceTest` | Cambio de rol, último ADMIN, SUPER_ADMIN, operación repetida, contraseña válida, débil, con nombre, con slug y usuario inexistente | 11/11 |
| `AuthServiceCurrentRoleTest` | El siguiente login usa el rol actual del usuario | 1/1 |
| `RefreshTokenServiceIssueForLoginTest` | El JWT se genera con los datos del usuario bajo bloqueo | 1/1 |
| **Total** |  | **16/16 aprobadas** |

Surefire reportó **16 pruebas, 0 fallos, 0 errores y 0 omitidas**. Maven terminó con `BUILD SUCCESS` en 24.410 s; el tiempo agregado de ejecución de las clases fue 3.197 s.

## 4. Cómo ejecutar

Desde PowerShell, en un entorno Java 21:

    Set-Location backend
    .\mvnw.cmd -B "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dtest=UserServiceTest,AuthServiceCurrentRoleTest,RefreshTokenServiceIssueForLoginTest,PlanLimitServiceTest" test

La ejecución local usó JDK 25.0.2 porque el JDK 26 disponible no es compatible con la versión de Byte Buddy fijada por el proyecto. Para reproducir en ese entorno, ejecuté las mismas clases con `-DforkCount=0` y `JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`. No requiere Docker ni una base PostgreSQL: las clases ejecutadas usan JUnit y dobles de prueba.

## 5. Evidencia y límites

- TXT por clase: [respuestas/C02-surefire.txt](respuestas/C02-surefire.txt).
- XML agregado sin propiedades locales: [respuestas/C02-surefire-resumen.xml](respuestas/C02-surefire-resumen.xml).
- Imagen de lectura del resumen real de Surefire: [capturas/C02-salida-maven.png](capturas/C02-salida-maven.png). Es una representación del reporte, no una captura literal del escritorio.
- Informe Word: [CAL-122-evidencia-UT.docx](CAL-122-evidencia-UT.docx).
- `mvnw verify`, CI y SIT no se ejecutaron localmente; la ejecución cubre únicamente las UT indicadas.
- No se modificó manualmente el estado de CAL-122 en Linear.