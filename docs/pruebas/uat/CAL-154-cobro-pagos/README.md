# Evidencia de prueba · CAL-154 — UAT · Cobro y pagos

> **Estado de este documento: preparado, sin ejecutar.** Las secciones 1, 2, 4, 5 y 6 (precondiciones, pasos y resultados esperados) están llenas de antemano; todo lo que dice *«llenar en la sesión»* se escribe **durante** la ejecución. Hasta entonces ningún caso tiene resultado y el issue sigue en Todo.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-154](https://linear.app/callejon19/issue/CAL-154) — UAT · Cobro y pagos |
| Historia o issue padre | [CAL-117](https://linear.app/callejon19/issue/CAL-117) — Ejecutar pruebas de aceptación (UAT) con usuarios por rol |
| Tipo de prueba | UAT |
| Escenario | UAT-09 · participante: cajero |
| Módulo | Cobro y pagos |
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
| Revisor | *llenar después* | **Distinto del ejecutor y del participante.** Llena la sección 10 |
| Participante | P1 — integrante del equipo en el rol de cajero *(ver «Desviación declarada»)* | Se identifica solo como P1; la hoja firmada se adjunta en Linear |

**Desviación declarada.** `docs/pruebas/README.md` pide que el participante de una UAT no sea integrante del equipo. El proyecto no tiene clientes ni restaurantes piloto, así que P1 es un integrante que actúa como cajero. Para que la prueba conserve valor:

- P1 **no** puede ser quien implementó el cobro ni quien ejecutó la SIT de cobro (CAL-141).
- P1 no lee este documento antes de la sesión: recibe solo [`guion-participante.md`](guion-participante.md).
- El facilitador no da indicaciones sobre la interfaz; toda ayuda se anota en la sección 9.
- La desviación se repite en la sección 8 y en el informe de CAL-117.

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-117) | 2026-10-20 | 2026-10-26 | 2026-10-21 | | Las fechas se movieron porque las SIT de CAL-141 y CAL-144 se reabrieron el 2026-10-27 |
| Esta tarea (CAL-154) | *por acordar* | | | | |
| Preparación del entorno y datos | | | | | |
| Ejecución de los casos | | | | | |
| Re-prueba de defectos *(si hubo)* | | | | | |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que una persona en el rol de cajero puede cobrar sin ayuda una cuenta real del turno dividida entre efectivo y tarjeta, con propina, y que entiende el total, la propina y el cambio que le muestra el sistema.

**Fuera de alcance.**
- Las respuestas de la API y los códigos de error, que cubre la SIT [CAL-141](https://linear.app/callejon19/issue/CAL-141).
- El ticket en PDF y la búsqueda por folio, que cubre UAT-10 ([CAL-155](https://linear.app/callejon19/issue/CAL-155)).
- El arqueo del turno de caja.
- Los métodos Transferencia y Mercado Pago.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. El total, la propina y el cambio son correctos y se entienden. | C01 |
| 2. La venta cierra y la mesa queda libre para el mesero. | C01 (pasos 9 y 10) |
| 3. Si los pagos no alcanzan, el sistema lo explica y no cierra la cuenta. | C02 |

## 5. Preparación

**Dependencias que deben estar cerradas antes de ejecutar**

- [ ] SIT [CAL-141](https://linear.app/callejon19/issue/CAL-141) · Cobro y pagos **aprobada** por un revisor. Hoy está en In Progress: se reabrió el 2026-10-27. Ejecutar antes la invalida («UAT ejecutada antes de aprobar la SIT del mismo módulo»).
- [ ] UAT-06 ([CAL-151](https://linear.app/callejon19/issue/CAL-151)) ejecutada. Su cuenta de cuatro comensales es la que se cobra en C01.
- [ ] UAT-08 ([CAL-153](https://linear.app/callejon19/issue/CAL-153)) marcó esa cuenta como lista, o la sesión acuerda cobrarla desde «enviada».

> Si UAT-06 no se ejecutó, el facilitador captura una cuenta equivalente antes de la sesión: cuatro platillos de precios distintos, con al menos una nota. Lo anota aquí como desviación. Esa preparación no la ve el participante.

**Precondiciones del entorno**

- [ ] Backend encendido y `/actuator/health` en `UP`. Guardar la salida literal en `respuestas/health.txt` con `curl.exe -i https://app-callejon9-api-prod-3878c6.azurewebsites.net/actuator/health`.
- [ ] Frontend accesible en `https://restohub.azurewebsites.net/login`. El plan F1 se duerme a los ~20 min sin uso: abre la página 5 minutos antes.
- [ ] Commit desplegado: `<SHA>`, igual al del registro de despliegue.
- [ ] **Turno de caja.** Si el commit desplegado incluye CAL-98 (PR #93, 2026-10-26), cobrar exige un turno abierto; sin él aparece «Abre un turno de caja antes de cobrar una cuenta.». La apertura es el paso 2 de C01 y forma parte de lo que se observa. Si el commit es anterior, el paso 2 queda como «No aplica».
- [ ] Calculadora a la mano y la hoja de control de cobros (anexo A) impresa. CAL-157 la usa para cuadrar los totales.

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Cuenta del cajero | `caja@pruebas.com` | Semilla del restaurante `pruebas`. La contraseña vive fuera del repositorio |
| Cuenta del mesero (paso 10) | `mesero@pruebas.com` | Semilla del restaurante `pruebas` |
| Cuenta de C01 | Folio *llenar*, mesa *llenar*, 4 comensales | UAT-06 (CAL-151) |
| Cuenta de C02 | Folio *llenar*, mesa *llenar* | UAT-07 (CAL-152, app móvil) o la que prepare el facilitador |
| Fondo inicial del turno | $500.00 | Lo captura P1 en el paso 2 |

> Nunca escribas contraseñas, tokens ni el valor de cookies. Si una captura los muestra, tápalos antes de subirla.

**Valores esperados de C01.** El facilitador los calcula **antes** de la sesión, con el subtotal real de la cuenta, y no se los muestra a P1.

| Concepto | Fórmula | Valor esperado |
|---|---|---|
| Subtotal | Suma de partidas de la cuenta de UAT-06 | |
| Propina (10 %) | Subtotal × 0.10, redondeado a centavos | |
| Total a cobrar | Subtotal + propina | |
| Pago con tarjeta | La mitad del total, a centavos | |
| Efectivo que entrega el cliente | Lo que falta, redondeado hacia arriba a la siguiente centena | |
| Cambio | Efectivo entregado − (total − tarjeta) | |

## 6. Casos de prueba

### C01 — El cajero cobra la cuenta dividida entre tarjeta y efectivo con propina, y entiende el cambio

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 y 2 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (P1); WAITER solo en el paso 10, lo opera el facilitador |
| Inicio / fin de ejecución | *llenar* / *llenar* |
| Estado | *llenar en la sesión* |
| Defecto vinculado | *llenar o «—»* |

**Precondición.** La cuenta de UAT-06 está abierta y lista para cobrar. P1 tiene el guion y nunca ha usado la pantalla de caja en esta sesión. El cronómetro está en cero.

**Pasos para reproducir**

| # | Acción del participante | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Inicia sesión en `/login` | `caja@pruebas.com` | Llega directo a **Caja** (`/cashier`) con la lista de cuentas por cobrar | | `capturas/C01-P01-caja-inicial.png` |
| 2 | *(Si aplica CAL-98)* Abre **Turnos de caja** → **Abrir caja** con el fondo inicial | $500.00 | El turno queda **En curso** con fondo inicial $500.00 | | `capturas/C01-P02-turno-abierto.png` |
| 3 | En **Caja**, elige la cuenta de UAT-06 | Folio de la tabla de datos | Aparece **Cobrar cuenta** con el folio, las cuatro partidas y el **Subtotal** | | `capturas/C01-P03-cuenta-seleccionada.png` |
| 4 | Captura la propina | Propina (%) = 10 | **Propina** y **Total a cobrar** coinciden con la tabla de valores esperados | | `capturas/C01-P04-propina.png` |
| 5 | Pulsa **Agregar pago por monto libre**, elige **Tarjeta** y captura la mitad del total | Monto de la tabla | **Falta cubrir** baja a la otra mitad | | `capturas/C01-P05-pago-tarjeta.png` |
| 6 | Agrega un segundo pago en **Efectivo** con el monto que entrega el cliente | Efectivo de la tabla | **Falta cubrir** = $0.00 y **Cambio de efectivo** = valor esperado | | `capturas/C01-P06-pago-efectivo-cambio.png` |
| 7 | El facilitador pregunta, sin señalar la pantalla: «¿Cuánto cobraste en total, cuánto fue de propina y cuánto cambio entregas?» | — | P1 responde con las tres cifras correctas leyendo la pantalla | *(respuesta literal de P1)* | — |
| 8 | Pulsa **Confirmar cobro** | — | Aviso «Cobro registrado. Ticket TCK-…» y el resumen del ticket con subtotal, propina, pagos, cambio y total cobrado; la cuenta desaparece de la lista de Caja | | `capturas/C01-P08-cobro-registrado.png` |
| 9 | Anota el folio del ticket en la hoja de control (anexo A) | — | El folio se anota completo; CAL-155 lo usa después | *(folio)* | — |
| 10 | El facilitador entra como mesero en otra ventana o dispositivo y abre **Mesas** | `mesero@pruebas.com` | La mesa de la cuenta aparece **libre** | | `capturas/C01-P10-mesa-libre.png` |

**Observaciones.** *Llenar en la sesión:* dudas de P1, tiempo por paso y textos que no entendió.

### C02 — Con pagos que no alcanzan, el sistema explica lo que falta y no deja cerrar la cuenta

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (P1) |
| Inicio / fin de ejecución | *llenar* / *llenar* |
| Estado | *llenar en la sesión* |
| Defecto vinculado | *llenar o «—»* |

**Precondición.** Hay una segunda cuenta abierta (la de C02 en la tabla de datos) y el turno de caja sigue abierto.

**Pasos para reproducir**

| # | Acción del participante | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | El facilitador dice: «El cliente solo trae $100 en efectivo; intenta cobrarle». En **Caja**, P1 elige la segunda cuenta | Folio de C02 | Aparece **Cobrar cuenta** con su total | | `capturas/C02-P01-cuenta.png` |
| 2 | Agrega un pago en **Efectivo** menor al total | $100.00 *(o menos que el total)* | **Falta cubrir** muestra la diferencia; **Confirmar cobro** queda deshabilitado | | `capturas/C02-P02-falta-cubrir.png` |
| 3 | El facilitador pregunta: «¿Por qué no puedes cobrar y qué le dirías al cliente?» | — | P1 explica que falta dinero y dice cuánto, leyendo **Falta cubrir** | *(respuesta literal de P1)* | — |
| 4 | P1 sale de la pantalla sin cobrar y vuelve a **Caja** | — | La cuenta sigue en la lista de cuentas por cobrar; no se generó ticket | | `capturas/C02-P04-cuenta-sigue-abierta.png` |

**Observaciones.** La interfaz impide enviar un cobro insuficiente con el botón deshabilitado. El rechazo del servidor («Los pagos no cubren el total de la cuenta.», HTTP 422) lo prueba la SIT CAL-141, no esta UAT.

> Después de C02, esta cuenta se cobra completa fuera de la evaluación, o se deja abierta y se anota en el anexo A. CAL-157 necesita saber si cuenta o no en el total del día.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| *llenar o «Ninguno»* | | | | | |

> Severidad según `docs/pruebas/README.md`. Un defecto crítico o alto impide cerrar CAL-117 hasta su re-prueba.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | | | | |

**Desviaciones y limitaciones.**
- El participante es un integrante del equipo, no un cliente (ver sección 2).
- *Llenar:* cuenta de UAT-06 preparada por el facilitador, si fue el caso.
- *Llenar:* turno de caja aplicable o no, según el commit desplegado.

**Conclusión del ejecutor.** *Llenar en la sesión.*

## 9. Anexo UAT — aceptación de usuario

| Campo | Valor |
|---|---|
| Perfil del participante | P1 — integrante del equipo en el rol de cajero, que no implementó el cobro ni ejecutó CAL-141; ¿conocía la pantalla de caja? *Sí / No* |
| Modalidad | *Presencial · Remota (con pantalla compartida)* |
| Facilitador | *llenar* |
| Duración medida | *minutos, con cronómetro, de C01-P1 a C02-P4* |
| Ayuda brindada | *«Ninguna» o en qué paso y qué se dijo* |
| Comentarios del participante | *Palabras de P1, tomadas al momento* |
| ¿Acepta el escenario? | *Sí · No · Con observaciones* |
| Hoja de aceptación firmada | Adjunta en Linear a CAL-154 y a CAL-117; no se sube al repositorio. Responsable: *llenar* |

### Anexo A — Hoja de control de cobros de la sesión

La llena el facilitador en cada cobro de la sesión de caja (UAT-09 y cualquier otro). CAL-157 compara contra esta tabla.

| Folio de ticket | Mesa | Subtotal | Propina | Total cobrado | Tarjeta | Efectivo aplicado | Hora |
|---|---|---|---|---|---|---|---|
| | | | | | | | |
| | | | | | | | |
| **Totales** | | | | | | | |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL, la fecha y hora del sistema y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado existe en el repositorio y corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** *llenar* · **Fecha:** *llenar*
