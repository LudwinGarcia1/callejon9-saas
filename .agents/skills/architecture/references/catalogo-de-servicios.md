# Catálogo de servicios

El backend es un monolito modular organizado por funcionalidad bajo `com.callejon9`:

- `auth`: login, sesión, logout y JWT.
- `tenancy`: contexto, filtro y transaction manager multi-tenant.
- `platform.plan` / `platform.tenant`: planes, alta e identidad de restaurantes.
- `user`, `table`, `catalog`: administración operativa.
- `order`, `kitchen`, `sale`, `ticket`: flujo comanda → cocina → cobro → comprobante.
- `realtime`: mensajes STOMP y autorización de suscripción por tenant.
- `analytics`: agregados operativos por rango.
- `shared`: errores y piezas transversales.

No extraigas servicios por defecto. Primero conserva cohesión modular, contratos explícitos y transacciones locales.
