---
name: architecture
description: Analiza o diseña cambios que cruzan límites entre Next.js, Spring Boot, PostgreSQL, autenticación, realtime o tenants en Callejón 9. Úsala para decisiones arquitectónicas, contratos, dependencias y revisiones de impacto.
---

# Arquitectura

1. Lee `AGENTS.md` y solo las referencias necesarias de `references/`.
2. Traza el flujo completo: actor → ruta frontend → cliente API → controller → service/transacción → repository → tabla/política → respuesta o evento.
3. Identifica la fuente de verdad, el límite de seguridad y los fallos parciales.
4. Contrasta propuestas con código, migraciones y pruebas existentes; no diseñes desde nombres de documentos solamente.
5. Si cambia una decisión durable, propón un registro en `workflow/decisions/` con contexto, opciones, decisión, consecuencias y evidencia.

La salida debe incluir alcance, diagrama textual breve si ayuda, archivos afectados, invariantes, riesgos, estrategia de migración y pruebas necesarias.
