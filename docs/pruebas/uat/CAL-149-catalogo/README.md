# CAL-149 · UAT-04 Catálogo

## Estado

Guía de preparación; la UAT manual aún no se ha ejecutado. La cobertura automatizada de interfaz se puede correr en local, pero no acredita una sesión real en Azure.

Al 27 de octubre de 2026, CAL-118 sigue `In Progress`. Antes de convocar al administrador, confirmar en Linear que CAL-118 ya permite iniciar UAT y que CAL-136 tiene evidencia SIT aceptada: la revisión registrada en CAL-136 rechazó el respaldo anterior por no corresponder a una ejecución real.

## Criterios que se deben observar

1. El administrador crea una categoría y tres productos con precio.
2. El mesero ve los productos activos y sus precios en la comanda.
3. El administrador desactiva uno; deja de ofrecerse en el catálogo y no se puede agregar a una orden. Si se intenta con una comanda que ya tenía el producto disponible, el servidor debe rechazarlo con HTTP `409`.
4. El administrador corrige el precio sin ayuda; el nuevo precio aparece en el catálogo.

## Preparación de la sesión

- Entorno: Azure, restaurante y cuentas de prueba aprobados para UAT.
- Participantes: una persona con rol administrador y una con rol mesero.
- Usar un producto y una orden de prueba. No pegar contraseñas, cookies ni datos personales en este repositorio.
- Guardar capturas y el registro firmado en el repositorio de evidencias del equipo, no en Git. Incluir URL completa, fecha/hora y resultado por criterio; ocultar secretos.

## Ejecución manual

1. Inicia sesión como administrador, crea una categoría y tres productos con precios distintos. Guarda los nombres usados en el registro de evidencia del equipo.
2. Inicia sesión como mesero y abre una comanda. Confirma que aparecen los tres productos y sus precios correctos.
3. Regresa como administrador y desactiva uno de los productos.
4. Como mesero, actualiza el catálogo. Confirma que el producto ya no aparece. Intenta añadirlo desde una comanda abierta antes de la desactivación y confirma el rechazo HTTP `409`, sin agregar líneas ni modificar el total.
5. Como administrador, cambia el precio de uno de los productos activos. Como mesero, vuelve a consultar el menú y confirma el precio actualizado.
6. Registra por criterio: aceptado sí/no, observación y enlace a evidencia. Abre defectos separados con severidad para cualquier discrepancia.

## Verificación automatizada disponible

Desde `frontend`:

```powershell
pnpm test src/test/catalog-uat.test.tsx
```

La prueba de backend `ProductControllerTest` cubre creación, edición de precio y filtro de productos inactivos; `OrderControllerTest` cubre el rechazo al agregar un producto inactivo. Estas pruebas locales complementan la UAT, pero no la reemplazan.

## Resultado manual

Sin ejecutar. Completar este apartado solo después de la sesión real; no inferir aprobación a partir de las pruebas automatizadas.
