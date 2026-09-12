---
name: wf-review-ships
description: Revisa un conjunto de entregas recientes de Callejón 9 para detectar regresiones, patrones de defectos, evidencia faltante y mejoras del proceso sin modificar código.
---

# Review ships

Selecciona un rango explícito de commits, PRs o entradas de `workflow/state/review-history.jsonl`. Para cada entrega contrasta intención, diff, pruebas y resultado; después busca patrones compartidos en seguridad, contratos, UX, escapes de revisión y documentación. Distingue defecto actual de oportunidad de proceso. Devuelve hallazgos priorizados y máximo tres mejoras con señal de éxito.
