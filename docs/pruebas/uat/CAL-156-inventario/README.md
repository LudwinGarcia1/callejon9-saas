# Evidencia de prueba · CAL-156 — UAT · Inventario

> **Estado: Ejecución declarada por el usuario el 2026-10-29; resultados por caso y aceptación pendientes.** La declaración general de ejecución no confirma por sí sola que todos los criterios hayan pasado. Se recibió el documento Word adjunto; su contenido queda pendiente de revisión independiente.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-156 — UAT · Inventario](https://linear.app/callejon19/issue/CAL-156) |
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
| Participante | P1 — Cocinero y administrador; perfil por confirmar sin datos personales |
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
| La existencia mostrada coincide con los movimientos registrados. | C01 |
| La existencia mostrada coincide con los movimientos registrados. | C02 |
| El administrador identifica qué insumos están por debajo del mínimo. | C03 |

**Fuera de alcance:** declarar aprobación sin resultados y aceptación, sustituir la app móvil por Chrome Android o atribuir el ensayo local a Azure.

## 5. Preparación

- Confirmar versión servida, disponibilidad de API/frontend y cuentas del mismo restaurante.
- Registrar participantes, dispositivos, folios y horarios reales.
- Usar datos ficticios y sesiones separadas por rol.

Los valores 10 kg iniciales, salida de 2 kg, conteo físico 7 kg y mínimo 8 kg son los datos propuestos en las instrucciones; falta confirmar los valores realmente observados.

## 6. Casos y resultados

### C01 — Salida y existencia

**Pasos y esperado:** Preparar Insumo prueba CAL-156 con unidad kg, mínimo 8, costo unitario ficticio 20 y stock inicial 10. Cocina registra Salida de 2. Esperado: 8 kg y movimiento de salida de 2; verificar desde administración.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** Captura individual pendiente de indexar; consultar el Word adjunto. Nombre previsto: `capturas/C01-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C02 — Ajuste por conteo

**Pasos y esperado:** Administrador abre Movimiento, selecciona Tipo Ajuste y captura Conteo físico 7 y motivo. Esperado: existencia 7 kg y diferencia -1 kg. Capturar 7, no -1, en Conteo físico.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** Captura individual pendiente de indexar; consultar el Word adjunto. Nombre previsto: `capturas/C02-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C03 — Bajo mínimo

**Pasos y esperado:** Comparar existencia 7 kg con mínimo 8 kg y comprobar indicador de stock bajo. Si el mínimo no aparece en la tabla, consultarlo en Editar sin guardar cambios.

**Ejecución:** Ejecutada según declaración general; resultado específico pendiente.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** Captura individual pendiente de indexar; consultar el Word adjunto. Nombre previsto: `capturas/C03-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

## 7. Defectos

No se recibieron descripciones de fallos de esta ejecución. Esto no equivale a ausencia de defectos. Completar observación, severidad, issue y re-prueba cuando corresponda.

## 8. Resultado y aceptación

Consultar [registro-aceptacion.md](registro-aceptacion.md). El resultado global no se declara aprobado hasta completar los resultados por caso y la aceptación del participante.

## 9. Autenticidad y límites

El usuario manifestó que realizó esta prueba. Se registra su declaración; el agente no observó la ejecución; el Word adjunto se conserva íntegro y queda pendiente de revisión. No se inventan resultados, tiempos ni aprobación.

## 10. Revisión y cierre

Revisor, fecha, hallazgos y veredicto pendientes. Según [workflow/workflow.md](../../../../workflow/workflow.md), Done requiere evidencia completa revisada, aprobación independiente y PR integrado en main. La publicación no cierra el issue automáticamente.

## Documento Word adjunto

[Descargar documento de evidencia CAL-156](Evidencia_UAT_CAL-156_C01_C02.docx)

Archivo aportado por el usuario y conservado sin modificaciones. La carga del archivo no representa una revisión aprobada ni cambia el estado del issue. Los resultados y capturas por caso deben contrastarse con este documento durante la revisión.
