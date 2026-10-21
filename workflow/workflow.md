# Flujo operativo

Las skills de `.agents/skills` exponen estos procedimientos. Los pasos externos —crear/modificar issues, push, PR o merge— requieren que el usuario los haya pedido y que la conexión correspondiente esté verificada.

## Estados de Linear

Un issue avanza en este orden; los nombres son los del team CAL y sus IDs están en `project-config.yaml §linear.states`.

| Estado | Entra cuando |
|---|---|
| Backlog | Se registra la necesidad, aún sin priorizar |
| Todo | El brief pasó `$pm-validate-issue` y está listo para construir |
| In Progress | `$wf-build` empieza la implementación en su rama |
| In review | La implementación está completa y hay PR abierto o diff listo para `$wf-review` |
| For Test | `$wf-review` dio veredicto aprobable y falta la validación funcional |
| Done | La revisión está aprobada, el PR está fusionado en `main` y la prueba funcional, si aplica, pasó |

**Ningún issue pasa a Done sin revisión.** Antes de mover un issue a Done debe existir evidencia de una revisión aprobada del mismo cambio: una entrada de `workflow/state/review-history.jsonl` con ese `issue` y un veredicto aprobable (campo `review` aprobable o `verdict: approved`), o una revisión aprobada en el PR de GitHub. Si la revisión encontró bloqueantes, el issue vuelve a In Progress, y solo pasa a Done después de corregirlos y de que una nueva revisión los dé por cerrados. Un issue sin esa evidencia se queda en In review aunque el PR esté fusionado. Lo mismo aplica a los cambios hechos a mano: si alguien mueve un issue a Done sin revisión, `$wf-health` lo reporta y el issue se regresa a In review.

Las issues de prueba (etiqueta `Testing`: UT, SIT y UAT) siguen el mismo flujo; la regla no tiene excepciones por tipo. Lo que cambia es qué se revisa:

| Tipo | La revisión confirma |
|---|---|
| UT | Que las pruebas nuevas fallan sin el comportamiento que protegen, que cubren los criterios del issue y que la puerta del módulo (`mvnw -B verify`, `pnpm test`) pasa en CI |
| SIT | Que la evidencia corresponde a una ejecución real contra el entorno indicado, con fecha, commit y resultado de cada caso, y que los fallos quedaron registrados como issues |
| UAT | Que el registro de aceptación está completo por rol y caso, que cada caso tiene resultado y que las observaciones de los usuarios tienen seguimiento |

Quien revisa una issue de prueba no es quien la ejecutó. Una prueba que no se ejecutó no cuenta como evidencia, aunque el formato esté lleno.

Nadie revisa su propio cambio. Revisa otro integrante del equipo o una sesión de agente independiente (Claude Code o Codex): una sesión nueva, que no escribió ni corrigió ese cambio, y que revisa con `$wf-review`. Cada revisión se registra en `review-history.jsonl` con `reviewer`, `reviewedHead` y `review`; si entra un commit después, se revisa de nuevo. Reglas y límites en `workflow/decisions/2026-10-20-revision-independiente.md`.

## `$wf-build`

1. Identifica el issue o redacta un brief local; valida objetivo, criterios y fuera de alcance.
2. Haz preflight: árbol Git, rama/base, archivos afectados, configuración Linear y dependencias.
3. Inspecciona código y pruebas reales. Elige el cambio vertical mínimo que satisfaga un criterio.
4. Implementa preservando invariantes; ejecuta comprobaciones focalizadas después de cada bloque coherente.
5. Ejecuta las puertas finales aplicables de `project-config.yaml`.
6. Resume diff, criterios cubiertos, evidencia, deuda y bloqueos. No publiques nada sin autorización.

## `$wf-review`

