# Stack backend verificado

- Java 21 y Spring Boot 3.4.13 (`backend/pom.xml`).
- Spring MVC, Security, Validation, Data JPA, WebSocket y Actuator.
- PostgreSQL con Flyway; no H2.
- JJWT para sesión, TOTP disponible como servicio y OpenPDF para tickets.
- `spring.jpa.hibernate.ddl-auto=validate` y `open-in-view=false` (`application.yml`).
- Errores HTTP en formato Problem Detail.

Las pruebas sensibles a base deben ejecutarse contra PostgreSQL 16. Revisa `README.md` y `.github/workflows/ci.yml` para variables y preparación de roles.
