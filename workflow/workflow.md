# Flujo operativo

Las skills de `.agents/skills` exponen estos procedimientos. Los pasos externos —crear/modificar issues, push, PR o merge— requieren que el usuario los haya pedido y que la conexión correspondiente esté verificada.

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

## `$wf-ship`

1. Confirma que la implementación y la revisión están completas y que el árbol contiene solo cambios previstos.
2. Repite puertas finales si el diff cambió después de la revisión.
3. Actualiza documentación durable y evidencia.
4. Propón commit/PR con resumen, pruebas y riesgos. Commit, push, PR y merge solo si fueron solicitados explícitamente.
5. Si Linear está habilitado, actualiza el issue únicamente después de verificar workspace, team e identificador.

## `$wf-health`

Audita sin implementar: estado Git, configuración, referencias rotas del harness, skills inválidas, puertas disponibles, divergencia entre API cliente/controllers, migraciones/RLS, documentación obsoleta y trabajo sin evidencia. Devuelve semáforo, hallazgos y siguientes acciones priorizadas.

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
