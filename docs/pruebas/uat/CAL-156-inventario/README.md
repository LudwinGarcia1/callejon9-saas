# CAL-156 · UAT-11 Inventario

## Estado

Guía de preparación; la UAT manual aún no se ha ejecutado. La prueba automatizada local cubre las mutaciones y la lectura de alertas en interfaz, pero no acredita una sesión en Azure.

Al 27 de octubre de 2026, CAL-118 sigue `In Progress`. CAL-143 aparece `Done`, pero tiene una revisión que rechazó la evidencia anterior por discrepancia en el mensaje HTTP y falta de capturas. Confirmar que CAL-118 y CAL-143 cuentan con evidencia aceptada antes de convocar a los participantes.

## Criterios que se deben observar

1. La persona de cocina registra una salida de insumos al terminar el turno.
2. La persona administradora ve la existencia coincidente con el movimiento.
3. La administración registra un ajuste con el conteo físico observado.
4. Si el stock queda por debajo del mínimo, el insumo aparece en alerta.

## Preparación de la sesión

- Entorno: Azure, restaurante e insumos de prueba habilitados para UAT.
- Participantes: una persona con rol cocina y una con rol administrador.
- Anotar en el registro de evidencia del equipo el stock inicial, mínimo, salida y conteo físico observados.
- Guardar capturas y respuestas en el repositorio de evidencias del equipo, fuera de Git. Incluir URL completa, fecha/hora y rol; ocultar cookies y datos personales.

## Ejecución manual

1. Como administrador, identifica un insumo de prueba y anota stock y mínimo.
2. Como cocina, abre Inventario, registra una salida y guarda la respuesta mostrada.
3. Como administrador, consulta de nuevo el insumo. Confirma que el stock mostrado sea el inicial menos la salida y que el historial contenga el movimiento.
4. Cuenta físicamente el insumo y registra un ajuste con ese conteo, no con una diferencia calculada manualmente.
5. Confirma que la existencia coincida con el conteo y que el estado indique **Bajo mínimo** si el valor es inferior al mínimo configurado.
6. Registra aceptación sí/no, observaciones y enlaces a evidencia. Abre defectos con severidad para cualquier discrepancia.

## Verificación automatizada disponible

Desde `frontend`:

```powershell
pnpm test src/test/inventory-uat.test.tsx
```

La prueba de interfaz cubre salida, actualización visible del stock, ajuste por conteo y alerta de mínimo. `InventoryMovementControllerTest` cubre las reglas de stock y movimientos en PostgreSQL local. Estas verificaciones locales complementan la UAT; no la reemplazan.

## Resultado manual

Sin ejecutar. Completar solo después de la sesión real con cocina y administración.