1. Fija base (`main` salvo indicación) y commit/diff objetivo.
2. Mapea cambios a criterios y áreas de riesgo.
3. Aplica `$skill-code-review` y las especialidades afectadas.
4. Ejecuta pruebas focalizadas sin alterar la implementación.
5. Devuelve hallazgos por severidad; luego dudas, veredicto y pruebas no ejecutadas.
6. Registra el veredicto en `workflow/state/review-history.jsonl` (campo `review`). Esa entrada es la evidencia que exige el paso a Done; sin ella el issue no sale de In review.

## `$wf-ship`

1. Confirma que la implementación y la revisión están completas y que el árbol contiene solo cambios previstos.
2. Repite puertas finales si el diff cambió después de la revisión.
3. Actualiza documentación durable y evidencia.
4. Propón commit/PR con resumen, pruebas y riesgos. Commit, push, PR y merge solo si fueron solicitados explícitamente.
5. Si Linear está habilitado, actualiza el issue únicamente después de verificar workspace, team e identificador. No lo muevas a Done sin la evidencia de revisión aprobada que exige la sección «Estados de Linear»; si falta, déjalo en In review y dilo en el reporte.

## `$wf-health`

Audita sin implementar: estado Git, configuración, referencias rotas del harness, skills inválidas, puertas disponibles, divergencia entre API cliente/controllers, migraciones/RLS, documentación obsoleta y trabajo sin evidencia, incluidos issues de Linear en Done sin revisión aprobada. Devuelve semáforo, hallazgos y siguientes acciones priorizadas.

## `$wf-review-ships`

Toma un rango explícito de entregas y compara intención, cambios, checks y resultados. Identifica regresiones aún abiertas y patrones de escape; propone pocas mejoras medibles del proceso. No confunde volumen de actividad con valor entregado.

## `$wf-decision`

Para una decisión difícil: formula contexto y fuerzas, restricciones, opciones reales, consecuencias, decisión y señales para revisarla. Verifica lo que ya impone el repositorio. Guarda decisiones aceptadas en `workflow/decisions/YYYY-MM-DD-slug.md`; una propuesta no se registra como aceptada.

## `$wf-debrief`

Después de una entrega o incidente, registra qué se esperaba, qué ocurrió, evidencia, causas, señales ignoradas, qué funcionó y una o dos mejoras del sistema. Evita convertir una anécdota en regla global. Actualiza una skill o referencia solo si el aprendizaje es durable y repetible.

## `$wf-update-docs`

Compara el diff con README, OpenAPI, producto, roadmap, especialidades y decisiones. Actualiza solo verdades durables; no copies estado efímero. Registra documentos en `workflow/docs/registry.json` cuando entren al conjunto gobernado.

## `$pm-create-issue`

Genera título orientado a resultado, contexto, problema, alcance/fuera de alcance, criterios Given/When/Then, dependencias, riesgos, archivos probables, pruebas y DoD. Deduplica contra trabajo visible. Si Linear no está habilitado/verificado, entrega Markdown local y no simula un ID.

## `$pm-create-epic`

Define resultado y métrica; divide en issues verticales independientes, con dependencias explícitas y orden de entrega. Evita epics que solo agrupan capas técnicas. Publica en Linear solo con autorización, workspace y team verificados.

## `$pm-validate-issue`

Verifica claridad, valor, alcance, criterios observables, dependencias, archivos, seguridad, UX, datos, pruebas y DoD. Devuelve bloqueantes y una versión corregida; no edita el issue remoto salvo petición explícita.

## `$pm-retro`

Resume periodo/entrega con datos disponibles: objetivos, entregado, no entregado, defectos, tiempos o bloqueos, causas y acciones con dueño/señal de éxito. Diferencia hechos de interpretación.

## `$pm-add-specialty`

Crea o actualiza contexto especializado solo después de inspeccionar fuentes reales. Mantén `SKILL.md` procedural y mueve conocimiento detallado a `references/`. Añade rutas exactas, fecha/commit cuando sea útil y criterios para detectar obsolescencia. Valida la skill antes de terminar.
