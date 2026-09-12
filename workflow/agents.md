# Operación de agentes

El agente principal conserva responsabilidad de extremo a extremo: entiende la petición, inspecciona el repositorio, implementa, integra, verifica y reporta. Un especialista aporta una lectura acotada; no sustituye evidencia del código ni autoriza acciones externas.

## Especialistas disponibles

La definición vive en `.agents/agents/<nombre>.agent.md`. Codex los expone en `.codex/agents/` con guion bajo; Claude Code, en `.claude/agents/` con guion medio.

| Enfoque | Codex | Claude Code | Escritura |
|---|---|---|---|
| fronteras, contratos, dependencias e invariantes | `architecture` | `architecture` | no |
| Spring, PostgreSQL, Flyway y RLS | `backend_database` | `backend-database` | sí |
| Next.js, React, API cliente y estado | `frontend` | `frontend` | sí |
| tokens, componentes, accesibilidad y experiencia | `design_ux` | `design-ux` | sí |
| cobertura y secuencia de planes | `planning_auditor` | `planning-auditor` | no |
| briefs, roadmap y trazabilidad | `pm_orchestrator` | `pm-orchestrator` | sí |
| preguntas, criterios de aceptación y riesgos | `refiner` | `refiner` | no |

Solo se delega cuando el usuario lo pide explícitamente. Antes de delegar, define una tarea independiente, archivos permitidos y formato de retorno. El agente principal revisa diffs, resuelve conflictos y ejecuta las puertas finales.

## Orden de precedencia

`AGENTS.md` —`CLAUDE.md` lo importa sin alterarlo— > alcance aceptado de la tarea > configuración de `workflow/project-config.yaml` > documentación de producto > especialidades > supuestos. Cuando dos fuentes discrepan, señala la contradicción y verifica en código o pruebas.
