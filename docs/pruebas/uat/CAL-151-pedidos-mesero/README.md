# Evidencia de prueba ? CAL-151 ? UAT ? Pedidos del mesero

> **Estado: guion preparado; resultados y aceptaci?n pendientes.** En la conversaci?n se dieron instrucciones y se prepararon cuentas y una mesa; no se recibi? confirmaci?n de ejecuci?n completa ni capturas. No se declara esta UAT aprobada.

## 1. Identificaci?n

| Campo | Valor |
|---|---|
| Issue | [CAL-151 ? UAT ? Pedidos del mesero](https://linear.app/callejon19/issue/CAL-151) |
| Padre | [CAL-117 ? UAT con usuarios por rol](https://linear.app/callejon19/issue/CAL-117) |
| Tipo / escenario | UAT / UAT-06 |
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
| Asignaci?n en Linear consultada el 2026-10-28 | Braulio Hern?ndez Mej?a |
| Ejecutor real | Pendiente de registrar |
| Participante | P1 ? Mesero; perfil por confirmar, sin datos personales |
| Revisor independiente | Pendiente; distinto del ejecutor |

## 3. Fechas

Hora local: `America/Mexico_City`. La fecha documental no representa ejecuci?n.

| Actividad | Inicio planeado | Fin planeado | Inicio real | Fin real |
|---|---|---|---|---|
| Preparaci?n de la sesi?n | Pendiente | Pendiente | Pendiente | Pendiente |
| Ejecuci?n de casos | Pendiente | Pendiente | Pendiente | Pendiente |
| Re-prueba y revisi?n | Pendiente | Pendiente | Pendiente | Pendiente |

## 4. Alcance y criterios

| Criterio de CAL-151 | Caso |
|---|---|
| Captura el pedido completo sin ayuda. | C01 |
| Si se equivoca antes de enviar a cocina, puede corregir o cancelar. | C02 |
| Recibe folio y total, y la mesa queda ocupada. | C03 |

**Fuera de alcance:** aprobar UAT sin observaci?n del participante, sustituir la app m?vil por Chrome Android, atribuir una ejecuci?n local al despliegue de Azure o presentar pruebas automatizadas como aceptaci?n del usuario.

## 5. Preparaci?n

- [ ] Confirmar backend disponible, frontend accesible y versi?n servida.
- [ ] Registrar participantes, revisor, dispositivos y fechas.
- [ ] Usar cuentas activas del mismo restaurante con los roles necesarios.
- [ ] Preparar datos ficticios y anotar sus folios sin borrar pedidos ajenos.
- [ ] Mantener separadas las sesiones de PC y Android.
- [ ] Tener una mesa libre, cat?logo activo y funcionalidad de notas/correcci?n disponible. El PR [#111](https://github.com/LudwinGarcia1/callejon9-saas/pull/111) contiene cambios relacionados; no se asume integrado ni desplegado. Si falta el control, registrar bloqueo o fallo.

La mesa 30 se liber? durante la preparaci?n de esta conversaci?n. Su estado actual debe comprobarse antes de abrirla. La preparaci?n no demuestra que se ejecutara el pedido.

## 6. Casos reproducibles

### C01 ? Captura del pedido con notas

**Rol:** Mesero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Abrir una mesa libre para cuatro comensales, elegir dos productos con cantidades distintas y agregar una nota por producto.

**Resultado esperado:** La comanda conserva mesa, cuatro comensales, productos, cantidades y notas correctas.

**Resultado obtenido:** Pendiente.

**Captura prevista, todav?a no existente:** `capturas/C01-resultado.png`. Registrar folio(s), hora de inicio/fin y observaciones del participante.

### C02 ? Correcci?n o cancelaci?n antes del env?o

**Rol:** Mesero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Antes de enviar, corregir cantidad o quitar una l?nea y verificar el total. En una segunda orden de prueba, cancelar antes del env?o y comprobar que libera la mesa.

**Resultado esperado:** Se corrige el pedido sin enviarlo accidentalmente; la orden cancelada libera su mesa.

**Resultado obtenido:** Pendiente.

**Captura prevista, todav?a no existente:** `capturas/C02-resultado.png`. Registrar folio(s), hora de inicio/fin y observaciones del participante.

### C03 ? Env?o con folio, total y mesa ocupada

**Rol:** Mesero. **Estado:** No ejecutado / pendiente de confirmaci?n.

**Acci?n:** Revisar el pedido v?lido, enviarlo a cocina una sola vez y anotar folio y total. Volver a Mesas y abrir la misma comanda.

**Resultado esperado:** El env?o se confirma, se identifica folio y total, la mesa est? ocupada y conserva el pedido.

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
