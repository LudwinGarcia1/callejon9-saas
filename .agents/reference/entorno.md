# Entorno de Callejón 9

- SO de desarrollo actual: Windows / PowerShell.
- JDK: 21; Maven se ejecuta con `backend/mvnw.cmd`.
- PostgreSQL: 16, puerto local documentado 5433; CI usa 5432.
- Node: 24 recomendado; package manager `pnpm@10.28.2`.
- Backend: `http://localhost:8080`; Swagger UI: `/swagger-ui.html`.
- Frontend: `http://localhost:3000`; rewrite server-side hacia backend.
- Arranque conjunto: `scripts/run-dev.ps1`.

Las credenciales locales y secretos se proporcionan por variables de entorno según `README.md`; nunca se copian a esta referencia.
