# Evidencia UT · CAL-122 — Usuarios y roles

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-122 — UT · Usuarios y roles](https://linear.app/callejon19/issue/CAL-122/ut-usuarios-y-roles) |
| Issue padre | [CAL-119 — UT · Cerrar los huecos de pruebas unitarias por módulo](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) |
| Tipo / módulo | UT · Usuarios y roles |
| Entorno | Local, backend |
| Rama | chore/cal-122-user-limits-ut |
| Base de trabajo | origin/main en d31fd0c |
| Fecha y hora de ejecución | 2026-10-22 10:25:20, America/Mexico_City |
| Estado de Linear consultado | Todo; no se modificó el issue |

## 2. Alcance

Se agregó una UT aislada para PlanLimitService.assertCanAddUser, con repositorios simulados. Cubre un cupo disponible, el límite exacto y un conteo por encima del límite.

No se agregó comportamiento de producción ni pruebas SIT. El cambio de rol y la edición de contraseña no tienen operaciones en el servicio/controlador actual, así que esas dos condiciones del issue quedan pendientes de que exista esa funcionalidad. La regla general de contraseña con datos personales ya tiene cobertura unitaria en PasswordPolicyTest; el caso de edición de usuario no existe todavía.

## 3. Casos ejecutados

| Caso | Conteo / tope | Esperado | Obtenido |
|---|---:|---|---|
| C01 — queda un cupo | 2 / 3 | Permitir la operación | Aprobado |
| C02 — justo en el tope | 3 / 3 | Rechazar con BusinessRuleException | Aprobado |
| C03 — por encima del tope | 4 / 3 | Rechazar con BusinessRuleException | Aprobado |

Resultado de Surefire: **3 pruebas, 0 fallos, 0 errores, 0 omitidas**; 2.192 s de ejecución de la clase. Maven terminó con BUILD SUCCESS en 17.549 s.

El informe Word está en [CAL-122-evidencia-UT.docx](CAL-122-evidencia-UT.docx). La salida de Surefire se conserva en [respuestas/C01-surefire.txt](respuestas/C01-surefire.txt) y en XML resumido sin propiedades del equipo en [respuestas/C01-surefire-resumen.xml](respuestas/C01-surefire-resumen.xml). La imagen capturas/C01-salida-maven.png es una representación visual del resultado real del log; no es una captura literal del escritorio.

## 4. Cómo ejecutar

Desde la raíz del worktree de esta rama, en PowerShell:

    Set-Location backend
    .\mvnw.cmd -B -Dtest=PlanLimitServiceTest test

La prueba usa JUnit y Mockito, no levanta Spring ni PostgreSQL y **no requiere Docker**. En el entorno usado para esta ejecución, Java reportó user.home=C:\; por eso Maven necesitó que se le indicara una caché local existente. En una máquina que ya tenga Maven configurado, el comando anterior es suficiente. Si Maven intenta crear la caché en C:\.m2, usa esta alternativa en PowerShell:

    .\mvnw.cmd -B "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Dtest=PlanLimitServiceTest test

## 5. Evidencia y límites

- Prueba ejecutada: backend/src/test/java/com/callejon9/platform/tenant/service/PlanLimitServiceTest.java.
- Resultado local: PASS, 3/3.
- Esta evidencia corresponde a la ejecución local; el estado del CI y la revisión se consulta en el PR.
- mvnw verify y las pruebas de integración no se ejecutaron; el alcance de esta entrega es la UT focalizada.
- CAL-122 permanece abierto: el test del límite está cubierto en UT, pero el cambio de rol y la contraseña durante edición siguen sin una operación de producto que pueda probarse.

La ejecución es local y reproducible con el comando indicado. La imagen solo facilita la lectura; el reporte Surefire es la evidencia verificable.