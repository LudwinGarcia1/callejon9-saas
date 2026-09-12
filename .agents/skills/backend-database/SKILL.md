---
name: backend-database
description: Implementa, diagnostica o revisa backend Java/Spring y PostgreSQL de Callejón 9, incluidas migraciones Flyway, transacciones, autorización, concurrencia y RLS.
---

# Backend y base de datos

1. Lee `AGENTS.md` y las referencias pertinentes.
2. Ubica el paquete funcional real antes de editar. Conserva la dirección `web → service → repository`.
3. Define el límite transaccional en el service; recuerda que el tenant se publica a PostgreSQL al abrir la transacción.
4. Para esquema, crea una migración Flyway nueva. Evalúa propietario, grants, RLS, `USING`, `WITH CHECK`, datos existentes y reversibilidad operativa.
5. Valida entrada, autorización y respuestas RFC 7807; nunca confíes en un tenant enviado por el cliente.
6. Añade pruebas al nivel más bajo que demuestre el comportamiento. Usa integración con PostgreSQL para RLS, SQL, locks o concurrencia.
7. Ejecuta pruebas focalizadas y, antes de cerrar, `cd backend; .\mvnw.cmd -B verify` cuando el entorno esté disponible.

Reporta migraciones, contratos afectados, pruebas ejecutadas y cualquier puerta bloqueada.
