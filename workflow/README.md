# Harness de Callejón 9

Este directorio contiene el estado y la documentación operativa del harness. El método es uno solo y corre en dos herramientas, cada una con sus primitivas nativas:

| Pieza | Fuente versionada | Codex | Claude Code |
|---|---|---|---|
| Instrucciones persistentes | `AGENTS.md` | `AGENTS.md` | `CLAUDE.md`, que lo importa |
| Skills del flujo | `.agents/skills/` | `.agents/skills/` · `$wf-build` | `.claude/skills/` · `/wf-build` |
| Especialistas | `.agents/agents/` | `.codex/agents/*.toml` | `.claude/agents/*.md` |
| Conexión a Linear | — | `.codex/config.toml` | `.mcp.json` |
| Protocolo y estado | `workflow/` | igual | igual |

`.agents/` es la fuente de verdad. `.claude/skills/` y `.claude/agents/` son un espejo ignorado por git que se regenera con `.\scripts\sync-claude-harness.ps1` (con `-Check` solo verifica). Editar dentro de `.claude/` pierde el cambio: se edita en `.agents/` y se sincroniza.

## Inicio rápido

1. Lee `AGENTS.md` y `workflow/project-config.yaml`.
2. Si la tarea viene de Linear, autentica el servidor `linear_callejon9` —`codex mcp login linear_callejon9` en Codex, `/mcp` en Claude Code— y confirma workspace y team en la configuración.
3. Convierte la petición en un brief verificable con `pm-create-issue` o valida el existente con `pm-validate-issue`.
4. Implementa con `wf-build`, revisa con `wf-review` y prepara la entrega con `wf-ship`.

Las skills se citan aquí y en `workflow/workflow.md` con la notación `$nombre`; en Claude Code se invocan como `/nombre`. El nombre y el contenido son los mismos.

El harness no conserva historia ni decisiones del proyecto que sirvió como plantilla. La fuente de producto inicial es el estado real documentado en `README.md` y `docs/`.
