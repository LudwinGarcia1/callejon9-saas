# Propuesta de matriz de autorización — CAL-5 / CAL-7

Fecha: 2026-09-24. Estado: **propuesta para revisión, no contrato aprobado ni permisos implementados**.

Fuentes: [CAL-5](https://linear.app/callejon19/issue/CAL-5/cerrar-la-matriz-de-autorizacion-de-la-api), [CAL-7](https://linear.app/callejon19/issue/CAL-7/hacer-exigible-el-contrato-de-autorizacion), controllers del checkout local y consumidores del frontend. El inventario siguiente se obtuvo del código; CAL-7 debe contrastarlo con los mappings del contexto Spring real.

## Objetivo y responsables

CAL-5, asignada a Ludwin, define y aplica quién puede usar cada endpoint. CAL-7 convierte la matriz aceptada en una prueba que detecta rutas nuevas sin decisión de autorización o diferencias con el contrato.

La autorización por rol complementa RLS. Un rol permitido solo opera sobre su restaurante: el tenant procede de la cookie JWT firmada y se publica dentro de la transacción. No se modifican políticas RLS, privilegios de base ni migraciones en esta propuesta.

## Cómo leer la matriz

- `A`: ADMIN; `W`: WAITER; `K`: KITCHEN; `C`: CASHIER; `S`: SUPER_ADMIN.
- Público: se permite la petición sin sesión; sigue habiendo validación de datos y reglas de negocio.
- Sesión: cualquiera de los cinco roles con autenticación válida.
- Cualquier rol no listado queda rechazado. SUPER_ADMIN no hereda los permisos de ADMIN.
- Las rutas abreviadas tienen el prefijo `/api/v1`.
- Actual `General`: depende de `anyRequest().authenticated()`, sin regla explícita de rol en el controller. No satisface por sí sola CAL-7.
- Actual `Método`, `Clase` o `Matcher`: ya existe una regla explícita con los roles propuestos. Las pruebas deben verificar su comportamiento efectivo.

## Matriz de endpoints de la aplicación

| Método | Ruta | Acceso propuesto | Estado actual / cambio |
|---|---|---|---|
| POST | `/auth/login` | Público | Público por `/auth/**`; limitar excepción a método y ruta exactos |
| GET | `/auth/me` | Sesión | Público en filtro, validación manual; declarar autenticación explícita |
| POST | `/auth/logout` | Sesión | Actualmente público; exigir sesión y conservar limpieza de cookie |
| POST | `/signup` | Público | Público; limitar excepción a POST |
| GET | `/platform/plans` | S | Matcher `/api/v1/platform/**`; conservar |
| GET | `/users` | A | Clase; conservar |
| POST | `/users` | A | Clase; conservar |
| PATCH | `/users/{id}` | A | Clase; conservar |
| GET | `/tables` | A, W, K, C | General; declarar estos roles |
| POST | `/tables` | A | Método; conservar |
| PUT | `/tables/{id}` | A | Método; conservar |
| PATCH | `/tables/{id}` | A | Método; conservar |
| POST | `/tables/{id}/status` | A, W | Método; conservar |
| GET | `/categories` | A, W | General; limitar a consumidores del catálogo |
| POST | `/categories` | A | Método; conservar |
| PUT | `/categories/{id}` | A | Método; conservar |
| GET | `/products` | A, W | General; limitar a consumidores del catálogo |
| POST | `/products` | A | Método; conservar |
| PUT | `/products/{id}` | A | Método; conservar |
| PATCH | `/products/{id}` | A | Método; conservar |
| GET | `/orders` | A, W, C | General; incluir caja y excluir cocina |
| GET | `/orders/{id}` | A, W, C | General; incluir detalle necesario para cobro |
| POST | `/orders` | A, W | Método; conservar |
| POST | `/orders/{id}/items` | A, W | Método; conservar |
| POST | `/orders/{id}/send-to-kitchen` | A, W | Método; conservar |
| POST | `/orders/{id}/cancel` | A, W | Método; conservar |
| POST | `/orders/{id}/checkout` | A, C | Método; conservar |
| GET | `/kitchen/orders` | A, K | Clase; conservar |
| POST | `/kitchen/items/{itemId}/status` | A, K | Clase; conservar |
| GET | `/tickets/{id}` | A, C | General; restringir |
| GET | `/tickets?folio=...` | A, C | General; restringir; mapping exige parámetro `folio` |
| GET | `/tickets/{id}/pdf` | A, C | General; restringir también descarga |
| GET | `/sales` | A, C | General; restringir historial |
| GET | `/analytics` | A | General; restringir analítica |
| GET | `/inventory/items` | A, K | General; alinear con pantalla de inventario |
| POST | `/inventory/items` | A | Método; conservar |
| PUT | `/inventory/items/{id}` | A | Método; conservar |
| PATCH | `/inventory/items/{id}` | A | Método; conservar |
| GET | `/inventory/movements` | A, K | General; alinear con consulta de movimientos |
| POST | `/inventory/movements` | A, K | Método; conservar |

Son 40 mappings de métodos declarados en los controllers de la aplicación, antes de incorporar infraestructura y métodos implícitos de Spring como HEAD.

### Evidencia de los permisos propuestos

| Decisión | Consumidor o regla que la justifica |
|---|---|
| K puede leer mesas | `frontend/src/app/(authenticated)/kitchen/kitchen-view.tsx` consulta `/tables` para mostrar el número de mesa |
| C puede leer mesas y órdenes | `cashier/cashier-view.tsx`, `cashier/checkout-panel.tsx` y `frontend/src/hooks/use-nav-counts.ts` |
| A y W pueden leer catálogo | `admin/admin-view.tsx` y `waiter/order/[id]/order-view.tsx` consultan productos y categorías |
| A y K pueden consultar inventario | `inventory/inventory-view.tsx`, navegación por rol y permiso existente para registrar movimientos |
| A y C consultan ventas y tickets | Alcance explícito de CAL-5; `history/history-view.tsx`, `cashier/checkout-panel.tsx` y `components/shared/ticket-summary.tsx` |
| A consulta analítica | Alcance explícito de CAL-5 y navegación de administración |
| S solo administra plataforma | Matcher actual de `SecurityConfig` y tenant técnico de plataforma |

Las rutas de pantallas abreviadas de la tabla anterior parten de `frontend/src/app/(authenticated)/`.

## Lista pública de infraestructura

No usar `/auth/**` como excepción pública ni excluir todos los controllers externos sin clasificarlos.

| Método | Ruta o familia acotada | Justificación |
|---|---|---|
| GET | `/actuator/health` | Comprobar disponibilidad sin iniciar sesión; no habilitar el resto de Actuator |
| GET | `/v3/api-docs` | Especificación OpenAPI JSON |
| GET | `/v3/api-docs.yaml` | Especificación YAML si está registrada por Springdoc |
| GET | `/v3/api-docs/swagger-config` | Configuración de Swagger UI |
| GET | `/swagger-ui.html` | Entrada a la documentación navegable |
| GET | `/swagger-ui/**` | Recursos estáticos de Swagger UI; no es una excepción para APIs de negocio |

Verificar estas rutas contra los mappings y recursos realmente expuestos en el perfil probado. La configuración actual permite `/v3/api-docs/**`, pero no cubre necesariamente la ruta hermana `/v3/api-docs.yaml`: tratar cualquier ajuste como cambio explícito de CAL-5. Los recursos estáticos pueden no aparecer como `HandlerMethod`.

HEAD heredará la decisión de GET donde Spring lo soporte; documentar y probar esta normalización. OPTIONS no será una excepción pública global automática: verificar el comportamiento efectivo necesario. `/error` requiere clasificación técnica separada y pruebas de despacho de errores; no añadirlo como API pública por comodidad. No habilitar rutas de infraestructura inexistentes solo para satisfacer la matriz.

## Decisiones propuestas para revisar con Ludwin

1. Aprobar A/W para lectura de productos y categorías, y A/K para lectura de inventario. Se basan en los consumidores actuales; confirmar si existen otros clientes legítimos.
2. Conservar inicialmente la semántica de filtros `includeInactive`: los roles con acceso al GET conservan el filtro. Restringirlo solo a ADMIN sería una decisión adicional, que requeriría pruebas por parámetro; esta propuesta no la introduce implícitamente.
3. Adoptar logout autenticado como exige CAL-5. Probar que una sesión expirada conduce a login sin bucles y que una sesión válida borra la cookie. No ampliar la lista pública para evitar ese caso.
4. Aprobar el inventario de infraestructura una vez inspeccionado en contexto real. La lista del código fuente no sustituye esa evidencia.

La propuesta es completa para revisión, pero no se considera matriz aceptada hasta resolver estos puntos. No cambiar la asignación ni cerrar CAL-5 o CAL-7 con este documento.

## Implementación de CAL-5

1. Mantener públicas únicamente las rutas y métodos aprobados en `backend/src/main/java/com/callejon9/config/SecurityConfig.java`. Declarar explícitamente autenticación para `/auth/me` y `/auth/logout`, y conservar el matcher de plataforma.
2. Agregar reglas de clase para controllers uniformes (analítica, ventas, tickets); reglas de método para controllers con lectura y escritura de distintos roles. No añadir una regla ADMIN de clase que accidentalmente bloquee lecturas operativas.
3. Revisar controllers de `catalog`, `table`, `order` e `inventory` para cubrir los GET marcados como General. Conservar reglas de escritura existentes.
4. Actualizar comentarios de `AuthController`, `frontend/src/components/layout/nav.ts` y `frontend/src/hooks/use-nav-counts.ts` que describen reglas antiguas. Comprobar navegación y peticiones habilitadas por rol.
5. Documentar requisitos de seguridad y respuestas 401/403 en OpenAPI sin publicar secretos.
6. Ejecutar las pruebas de comportamiento con PostgreSQL real y revisar evidencia antes de dar la matriz por implementada.

## Trabajo que puede adelantar CAL-7

- Crear `backend/src/test/java/com/callejon9/config/AuthorizationContractTest.java` con contexto Spring real y perfil `test`.
- Enumerar mappings de `RequestMappingHandlerMapping`, incluyendo método HTTP, patrón, condiciones de parámetros y controller/método. Conservar `params=folio` de tickets; no comparar únicamente cadenas de URL.
- Resolver la regla efectiva: anotaciones de método/clase (incluidas compuestas o heredadas), matchers explícitos y lista pública documentada. La mera presencia de una anotación no demuestra que los roles coincidan con la matriz.
- No aceptar el fallback `anyRequest().authenticated()` como decisión explícita para todas las rutas. Una excepción de sesión debe tener ruta y justificación, como `/auth/me`.
- Para matchers, compartir la declaración con producción si se centraliza, y añadir pruebas del filtro real para detectar diferencias de orden o reglas que permitan demasiado. No mantener una segunda copia de permisos desconectada de `SecurityConfig`.
- Comparar en ambas direcciones: mappings sin entrada y entradas de contrato sin mapping. Clasificar infraestructura por separado de los 40 métodos de aplicación y comprobarla; no descartarla silenciosamente por paquete.
- Crear fixtures aisladas: endpoint sin regla rechazado, regla de método aceptada, regla de clase aceptada, matcher de plataforma reconocido, ruta pública no incluida rechazada y discrepancia de roles rechazada. La fixture insegura no debe publicarse fuera de tests.
- Exigir diagnóstico con controller, método, HTTP y ruta. Un fallo esperado de la fixture debe ser afirmado por el test, no dejar toda la suite intencionalmente roja.
- Mantener pendientes las discrepancias reales hasta que CAL-5 las corrija; no añadir exclusiones, deshabilitar pruebas ni presentar la matriz propuesta como aprobada.
- Integrar `verify` en el Jenkinsfile de CAL-87 cuando esté disponible. No crear una integración nueva con GitHub Actions para esta tarea.

## Criterios de prueba y cierre

| Caso | Resultado esperado |
|---|---|
| Petición válida sin cookie a una ruta protegida | 401 |
| Rol válido no listado para la ruta, con datos válidos | 403 sin datos ni efectos persistentes |
| Rol listado y datos del mismo restaurante | Éxito correspondiente a la operación: 200, 201 o 204 según contrato |
| W/K consultan analítica, ventas o cualquiera de las tres rutas de tickets | 403 |
| C consulta órdenes, mesas, ventas y tickets propios | 200; el flujo de cobro sigue disponible |
| K consulta mesas y tablero de cocina propios | 200; no obtiene lectura general de órdenes |
| S consulta planes / rol operativo consulta planes | 200 / 403 |
| Ruta pública sin sesión y entrada válida | No rechazada por ausencia de sesión; login sigue validando credenciales |
| Rol autorizado intenta leer un ID de otro restaurante | Sin datos ajenos; comprobar respuesta de no encontrado definida por servicio |
| Rol autorizado intenta modificar datos de otro restaurante | Rechazo y ausencia de efectos en ambos restaurantes |
| Nuevo endpoint de fixture sin decisión explícita | Verificador lo rechaza con método y ruta |
| Endpoint cambia de permisos frente al contrato | Verificador detecta la discrepancia |

Probar los cinco roles y el caso anónimo de forma parametrizada. Usar datos y payloads válidos para no confundir errores de validación con rechazo por autorización. Las pruebas de RLS son independientes de las de rol.

Puertas para implementación: `cd backend; .\mvnw.cmd -B verify` con PostgreSQL real y `DB_PORT=5432` en esta máquina (base de pruebas `callejon9_test`); evidencia RLS como `callejon9_app`. Si cambia frontend: `pnpm lint`, `pnpm exec tsc --noEmit`, `pnpm build`. El entorno local tuvo un fallo del wrapper: si se usa Maven instalado como alternativa, registrar el comando y versión reales, sin afirmar que se ejecutó el wrapper.

Este documento no ejecuta esas pruebas ni cambia autorización. Su verificación consiste en contrastar las 40 entradas con los controllers y revisar los consumidores citados. La enumeración desde Spring y las pruebas de roles quedan para CAL-5/CAL-7.

## Mensaje sugerido para compartir

>Propuesta de matriz para CAL-5 que puedo usar como contrato en CAL-7. Incluye los 40 endpoints de la aplicación y una lista pública de infraestructura por validar. Conserva lectura de mesas para Cocina y lectura de órdenes para Caja.
Avance de descubrimiento de rutas y los casos del verificador; el cierre de CAL-7 dependerá de integrar la matriz aceptada y tus cambios.
