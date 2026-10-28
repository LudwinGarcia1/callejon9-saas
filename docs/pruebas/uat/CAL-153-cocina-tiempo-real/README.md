# Evidencia de prueba ? CAL-153 ? UAT ? Cocina y tiempo real

> **Estado: guion preparado; resultados y aceptaci?n pendientes.** En la conversaci?n se dieron instrucciones y se prepararon cuentas y una mesa; no se recibi? confirmaci?n de ejecuci?n completa ni capturas. No se declara esta UAT aprobada.

## 1. Identificaci?n

| Campo | Valor |
|---|---|
| Issue | [CAL-153 ? UAT ? Cocina y tiempo real](https://linear.app/callejon19/issue/CAL-153) |
| Padre | [CAL-117 ? UAT con usuarios por rol](https://linear.app/callejon19/issue/CAL-117) |
| Tipo / escenario | UAT / UAT-08 |
| Entorno preparado | Local, PC y Android; no constituye evidencia de Azure |
| Frontend PC | `http://localhost:3000` |
| Frontend Android | `http://192.168.1.10:3000`; confirmar IP Wi-Fi vigente |
| API | Proxy relativo `/api/v1/*` de Next.js al backend local |
| Restaurante ficticio | `demo-15-datos` |
| Commit realmente servido por frontend / backend | Pendiente de verificar; no inferir del HEAD del repositorio ni del nombre del JAR |
| Navegador / dispositivo | PC y Chrome en Android; versiones y modelo pendientes |
| Versi?n documental | v1 ? 2026-10-28 |

Reglas y formato: [gu?a de pruebas](../../README.md) y [plantilla de evidencia](../../plantilla-evidencia.md). No registrar contrase?as, tokens ni cookies.

## 2. Responsables

| Rol | Responsable |
|---|---|
| Asignaci?n en Linear consultada el 2026-10-28 | Valeria Mercado |
| Ejecutor real | Pendiente de registrar |
| Participante | P1 ? Cocinero; perfil por confirmar, sin datos personales |
| Revisor independiente | Pendiente; distinto del ejecutor |

## 3. Fechas

Hora local: `America/Mexico_City`. La fecha documental no representa ejecuci?n.

| Actividad | Inicio planeado | Fin planeado | Inicio real | Fin real |
|---|---|---|---|---|
| Preparaci?n de la sesi?n | Pendiente | Pendiente | Pendiente | Pendiente |
| Ejecuci?n de casos | Pendiente | Pendiente | Pendiente | Pendiente |
| Re-prueba y revisi?n | Pendiente | Pendiente | Pendiente | Pendiente |

## 4. Alcance y criterios

| Criterio de CAL-153 | Caso |
|---|---|
| Los pedidos aparecen solos, sin recargar la p?gina. | C01 |
| Distingue qu? pedido lleg? primero y cu?nto lleva esperando. | C02 |
| Al marcarlo como listo, el mesero lo ve. | C03 |

**Fuera de alcance:** aprobar UAT sin observaci?n del participante, sustituir la app m?vil por Chrome Android, atribuir una ejecuci?n local al despliegue de Azure o presentar pruebas automatizadas como aceptaci?n del usuario.

## 5. Preparaci?n

- [ ] Confirmar backend disponible, frontend accesible y versi?n servida.
- [ ] Registrar participantes, revisor, dispositivos y fechas.
- [ ] Usar cuentas activas del mismo restaurante con los roles necesarios.
- [ ] Preparar datos ficticios y anotar sus folios sin borrar pedidos ajenos.
- [ ] Mantener separadas las sesiones de PC y Android.
- [ ] Disponer de los pedidos de CAL-151 y CAL-152 para cubrir el escenario completo.
- [ ] Tener sesiones de Cocina y Mesero abiertas antes de los cambios.

El guion local ampliado est? en [sesion-local-pc-android.md](sesion-local-pc-android.md). La rama `feat/cal-153-uat-cocina-tiempo-real` contiene una correcci?n de refresco; este PR documental no la integra ni confirma que est? desplegada. Un fallo de refresco debe registrarse con la versi?n que realmente se sirva. No se exige WebSocket ni sonido: los criterios se refieren al resultado visible.

## 6. Casos reproducibles

### C01 ? Recepci?n autom?tica en cocina

**Rol:** Cocinero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Abrir cocina en PC antes del env?o. Generar pedidos identificables de CAL-151 (web) y CAL-152 (app m?vil), anotar folios y horas y observar el tablero sin recargar.

**Resultado esperado:** Ambos pedidos aparecen autom?ticamente con sus datos correctos. Chrome Android solo cubre web; el pedido de la app m?vil se comprueba por separado.

**Resultado obtenido:** Pendiente.

**Captura prevista, todav?a no existente:** `capturas/C01-resultado.png`. Registrar folio(s), hora de inicio/fin y observaciones del participante.

### C02 ? Orden y tiempo de espera

**Rol:** Cocinero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Con dos pedidos visibles, pedir al cocinero identificar el primero y su tiempo de espera; contrastar con las horas registradas.

**Resultado esperado:** El participante distingue orden y antig?edad sin ayuda. Con un solo pedido la comparaci?n queda pendiente.

**Resultado obtenido:** Pendiente.

**Captura prevista, todav?a no existente:** `capturas/C02-resultado.png`. Registrar folio(s), hora de inicio/fin y observaciones del participante.

### C03 ? Pedido listo visible para el mesero

**Rol:** Cocinero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Mantener la comanda del mismo folio abierta y visible en el mesero. En PC, pasar todos sus productos de Pendiente a En preparaci?n y Listo. Observar el mesero sin recargar y anotar el tiempo real. Si no cambia, capturar antes de recargar; recargar despu?s solo para diagn?stico.

**Resultado esperado:** El mesero ve el pedido Lista sin recarga manual. Si el cambio aparece solo tras recargar, registrar fallo de actualizaci?n autom?tica. Repetir por separado con la app m?vil.

**Resultado obtenido:** Pendiente.

**Captura prevista, todav?a no existente:** `capturas/C03-resultado.png`. Registrar folio(s), hora de inicio/fin y observaciones del participante.

## 7. Defectos y limitaciones

No se abren defectos ni se declaran resultados reales a partir de instrucciones. Al observar un fallo, registrar descripci?n, severidad, folio, versi?n, captura e issue correspondiente. No marcar como aprobado un paso resuelto mediante recarga manual si exig?a actualizaci?n autom?tica.

## 8. Resultados y aceptaci?n

| Caso | Resultado real | Estado | Evidencia real | Aceptado por participante |
|---|---|---|---|---|
| C01 | Pendiente | No ejecutado / sin confirmar | Pendiente | Pendiente |
| C02 | Pendiente | No ejecutado / sin confirmar | Pendiente | Pendiente |
| C03 | Pendiente | No ejecutado / sin confirmar | Pendiente | Pendiente |

La hoja de aceptaci?n se prepara en [registro-aceptacion.md](registro-aceptacion.md). La documentaci?n conserva solo el perfil del participante; cualquier hoja firmada se entrega por el canal autorizado, sin datos personales en el repositorio.

## 9. Declaraci?n de autenticidad

Este documento registra la preparaci?n y los resultados esperados. No contiene capturas, folios observados, aprobaci?n del participante ni declaraci?n de ejecuci?n completa. Completar con evidencia real durante la sesi?n.

## 10. Revisi?n independiente

Pendiente de revisor, fecha, hallazgos y veredicto. La publicaci?n del documento no cierra el issue ni aprueba la UAT.
