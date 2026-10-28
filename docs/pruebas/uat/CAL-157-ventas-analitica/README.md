# Evidencia de prueba · CAL-157 — UAT · Ventas, historial y analítica

> **Estado de este documento: preparado, sin ejecutar.** Las secciones 1, 2, 4, 5 y 6 (precondiciones, pasos y resultados esperados) están llenas de antemano; todo lo que dice *«llenar en la sesión»* se escribe **durante** la ejecución. Hasta entonces ningún caso tiene resultado y el issue sigue en Todo.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-157](https://linear.app/callejon19/issue/CAL-157) — UAT · Ventas, historial y analítica |
| Historia o issue padre | [CAL-117](https://linear.app/callejon19/issue/CAL-117) — Ejecutar pruebas de aceptación (UAT) con usuarios por rol |
| Tipo de prueba | UAT |
| Escenario | UAT-12 · participantes: cajero y administrador |
| Módulo | Ventas, historial y analítica |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado | *llenar en la sesión:* SHA desplegado, tomado del registro de despliegue |
| Registro de despliegue | *llenar en la sesión:* enlace al comentario «Despliegue» en Linear |
| Navegador / dispositivo | *llenar en la sesión* |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v0 · 2026-10-28 · preparación (sin ejecutar) |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte Villavicencio | Asignado en Linear |
| Ejecutor de la prueba (facilitador) | *llenar en la sesión* | Conduce la sesión, cronometra, toma capturas y anota; **no** opera la pantalla |
| Revisor | *llenar después* | **Distinto del ejecutor y de los participantes.** Llena la sección 10 |
| Participante P1 | Integrante del equipo en el rol de cajero; de preferencia el mismo P1 de CAL-154 | Se identifica solo como P1 |
| Participante P2 | Integrante del equipo en el rol de administrador | Se identifica solo como P2 |

**Desviación declarada.** `docs/pruebas/README.md` pide que los participantes de una UAT no sean integrantes del equipo. El proyecto no tiene clientes ni restaurantes piloto, así que P1 y P2 son integrantes que actúan cada rol. Para que la prueba conserve valor:

- P2 **no** puede ser quien implementó la analítica ni quien ejecutó la SIT CAL-144.
- Ningún participante lee este documento antes: reciben solo [`guion-participante.md`](guion-participante.md).
- La pregunta del criterio 2 se hace con las palabras exactas del issue, sin pistas.

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-117) | 2026-10-20 | 2026-10-26 | 2026-10-21 | | Las fechas se movieron porque las SIT de CAL-141 y CAL-144 se reabrieron el 2026-10-27 |
| Esta tarea (CAL-157) | *por acordar; al cierre de la sesión de CAL-154* | | | | |
| Preparación del entorno y datos | | | | | |
| Ejecución de los casos | | | | | |
| Re-prueba de defectos *(si hubo)* | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que, al cierre de un turno, el cajero puede consultar las ventas del día y el administrador puede leer la analítica, y que en los dos casos las cifras cuadran con los cobros que se hicieron en la sesión.

**Fuera de alcance.**
- Los cálculos y respuestas de la API, que cubre la SIT [CAL-144](https://linear.app/callejon19/issue/CAL-144).
- La descarga del ticket y la búsqueda por folio, que cubre UAT-10 ([CAL-155](https://linear.app/callejon19/issue/CAL-155)).
- El arqueo del turno de caja.
- Rangos de fechas de otros días.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Los totales coinciden con los cobros hechos durante la sesión. | C01 (historial del cajero), C02 (analítica del administrador) |
| 2. El administrador responde sin ayuda «¿cuánto se vendió hoy y qué fue lo más vendido?». | C02 |

## 5. Preparación

**Dependencias que deben estar cerradas antes de ejecutar**

- [ ] SIT [CAL-144](https://linear.app/callejon19/issue/CAL-144) · Ventas, historial y analítica **aprobada** por un revisor. Hoy está en In Progress: se reabrió el 2026-10-27.
- [ ] UAT-09 ([CAL-154](https://linear.app/callejon19/issue/CAL-154)) ejecutada en la misma sesión. Su anexo A (hoja de control de cobros) es la referencia de esta prueba.

**Precondiciones del entorno**

- [ ] Backend encendido y `/actuator/health` en `UP`. Guardar la salida literal en `respuestas/health.txt`.
- [ ] Frontend accesible en `https://restohub.azurewebsites.net/login`.
- [ ] Commit desplegado: `<SHA>`, igual al del registro de despliegue.
- [ ] **Línea base, tomada antes del primer cobro de la sesión.** El restaurante `pruebas` puede tener ventas previas del mismo día, de otras pruebas. Antes de UAT-09, el facilitador abre **Historial** como cajero con el rango de hoy y anota «Ventas en el rango» y «Total cobrado» en la tabla siguiente, con su captura. Sin línea base el criterio 1 no se puede comprobar: el caso queda ⛔ Bloqueado.

| Línea base (antes de la sesión) | Valor | Hora | Evidencia |
|---|---|---|---|
| Ventas en el rango (hoy) | | | `capturas/C00-linea-base-historial.png` |
| Total cobrado (hoy) | | | misma captura |

**Valores esperados.** Se calculan al cierre, con el anexo A de CAL-154.

| Concepto | Fórmula | Valor esperado |
|---|---|---|
| Ventas del día | Línea base + número de cobros de la sesión | |
| Total cobrado del día | Línea base + suma de «Total cobrado» de la sesión (incluye propina) | |
| Pagos con tarjeta | Suma de la columna «Tarjeta» de la sesión, más los de la línea base si se anotaron | |
| Producto con más ingreso en la sesión | Del detalle de las cuentas cobradas | |

> **Sobre «lo más vendido».** El **Pareto de productos** de Analítica ordena por **participación de ingresos**, no por unidades. Si P2 responde con el producto de mayor ingreso, la respuesta es correcta según la pantalla. Si P2 dice que no sabe cuántas unidades se vendieron, se anota como observación: la pantalla no muestra unidades, y eso puede ser un hallazgo de usabilidad válido.

> **Sobre la propina.** En Historial, «Total cobrado» incluye la propina. El Pareto reparte el ingreso de los productos, sin propina. No se comparan entre sí.

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Cuenta del cajero | `caja@pruebas.com` | Semilla del restaurante `pruebas`. La contraseña vive fuera del repositorio |
| Cuenta del administrador | `admin@pruebas.com` | Semilla del restaurante `pruebas` |
| Cobros de la sesión | Anexo A de CAL-154 | UAT-09 y los demás cobros del turno |

## 6. Casos de prueba

### C01 — El cajero consulta las ventas del día y cuadran con los cobros de la sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (P1) |
| Inicio / fin de ejecución | *llenar* / *llenar* |
| Estado | *llenar en la sesión* |
| Defecto vinculado | *llenar o «—»* |

**Precondición.** Terminaron todos los cobros de la sesión y están anotados en el anexo A de CAL-154. P1 sigue con la sesión de cajero abierta.

**Pasos para reproducir**

| # | Acción del participante | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El facilitador dice: «Terminó el turno; revisa cuánto se vendió hoy». P1 abre **Historial** desde el menú | — | Aparece **Historial de ventas** con «Ventas en el rango» y «Total cobrado» | | `capturas/C01-P01-historial.png` |
| 2 | Si el rango no es el de hoy, P1 lo ajusta | Fecha de hoy | El rango muestra solo el día de hoy | | `capturas/C01-P02-rango-hoy.png` |
| 3 | P1 lee en voz alta cuántas ventas hubo y cuánto se cobró | — | Las dos cifras coinciden con la tabla de valores esperados | *(respuesta literal y cifras en pantalla)* | `capturas/C01-P03-totales.png` |
| 4 | P1 revisa el desglose de pagos | — | El monto en Tarjeta coincide con la suma del anexo A más la línea base | | `capturas/C01-P04-desglose-pagos.png` |
| 5 | P1 localiza en la tabla **Ventas** la venta de UAT-09 por su folio | Folio del anexo A | La venta aparece con su total | | `capturas/C01-P05-venta-uat09.png` |

**Observaciones.** *Llenar en la sesión.*

### C02 — El administrador responde sin ayuda cuánto se vendió hoy y qué fue lo más vendido

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 y 2 |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (P2) |
| Inicio / fin de ejecución | *llenar* / *llenar* |
| Estado | *llenar en la sesión* |
| Defecto vinculado | *llenar o «—»* |

**Precondición.** P2 no ha visto la pantalla de Analítica en esta sesión. El cronómetro está en cero.

**Pasos para reproducir**

| # | Acción del participante | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | P2 inicia sesión en `/login` | `admin@pruebas.com` | Llega a **Administración** con el menú completo | | `capturas/C02-P01-admin.png` |
| 2 | El facilitador lee, **literalmente** y una sola vez: «¿Cuánto se vendió hoy y qué fue lo más vendido?». Arranca el cronómetro. | — | P2 navega solo, por ejemplo a **Analítica** o **Historial** | *(ruta que siguió)* | `capturas/C02-P02-pantalla-elegida.png` |
| 3 | P2 responde en voz alta. Se detiene el cronómetro | — | Cifra del día igual a «Total cobrado del día» esperado, y producto igual al de mayor ingreso en el Pareto | *(respuesta literal, tiempo en segundos)* | `capturas/C02-P03-pareto.png`, `capturas/C02-P03-ventas-por-dia.png` |
| 4 | El facilitador compara **Ventas por día** (barra de hoy, en vista de tabla) con el total esperado | — | Coinciden | | `capturas/C02-P04-ventas-por-dia-tabla.png` |
| 5 | El facilitador compara **Métodos de pago** con el anexo A | — | Efectivo y tarjeta coinciden con lo cobrado en la sesión más la línea base | | `capturas/C02-P05-metodos-pago.png` |

**Observaciones.** *Llenar en la sesión:* toda ayuda que se haya dado, aunque sea mínima, se anota aquí y en la sección 9. Con ayuda, el criterio 2 se marca ❌.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| *llenar o «Ninguno»* | | | | | |

> Severidad según `docs/pruebas/README.md`. Un total que no cuadra con los cobros reales es al menos **Alta**.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | | | | |

**Desviaciones y limitaciones.**
- Los participantes son integrantes del equipo, no clientes (ver sección 2).
- *Llenar:* si hubo ventas del día ajenas a la sesión, cómo se descontaron con la línea base.

**Conclusión del ejecutor.** *Llenar en la sesión.*

## 9. Anexo UAT — aceptación de usuario

| Campo | Valor |
|---|---|
| Perfil de los participantes | P1 — integrante del equipo en el rol de cajero. P2 — integrante del equipo en el rol de administrador, que no implementó la analítica ni ejecutó CAL-144. ¿Conocían estas pantallas? *Sí / No* |
| Modalidad | *Presencial · Remota (con pantalla compartida)* |
| Facilitador | *llenar* |
| Duración medida | C01: *min* · C02: *seg hasta la respuesta* |
| Ayuda brindada | *«Ninguna» o en qué paso y qué se dijo* |
| Comentarios de los participantes | *Palabras literales de P1 y P2* |
| ¿Aceptan el escenario? | *Sí · No · Con observaciones* |
| Hoja de aceptación firmada | Adjunta en Linear a CAL-157 y a CAL-117; no se sube al repositorio. Responsable: *llenar* |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL, la fecha y hora del sistema y coinciden con los pasos
- [ ] Las cifras de pantalla cuadran con el anexo A de CAL-154 y la línea base
- [ ] El commit probado existe en el repositorio y corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** *llenar* · **Fecha:** *llenar*
