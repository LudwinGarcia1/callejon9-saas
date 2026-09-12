# Definición de terminado

Una tarea se considera terminada cuando:

- los criterios de aceptación son observables y están cubiertos;
- no rompe aislamiento por tenant, autenticación, autorización ni conservación del histórico;
- los contratos backend/frontend siguen alineados;
- existen pruebas nuevas o una justificación concreta de por qué no aplican;
- pasan las puertas de `workflow/project-config.yaml` afectadas por el cambio;
- lint, tipos y build se evalúan por separado en frontend;
- se actualizan mapa, roadmap, decisión o especialidad cuando cambió una verdad durable;
- la evidencia y los bloqueos se reportan sin afirmar verificaciones no ejecutadas.
