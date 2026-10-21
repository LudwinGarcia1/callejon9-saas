---
name: wf-review
description: Revisa una implementación o diff de Callejón 9 contra main, sus criterios e invariantes, con hallazgos priorizados y evidencia; no corrige salvo petición explícita.
---

# Review

Sigue `workflow/workflow.md`, sección `$wf-review`, y aplica `$skill-code-review`. Mantén la revisión no mutante. Un check verde no sustituye inspección y una prueba no ejecutada nunca cuenta como evidencia. Al cerrar, registra el veredicto en `workflow/state/review-history.jsonl`: es la condición para que el issue pase a Done. Las issues de prueba (UT, SIT, UAT) también se revisan, con los criterios de la sección «Estados de Linear». No revises un cambio que esta misma sesión escribió o corrigió: dilo y pide una sesión nueva u otro integrante. Registra `reviewer`, `reviewedHead` y `review` como indica `workflow/decisions/2026-10-20-revision-independiente.md`.
