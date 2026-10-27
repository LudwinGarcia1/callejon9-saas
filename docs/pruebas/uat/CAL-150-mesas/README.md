# CAL-150 · UAT-05 Mesas

## Estado

Guía de preparación; la UAT manual no se ha ejecutado. Las pruebas locales verifican el formulario de alta y el tablero del mesero; no acreditan una sesión real en Azure.

Al 27 de octubre de 2026, CAL-118 sigue `In Progress`. Antes de convocar a los participantes, confirmar que CAL-118 y CAL-137 tienen evidencia SIT aceptada. Aunque CAL-137 figura `Done`, la revisión registrada en CAL-150 indica que la sesión anterior no tenía participantes ni capturas y que la SIT de mesas aún no estaba demostrada.

## Criterios que se deben observar

1. El administrador registra tres mesas con número y capacidad.
2. El mesero ve las tres mesas libres con su número y capacidad.
3. El mesero abre una mesa; su estado cambia a ocupada y se distingue visualmente de las libres.

## Preparación de la sesión

- Entorno: Azure, restaurante de prueba y cuentas habilitadas para los roles administrador y mesero.
- Participantes: una persona administradora y una persona mesera.
- Elegir números de mesa que no existan ya en ese restaurante.
- Guardar capturas y respuestas en el repositorio de evidencias del equipo, fuera de Git. Capturar URL completa, fecha/hora y ambas vistas; ocultar cookies y datos personales.

## Ejecución manual

1. Como administrador, registra tres mesas con números y capacidades diferentes.
2. Como mesero, abre la pantalla **Mesas** y confirma que las tres aparecen como **Libres**, con sus números y capacidades.
3. Abre una mesa e inicia una orden. Confirma que el estado cambie a **Ocupada** y que las otras dos sigan **Libres**. Si hay otra sesión de mesero, confirma allí el mismo estado después de la actualización automática.
4. Registra por criterio: aceptado sí/no, observaciones y enlaces a evidencia. Crea defectos con severidad si el estado o la capacidad no coincide.

## Verificación automatizada disponible

Desde `frontend`:

```powershell
pnpm test src/test/tables-uat.test.tsx
```

La prueba cubre alta con capacidad, lectura visual del número/capacidad/estado y transición libre → ocupada desde el flujo del mesero. `TableControllerTest` cubre el catálogo y las transiciones permitidas; `OrderControllerTest` verifica que abrir una orden ocupa la mesa y que una mesa ya ocupada rechaza otra apertura con `409`. Estas verificaciones locales complementan la UAT; no la reemplazan.

## Resultado manual

Sin ejecutar. Completar solo después de la sesión real con administrador y mesero.
