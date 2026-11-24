# CAL-150 · UAT-05 Mesas

La [SIT de Mesas (CAL-137)](../../sit/CAL-137-mesas/README.md) documenta la integración previa. La sesión de aceptación queda pendiente.

## Sesión de aceptación

Usar un restaurante de prueba y dos sesiones independientes, una con rol administrador y otra con rol mesero. Elegir tres números de mesa disponibles y capacidades distintas. Registrar el entorno y la versión servida en el [registro de aceptación](registro-aceptacion.md).

1. **C01.** Como administrador, crear las tres mesas y comprobar que el catálogo conserva el número y la capacidad de cada una.
2. **C02.** Como mesero, abrir **Mesas** y verificar que las tres aparecen como **Libre**, con el número y la capacidad de cada una.
3. **C03.** Abrir una de las tres mesas e iniciar la orden. Volver al tablero de mesas y comprobar que esa mesa dice **Ocupada** y las otras dos dicen **Libre**.

Guardar las capturas sin datos personales ni secretos. Registrar resultados por caso y, ante un defecto, anotar su severidad y el enlace al issue correspondiente. Al terminar, cancelar la orden de prueba y retirar las mesas temporales según las reglas del entorno.

## Prueba automatizada

Desde `frontend`, ejecutar `pnpm test src/test/tables-uat.test.tsx`. La prueba conecta los componentes de administrador y mesero mediante respuestas HTTP simuladas y cachés independientes. Comprueba el alta, la capacidad y la transición de estado. La aceptación requiere la sesión con los participantes.
