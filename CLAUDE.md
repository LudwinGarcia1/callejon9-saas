# Callejón 9 — guía para Claude Code

Las reglas del repositorio —invariantes, convenciones, verificación y definición de terminado— son las de `AGENTS.md`, que se importa aquí y gobierna igual que si estuviera escrito en este archivo. Este documento solo añade cómo se opera ese mismo harness desde Claude Code.

@AGENTS.md

## Dónde vive cada pieza

| Pieza | Ruta | Nota |
|---|---|---|
| Reglas del repositorio | `AGENTS.md` | Fuente única; no se duplica en este archivo |
| Skills invocables | `.claude/skills/` | Espejo generado desde `.agents/skills/` |
| Subagentes | `.claude/agents/` | Espejo generado desde `.agents/agents/*.agent.md` |
| Permisos del equipo | `.claude/settings.json` | Versionado; lo personal va en `settings.local.json` |
| Conexión a Linear | `.mcp.json` | Servidor `linear_callejon9` |
| Protocolo y estado | `workflow/` | Flujo, configuración, producto, evidencia |
| Referencias del harness | `.agents/reference/`, `.agents/templates/` | Se leen por ruta; no se espejan |

`.agents/` es la fuente de verdad y es lo que el repositorio versiona: lo leen tanto Codex como Claude Code. `.claude/skills/` y `.claude/agents/` son copia, están ignorados por git y se regeneran con `.\scripts\sync-claude-harness.ps1`. **No edites dentro de `.claude/`**: el cambio se pierde en la siguiente sincronización. Edita en `.agents/` y vuelve a correr el script. Para comprobar si el espejo quedó desalineado: `.\scripts\sync-claude-harness.ps1 -Check`.

## Flujo de trabajo

`workflow/workflow.md` define el procedimiento de cada etapa y cita las skills con la notación `$nombre`. En Claude Code esas mismas skills se invocan con `/nombre` o con la herramienta Skill; el nombre y el contenido son idénticos.

1. Lee `workflow/README.md` y `workflow/project-config.yaml`.
2. Convierte la petición en algo verificable con `/pm-create-issue`, o valida el brief existente con `/pm-validate-issue`.
3. Implementa con `/wf-build`, revisa con `/wf-review`, prepara la entrega con `/wf-ship`.
4. Para el estado del harness, `/wf-health`; para registrar una decisión, `/wf-decision`.

## Delegación

Los subagentes de `.claude/agents/` son especialistas opcionales: `architecture`, `backend-database`, `frontend`, `design-ux`, `refiner`, `planning-auditor` y `pm-orchestrator`. Delega solo cuando el usuario lo pida o cuando el trabajo sea genuinamente paralelo e independiente; el trabajo ordinario corre en este contexto, que es donde está la evidencia.

Antes de despachar uno, define tarea, archivos permitidos y formato de retorno. Al volver, el agente principal integra, revisa el diff y ejecuta las puertas finales: el resumen de un subagente no es evidencia de que una prueba pasó.

## Linear

El servidor `linear_callejon9` se declara en `.mcp.json` y se autentica con `/mcp`. El workspace y el team de Callejón 9 están verificados en `workflow/project-config.yaml §linear`. Las mutaciones —crear o mover issues, comentar, publicar— están marcadas como `ask` en `.claude/settings.json` y requieren petición explícita del usuario, no inferencia.

## Verificación en esta máquina

Las puertas de `AGENTS.md` están escritas para PowerShell, que es el shell nativo aquí. La herramienta Bash existe en paralelo y usa sintaxis POSIX: no mezcles las dos en un mismo comando. `.\mvnw.cmd` solo corre desde PowerShell.
