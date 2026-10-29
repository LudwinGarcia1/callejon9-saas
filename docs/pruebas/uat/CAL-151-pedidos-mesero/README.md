# Evidencia de prueba · CAL-151 — UAT · Pedidos del mesero

> **Estado: Guion preparado; ejecución y resultados pendientes de confirmación.** La declaración general de ejecución no confirma por sí sola que todos los criterios hayan pasado. No se recibieron capturas ni resultados específicos de cada caso.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-151 — UAT · Pedidos del mesero](https://linear.app/callejon19/issue/CAL-151) |
| Padre | [CAL-117 — UAT por rol](https://linear.app/callejon19/issue/CAL-117) |
| Entorno coordinado | Local: PC y Chrome en Android; no demuestra Azure |
| Restaurante ficticio | `demo-15-datos` |
| Frontend PC / Android | `http://localhost:3000` / `http://192.168.1.10:3000`; confirmar IP vigente |
| API desde navegador | Proxy relativo `/api/v1/*` |
| Versión realmente servida | Pendiente para frontend y backend; no inferir del HEAD ni del nombre del JAR |
| Dispositivos | PC y Android; modelo, navegador y versiones pendientes |
| Confirmación de ejecución | Pendiente |

Formato: [guía de pruebas](../../README.md) y [plantilla](../../plantilla-evidencia.md). No guardar contraseñas, cookies ni datos personales.

## 2. Responsables

| Rol | Responsable |
|---|---|
| Asignación consultada en Linear | Braulio Hernández Mejía |
| Ejecutor | Persona usuaria de esta sesión; coordinación pendiente de completar |
| Participante | P1 — Mesero; perfil por confirmar sin datos personales |
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
| Captura el pedido completo sin ayuda. | C01 |
| Si se equivoca antes de enviar a cocina, puede corregir o cancelar. | C02 |
| Recibe folio y total, y la mesa queda ocupada. | C03 |

**Fuera de alcance:** declarar aprobación sin resultados y aceptación, sustituir la app móvil por Chrome Android o atribuir el ensayo local a Azure.

## 5. Preparación

- Confirmar versión servida, disponibilidad de API/frontend y cuentas del mismo restaurante.
- Registrar participantes, dispositivos, folios y horarios reales.
- Usar datos ficticios y sesiones separadas por rol.

Las notas y la corrección dependen de la versión servida. El PR [#111](https://github.com/LudwinGarcia1/callejon9-saas/pull/111) contiene cambios relacionados; comprobar integración y ejecución antes de atribuir resultados.

## 6. Casos y resultados

### C01 — Captura completa

**Pasos y esperado:** Abrir una mesa libre para cuatro comensales; agregar productos, cantidades y notas. Contrastar los datos de la comanda.

**Ejecución:** Pendiente de ejecución o confirmación.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C01-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C02 — Corrección y cancelación

**Pasos y esperado:** Antes del envío, corregir cantidad o quitar una línea. Probar cancelación en una orden separada y comprobar que libera su mesa.

**Ejecución:** Pendiente de ejecución o confirmación.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C02-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

### C03 — Envío confirmado

**Pasos y esperado:** Enviar una sola vez, anotar folio y total y comprobar en Mesas que la misma orden conserva sus datos y la mesa está ocupada.

**Ejecución:** Pendiente de ejecución o confirmación.

**Resultado observado / folio / fecha real:** Pendiente.

**Evidencia real:** No adjunta. Nombre previsto: `capturas/C03-resultado.png`; ese archivo todavía no existe.

**Aceptación del participante:** Pendiente.

## 7. Defectos

No se recibieron descripciones de fallos de esta ejecución. Esto no equivale a ausencia de defectos. Completar observación, severidad, issue y re-prueba cuando corresponda.

## 8. Resultado y aceptación

Consultar [registro-aceptacion.md](registro-aceptacion.md). El resultado global no se declara aprobado hasta completar los resultados por caso y la aceptación del participante.

## 9. Autenticidad y límites

Este documento contiene instrucciones y esperados; no se recibió confirmación de ejecución de esta prueba.

## 10. Revisión y cierre

Revisor, fecha, hallazgos y veredicto pendientes. Según [workflow/workflow.md](../../../../workflow/workflow.md), Done requiere evidencia completa revisada, aprobación independiente y PR integrado en main. La publicación no cierra el issue automáticamente.
