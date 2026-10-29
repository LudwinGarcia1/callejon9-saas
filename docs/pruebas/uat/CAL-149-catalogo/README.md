# Evidencia de prueba · CAL-149 — UAT · Catálogo

> **Estado: Ejecución declarada por el usuario el 2026-10-29; resultados por caso y aceptación pendientes.** La declaración general de ejecución no confirma por sí sola que todos los criterios hayan pasado. No se recibieron capturas ni resultados específicos de cada caso.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-149 — UAT · Catálogo](https://linear.app/callejon19/issue/CAL-149) |
| Padre | [CAL-117 — UAT por rol](https://linear.app/callejon19/issue/CAL-117) |
| Entorno coordinado | Local: PC y Chrome en Android; no demuestra Azure |
| Restaurante ficticio | `demo-15-datos` |
| Frontend PC / Android | `http://localhost:3000` / `http://192.168.1.10:3000`; confirmar IP vigente |
| API desde navegador | Proxy relativo `/api/v1/*` |
| Versión realmente servida | Pendiente para frontend y backend; no inferir del HEAD ni del nombre del JAR |
| Dispositivos | PC y Android; modelo, navegador y versiones pendientes |
| Confirmación de ejecución | Recibida el 2026-10-29; fecha exacta de ejecución pendiente |

Formato: [guía de pruebas](../../README.md) y [plantilla](../../plantilla-evidencia.md). No guardar contraseñas, cookies ni datos personales.

## 2. Responsables

| Rol | Responsable |
|---|---|
| Asignación consultada en Linear | Valeria Mercado |
| Ejecutor | Persona usuaria de esta sesión; coordinación pendiente de completar |
| Participante | P1 — Administrador y mesero; perfil por confirmar sin datos personales |
| Revisor independiente | Pendiente; distinto del ejecutor |

## 3. Fechas

Zona: `America/Mexico_City`. La fecha de confirmación no sustituye las fechas reales.

| Actividad | Inicio planeado | Fin planeado | Inicio real | Fin real |
|---|---|---|---|---|
| Preparación | Pendiente | Pendiente | Pendiente | Pendiente |
| Ejecución | Pendiente | Pendiente | Pendiente | Pendiente |
| Revisión y re-prueba | Pendiente | Pendiente | Pendiente | Pendiente |

## 4. Alcance y criterios

| Criterio literal | Caso |
|---|---|
| Los productos aparecen en el menú del mesero con su precio. | C01 |
| El producto desactivado ya no se puede pedir. | C02 |
| El administrador puede corregir un precio sin ayuda. | C03 |

**Fuera de alcance:** declarar aprobación sin resultados y aceptación, sustituir la app móvil por Chrome Android o atribuir el ensayo local a Azure.

## 5. Preparación

- Confirmar versión servida, disponibilidad de API/frontend y cuentas del mismo restaurante.
- Registrar participantes, dispositivos, folios y horarios reales.
- Usar datos ficticios y sesiones separadas por rol.

La mesa 29 se liberó durante la coordinación para consultar productos como mesero. No se infiere su estado actual ni se presenta esa preparación como resultado UAT.

## 6. Casos y resultados

### C01 — Disponibilidad y precios

**Pasos y esperado:** Crear categoría Prueba CAL-149 y tres productos A, B y C a $30, $45 y $60. Consultar el selector de productos desde una comanda del mesero.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C01-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C02 — Producto desactivado

**Pasos y esperado:** Dar de baja Producto C UAT como administrador. Consultar de nuevo el menú del mesero y comprobar que ya no se puede seleccionar.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C02-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C03 — Corrección de precio

**Pasos y esperado:** Editar Producto A UAT de $30 a $35. Consultar el menú del mesero y comprobar $35. Registrar si el participante pudo hacerlo sin ayuda.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C03-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

## 7. Defectos

No se recibieron descripciones de fallos de esta ejecución. Esto no equivale a ausencia de defectos. Completar observación, severidad, issue y re-prueba cuando corresponda.

## 8. Resultado y aceptación

Consultar [registro-aceptacion.md](registro-aceptacion.md). El resultado global no se declara aprobado hasta completar los resultados por caso y la aceptación del participante.

## 9. Autenticidad y límites

El usuario manifestó que realizó esta prueba. Se registra su declaración; el agente no observó la ejecución ni recibió capturas o valores finales. No se inventan resultados, tiempos ni aprobación.

## 10. Revisión y cierre

Revisor, fecha, hallazgos y veredicto pendientes. Según [workflow/workflow.md](../../../../workflow/workflow.md), Done requiere evidencia completa revisada, aprobación independiente y PR integrado en main. La publicación no cierra el issue automáticamente.
