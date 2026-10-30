# CAL-153 — Sesión local PC y Android

El usuario declaró ejecución el 2026-10-29. Los resultados específicos y capturas no se recibieron. El recorrido coordinado usa web en Android; no demuestra la app móvil ni Azure.

## C01 — Recepción

Abrir cocina antes del envío de un pedido nuevo desde el mesero. Anotar folio y hora y observar sin recargar. Comparar mesa, productos, cantidades y notas. Esperado: recepción automática.

## C02 — Antigüedad

Con dos pedidos visibles, identificar el primero y su tiempo de espera y contrastar las horas anotadas. Un solo pedido no demuestra la comparación.

## C03 — Pedido listo visible al mesero

1. Android: entrar como mesero, abrir la comanda y anotar el folio. Mantenerla visible.
2. PC: entrar como cocina y buscar exactamente la misma mesa y folio.
3. Marcar cada producto En preparación y después Listo. Un producto listo no necesariamente termina todo el pedido.
4. Observar Android sin recargar ni cambiar de ruta. Anotar si aparece Lista y cuánto tarda. Diez segundos son una referencia inicial, no un umbral de aceptación del issue.
5. Si no cambia, capturar antes de recargar; recargar después solo para diagnosticar persistencia. Si cambia únicamente tras la recarga, registrar fallo de actualización automática.
6. Guardar capturas reales con folio y estado en `capturas/`, sin contraseñas ni cookies.

La versión servida debe registrarse; no asumir que ejecuta otra rama con la corrección. Resultados y aceptación: [registro-aceptacion.md](registro-aceptacion.md).
