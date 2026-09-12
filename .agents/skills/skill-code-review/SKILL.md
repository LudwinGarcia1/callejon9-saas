---
name: skill-code-review
description: Revisa cambios de Callejón 9 con foco en defectos, regresiones, seguridad, aislamiento por tenant, contratos, UX y pruebas. Úsala para diffs, ramas o entregas antes de integrar.
---

# Revisión de código

1. Determina base y alcance; normalmente compara contra `main` sin modificar el árbol.
2. Lee `AGENTS.md` y las especialidades que correspondan al diff.
3. Examina primero comportamiento y riesgos, luego estilo. Rastrea entradas no confiables, autorización, tenant, transacciones, SQL, concurrencia, contratos, caché/query keys y estados de UI.
4. Contrasta pruebas con los caminos que podrían fallar. Ejecuta comprobaciones focalizadas si son seguras.
5. Reporta solo hallazgos accionables. Cada hallazgo lleva severidad, archivo/línea, escenario de fallo, impacto y corrección sugerida.

Si no hay defectos, dilo explícitamente y enumera riesgos residuales o verificaciones no ejecutadas. No mezcles cambios propios con una revisión salvo que el usuario pida corregirlos.
