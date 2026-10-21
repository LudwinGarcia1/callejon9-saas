# Quién puede revisar un cambio

- **Estado:** aceptada
- **Fecha:** 2026-10-20
- **Decide:** Octavio Duarte, Scrum Master del equipo
- **Issues:** CAL-117, CAL-118 (contexto: PR #68, plantilla de evidencia de pruebas)

## Contexto

`workflow/workflow.md` exige que ningún issue pase a Done sin una revisión aprobada, y que una issue de prueba la revise alguien distinto de quien la ejecutó. El equipo es de cuatro personas y no siempre hay otro integrante disponible en el mismo ciclo para revisar. Además, buena parte del trabajo se hace con agentes (Claude Code y Codex), que pueden revisar con las mismas skills del harness (`$wf-review`, `$skill-code-review`).

Se consideró que el Scrum Master pudiera autorrevisar sus cambios de proceso y se descartó: una autorrevisión no es revisión independiente, y en Scrum aceptar entregables no corresponde al Scrum Master.

Hechos que condicionan la decisión:

- GitHub no permite que el autor apruebe su propio PR, y una sesión de agente opera con la cuenta de un integrante. Por eso la revisión de un agente no puede quedar como aprobación en GitHub; queda en `workflow/state/review-history.jsonl`.
- Una sesión que escribió o corrigió un cambio conoce su intención y tiende a leer lo que quiso hacer, no lo que hizo. Eso la invalida como revisora de ese mismo cambio.

## Opciones consideradas

| Opción | Consecuencia |
|---|---|
| Solo otro integrante revisa | Máxima independencia; los cambios esperan a que alguien esté disponible |
| Autorrevisión del Scrum Master en cambios de proceso | Rápido; no hay revisión independiente. Descartada |
| Otro integrante o una sesión de agente independiente | No se bloquea el ciclo y se conserva la independencia, con reglas para que la sesión sea realmente independiente |

## Decisión

Nadie revisa su propio cambio. Un cambio lo puede revisar:

1. **Otro integrante del equipo**, distinto del autor.
2. **Una sesión de agente independiente** (Claude Code o Codex) que cumpla todo lo siguiente:
   - Es una sesión nueva, sin la conversación de la sesión que escribió o corrigió el cambio. Una sesión que hizo commits en ese PR no puede revisarlo.
   - Revisa con `$wf-review` (en Claude Code, `/wf-review`) contra la rama o el PR, y contrasta el diff con el código real, no con la descripción del PR.
   - Fija el commit que revisó. Si después entra un commit nuevo, la revisión ya no aplica y se repite.

Cada revisión se registra en `workflow/state/review-history.jsonl` con:

- `reviewer`: `"integrante: <nombre>"` o `"agente independiente: Claude Code"` / `"agente independiente: Codex"`;
- `reviewedHead`: el SHA revisado;
- `review`: el veredicto (aprobable, aprobable con observaciones o cambios requeridos) y los hallazgos.

Límites de la revisión de un agente:

- No sustituye la ejecución: una prueba que el agente no pudo correr no cuenta como evidencia, y lo dice en el registro.
- En UAT, la aceptación es de las personas que participan en la sesión. El agente puede revisar que el registro esté completo y sea coherente con el código, pero no aceptar el escenario.
- Fusionar el PR sigue siendo decisión de una persona del equipo.

## Consecuencias

- Los cambios no esperan a otro integrante cuando hay una sesión de agente independiente disponible.
- `$wf-health` reporta como revisión faltante una entrada sin `reviewer`, una revisión de la misma sesión que hizo el cambio o una revisión cuyo `reviewedHead` no es el último commit del PR.
- Si se activa la protección de `main` con aprobaciones obligatorias, hará falta además la aprobación en GitHub de un integrante distinto del autor.

## Señales para revisarla

- Una revisión de agente deja pasar un defecto que un integrante habría visto, en especial en RLS, autorización o evidencia de pruebas.
- Se activa la protección de `main` con aprobaciones obligatorias.
- Los docentes piden que la revisión sea solo entre integrantes.
