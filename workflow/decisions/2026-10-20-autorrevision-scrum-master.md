# Autorrevisión del Scrum Master en cambios de proceso

- **Estado:** aceptada
- **Fecha:** 2026-10-20
- **Decide:** Octavio Duarte, Scrum Master del equipo
- **Issues:** CAL-117, CAL-118 (contexto: PR #68, plantilla de evidencia de pruebas)

## Contexto

`workflow/workflow.md` exige que ningún issue pase a Done sin una revisión aprobada, y que una issue de prueba la revise alguien distinto de quien la ejecutó. Los cambios al propio proceso —flujo de trabajo, plantillas y guías del harness— los mantiene el Scrum Master, y esperar a otro integrante para cada ajuste de proceso retrasa el ciclo sin aportar una revisión técnica.

Hechos que condicionan la decisión:

- GitHub no permite que el autor apruebe su propio PR, así que una autorrevisión no puede quedar como aprobación en GitHub; solo puede quedar en `workflow/state/review-history.jsonl`.
- Hoy `main` no tiene protección de rama. Si se activa exigiendo una aprobación, un PR autorrevisado solo podrá fusionarlo un administrador exento de la regla.
- En Scrum, aceptar entregables corresponde al Product Owner y revisar código al equipo de desarrollo; el Scrum Master facilita el proceso. La excepción se limita por eso al proceso, que es lo que el Scrum Master mantiene.

## Opciones consideradas

| Opción | Consecuencia |
|---|---|
| Sin excepción: todo cambio lo revisa otro integrante | Máxima independencia; los ajustes de proceso esperan a otro integrante |
| Autorrevisión del Scrum Master en cualquier cambio | Rápido; elimina la revisión independiente justo donde protege la seguridad y la honestidad de la evidencia |
| Autorrevisión del Scrum Master solo en cambios de proceso, registrada | Los ajustes de proceso no se bloquean; código, datos y evidencias siguen con revisión independiente |

## Decisión

El Scrum Master puede autorrevisar y aceptar sus propios cambios **solo** cuando el cambio toca exclusivamente archivos de proceso y documentación del harness:

- `workflow/` (flujo, configuración, decisiones, plan, producto y estado), salvo las evidencias de prueba que se guarden ahí.
- `docs/pruebas/README.md` y `docs/pruebas/plantilla-evidencia.md`.
- `.agents/skills/`, `.agents/agents/`, `.agents/reference/` y `.agents/templates/`.
- `AGENTS.md`, `CLAUDE.md` y `README.md` cuando el cambio sea de proceso.

La excepción **no** aplica, aunque el cambio sea pequeño, a:

- Código de backend, frontend o app móvil, migraciones Flyway, RLS, privilegios de base de datos, autenticación o autorización.
- Infraestructura (`infra/`, Terraform, Jenkins, Docker, CI).
- Evidencias de pruebas UT, SIT o UAT, incluidas las carpetas de `docs/pruebas/<tipo>/`.
- Un PR que mezcle archivos de proceso con cualquiera de los anteriores.

Cada autorrevisión se registra en `workflow/state/review-history.jsonl` con `"review": "autorrevisión del Scrum Master; aprobable"`, el motivo y la lista de archivos revisados. Sin ese registro el cambio no cuenta como revisado.

## Consecuencias

- Los cambios de proceso pueden pasar a Done sin esperar a otro integrante, con trazabilidad explícita.
- `$wf-health` acepta una autorrevisión solo si todos los archivos del cambio están dentro del alcance; si no, la reporta como revisión faltante.
- La revisión independiente se conserva donde protege el aislamiento entre restaurantes, la seguridad y la honestidad de la evidencia.

## Señales para revisarla

- Una autorrevisión deja pasar un defecto de proceso que otro integrante habría visto.
- Se activa la protección de `main` exigiendo aprobaciones y el Scrum Master no está exento.
- El equipo designa un Product Owner o un revisor de proceso disponible en el mismo ciclo.
