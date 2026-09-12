# Brief de producto

Callejón 9 es una plataforma SaaS multi-restaurante para administrar personal, mesas, catálogo, comandas, cocina, cobro, tickets, historial y analítica. Cada restaurante comparte la misma aplicación y base, pero PostgreSQL debe impedir cualquier lectura o escritura cruzada mediante RLS.

## Usuarios

- `SUPER_ADMIN`: opera la plataforma y sus planes.
- `ADMIN`: configura un restaurante, su identidad, catálogo, mesas y personal.
- `WAITER`: abre mesas y captura comandas.
- `KITCHEN`: procesa partidas enviadas a cocina.
- `CASHIER`: cobra, emite tickets y consulta ventas.

## Principios

1. El aislamiento se demuestra en el motor, no se confía a filtros manuales.
2. La operación diaria debe ser clara, rápida y recuperable.
3. El histórico se conserva; las bajas de entidades referenciadas son lógicas.
4. El navegador mantiene autenticación first-party a través del rewrite de Next.js.
5. Las promesas de producto deben tener evidencia en rutas, pruebas y documentación.
