# Datos de demostración por apartado

Objetivo aceptado: disponer de 10 registros ficticios por apartado operativo,
incluidos usuarios. La carga se realiza en un restaurante local nuevo mediante
la API autenticada; conserva RLS, los límites de plan y las reglas de negocio.

Ejecuta `scripts/seed-demo-bulk.ps1` con la API local encendida. Define previamente
`DEMO_PASSWORD` en tu sesión o proporciona `-Password` sin guardar credenciales
en archivos versionados. El slug predeterminado es `demo10`; se puede cambiar
con `-Slug`. El administrador tiene el correo `admin@<slug>.example`.
Los demás usuarios usan `personal1@<slug>.example` hasta `personal9@<slug>.example`
y la contraseña proporcionada, con roles de mesero, cocina, caja y administrador.

La carga predeterminada crea:

- 10 usuarios en total, incluido el administrador.
- 10 categorías, productos, mesas e insumos.
- 10 movimientos iniciales de inventario, con algunos insumos bajo el mínimo.
- 10 ventas y sus 10 tickets, con pagos y propinas variados.
- 10 comandas adicionales en cocina, con productos pendientes, en preparación y listos.
- Una comanda cancelada: 21 comandas en total.

El restaurante usa PRO; con más de 15 usuarios el script elige PREMIUM. Todas las
mesas quedan ocupadas por las comandas de cocina. Ventas y movimientos se crean
con fecha actual y alimentan los resúmenes existentes.

La verificación final consulta cantidades y tickets por API. No se crean datos
en módulos diferidos sin endpoints, sesiones artificiales ni registros de
proveedores de pago externos. El panel de plataforma verá un restaurante nuevo,
no 30 restaurantes ni planes inventados.

El script rechaza destinos remotos y slugs existentes; no borra ni sobrescribe
datos. Si falla una petición, se detiene con los registros ya creados conservados.
No reintenta operaciones automáticamente: cada petición tiene su transacción.
Para una nueva carga usa otro slug. `-Count` admite entre 3 y 60 registros.
