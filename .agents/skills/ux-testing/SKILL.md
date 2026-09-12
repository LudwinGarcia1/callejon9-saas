---
name: ux-testing
description: Evalúa un flujo implementado de Callejón 9 desde la perspectiva del usuario mediante revisión de código, ejecución local o navegador cuando esté disponible.
---

# Prueba UX

Define actor, objetivo, datos previos y resultado observable. Recorre el camino feliz y después error de red, validación, vacío, permisos, doble envío, refresco y viewport estrecho. Comprueba que cada acción tenga feedback y que foco/teclado sean utilizables.

No confundas una revisión estática con una prueba ejecutada. Para cada hallazgo registra severidad, pasos, esperado, actual, evidencia y archivo o ruta probable. Usa `references/criterios.md` como checklist, no como sustituto de criterio.
