# Guion del video · Sprint 4

Sprint 4 · 26 de octubre al 6 de noviembre de 2026 · ciclo 6 de Linear · 32 puntos.

Duración objetivo: 8 a 10 minutos. Cada integrante presenta lo que hizo. Se graba en pantalla compartida (Meet o Teams) con la cámara encendida de quien habla.

## Antes de grabar

- [ ] Encender la API de Azure (`scripts/azure-backend-power.ps1`) y comprobar `/actuator/health` en `UP`.
- [ ] Iniciar sesión en `restohub.azurewebsites.net` con las cuentas del restaurante `pruebas` (caja, administrador, mesero). No mostrar contraseñas en pantalla.
- [ ] Abrir en pestañas: el ciclo 6 en Linear, el PBL del Sprint 4, las carpetas `docs/pruebas/sit/` y `docs/pruebas/uat/` en GitHub y el PR #93 (turnos de caja).
- [ ] Decidir quién sube el video y quién pega la URL en la plataforma.

## 1. Apertura · Ludwin (Product Owner) · 1 min

- Presenta al equipo y el proyecto: RestoHub, SaaS multi-restaurante en Azure.
- Lee el objetivo del sprint: completar las 10 SIT restantes, corregir las observaciones de la revisión de evidencias (CAL-181), cerrar dos UAT, rechazar productos inactivos en la comanda (CAL-10), turnos de caja con arqueo (CAL-98) y documentar los límites del plan F1 (CAL-165).
- **En pantalla:** el PBL del Sprint 4.

## 2. Planeación y capacidad · Octavio (Scrum Master) · 1 min

- Capacidad reducida a 8 puntos por integrante (32 en total) por las entregas de otras asignaturas.
- Explica el reparto: varias SIT estaban asignadas en Linear a Octavio y se redistribuyeron en el PBL para equilibrar la carga.
- **En pantalla:** la tabla de capacidad y el ciclo 6 en Linear con sus 15 issues en Done.

## 3. Demostración del incremento · 5 min

### Valeria · CAL-98 Turnos de caja (8 pts) · 1.5 min

- En Azure, como caja: abrir turno con fondo inicial, cobrar una orden, registrar un retiro con motivo y cerrar con el conteo. Mostrar la diferencia calculada.
- Explicar la fórmula: esperado = fondo + efectivo cobrado − cambio + ingresos − retiros.
- Mencionar que las tablas nuevas tienen RLS forzado y que un cajero solo ve su turno.
- **En pantalla:** `/cash-sessions` en Azure y el PR #93.

### Ludwin · CAL-10, SIT CAL-136, CAL-137, CAL-143, CAL-145 y UAT CAL-149 · 1.5 min

- CAL-10: en la web, intentar agregar a la comanda un producto desactivado y mostrar el rechazo (422) sin que cambie el total.
- SIT: abrir la evidencia de una suite (por ejemplo `CAL-145-aislamiento-seguridad-transversal`) y explicar el resultado: un mesero no puede ver la orden de otro restaurante (404).
- UAT CAL-149: mostrar el registro de aceptación del catálogo.

### Braulio · SIT CAL-135, CAL-140, CAL-141 y CAL-181 · 1 min

- CAL-140: cocina en tiempo real; enseñar las capturas en las que la comanda llega sin recargar, y los defectos que salieron de esa prueba (CAL-169 a CAL-172).
- CAL-141: pago dividido con propina y orden que ya no acepta cambios.
- CAL-181: qué observaciones de la revisión independiente se corrigieron.

### Octavio · SIT CAL-133, CAL-138, CAL-139, CAL-165 y UAT CAL-156 · 1 min

- CAL-139: la app de meseros en el teléfono contra Azure; pedido enviado y recibido en la cocina web.
- CAL-165: README de `infra/terraform/azure-frontend` con los límites de F1 y el procedimiento para subir a B1 durante demos.
- UAT CAL-156: registro de aceptación de inventario.

## 4. Resultados · Octavio · 1 min

- 15 de 15 issues en Done, 32 de 32 puntos.
- Defectos encontrados por las SIT y registrados en Linear: CAL-166, CAL-168, CAL-169 a CAL-172, CAL-182 y CAL-183.
- Las SIT de catálogo, mesas, cobro, tickets, inventario, ventas y aislamiento se re-ejecutaron el 27 de octubre (versión v2), porque la primera evidencia no correspondía al backend desplegado. Decirlo en el video: muestra que la revisión funcionó.

## 5. Retrospectiva · todos · 1 min

- **Qué salió bien:** cada SIT dejó evidencia reproducible con Playwright contra Azure.
- **Qué mejorar:** evidencia que no salió de una ejecución real tuvo que retirarse; desde entonces cada prueba se revisa por alguien distinto de quien la hizo.
- **Acuerdos:** plan de gestión de bloqueos y plan de reasignación por ausencias (aprobados el 28 de octubre).

## Comentario para la plataforma

Quien sube el video pega la URL. Los demás marcan la tarea como entregada y comentan:

> Entrega por equipo. Responsable de subir el video del Sprint 4: [nombre].
