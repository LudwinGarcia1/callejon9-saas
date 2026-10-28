# CAL-153 ? Guion de sesi?n local: PC y Android

> Preparaci?n para ejecutar con el usuario. No hay resultados observados, capturas ni aceptaci?n UAT registrados. Este ensayo local no sustituye la sesi?n formal de Azure descrita en [README.md](README.md).

## Entorno y alcance

- Restaurante ficticio: `demo-15-datos`.
- PC: web de cocina en `http://localhost:3000/kitchen`.
- Android: Chrome, web del mesero en `http://192.168.1.10:3000/waiter`; confirmar la IP Wi-Fi si cambia.
- API: detr?s del proxy de Next.js; el navegador usa `/api/v1/*` relativo.
- Preparaci?n comprobada el 2026-10-28: backend local `/actuator/health` respondi? `UP`. Esto no demuestra los casos UAT.
- Versi?n del backend en ejecuci?n: pendiente de verificar; no se atribuye un SHA al JAR sin evidencia.
- Versi?n del frontend servido: pendiente de confirmar en el proceso de desarrollo; no asumir que sirve esta rama.
- Participante, ejecutor, revisor, versi?n de Android/Chrome y fechas reales: pendientes de registrar.
- Las credenciales se comparten por el canal de coordinaci?n; no guardar contrase?as ni cookies aqu?.

La web abierta en Chrome Android no es la aplicaci?n m?vil de CAL-152. Este recorrido comprueba web en dos dispositivos. La recepci?n del pedido de la app m?vil queda pendiente.

## Preparaci?n

1. En PC, abrir una ventana de inc?gnito e iniciar sesi?n con una cuenta activa de rol Cocina del restaurante de prueba.
2. En Android, mantener abierta la cuenta Mesero del mismo restaurante. No iniciar ambas cuentas en pesta?as del mismo contexto del navegador.
3. Mantener visibles ambas pantallas, sin minimizar ni bloquear Android; registrar el comportamiento en primer plano.
4. Anotar el folio de la mesa 30 y su estado. Su liberaci?n se realiz? antes en esta conversaci?n, pero no se ha confirmado su estado actual. No cancelar ni cobrar pedidos para este caso sin registrar el motivo.
5. Para probar recepci?n autom?tica, abrir cocina antes de enviar un pedido nuevo. Si la mesa 30 ya fue enviada, sirve para C02/C03; C01 necesita otro pedido nuevo en una mesa libre preparada.

## C01 ? Recepci?n sin recargar

1. Mantener el tablero de cocina abierto y visible en PC.
2. Desde Android, abrir una mesa libre, indicar cuatro comensales, agregar dos productos y enviar a cocina una sola vez.
3. Anotar mesa, folio, cantidades, notas y hora de env?o.
4. Observar cocina sin recargar ni cambiar de pesta?a. Registrar tiempo observado; revisar primero tras 5?10 segundos. Ese intervalo es una referencia operativa, no un umbral de aceptaci?n del issue.
5. Comparar los datos recibidos con el pedido del mesero.

**Esperado:** aparece el pedido autom?ticamente con los datos correctos.
**Evidencias previstas, a?n no existentes:** `capturas/C01-P01-envio-mesero-android.png`, `capturas/C01-P02-recepcion-cocina-pc.png`.

## C02 ? Orden y antig?edad

1. Con dos pedidos visibles, identificar el que lleg? primero.
2. Comparar con las horas de env?o anotadas en C01. Si solo hay uno, dejar pendiente la comparaci?n entre pedidos.
3. Observar el tiempo de espera mostrado y que avance durante la observaci?n.

**Esperado:** el participante identifica cu?l lleg? primero y cu?nto lleva esperando; el tablero permite distinguirlo sin ayuda.
**Evidencia prevista:** `capturas/C02-P01-orden-antiguedad.png`.

## C03 ? Preparaci?n y aviso al mesero

1. Dejar la comanda correspondiente abierta y visible en Android antes de cambiar estados en cocina.
2. En PC, marcar cada producto primero En preparaci?n y despu?s Listo. Registrar el folio.
3. Observar Android sin recargar. Anotar qu? cambia y cu?nto tarda.
4. Si no cambia, capturar el estado antes de recargar; despu?s recargar solo para diagnosticar persistencia. No declarar aprobado un refresco autom?tico que requiri? recarga manual.

**Esperado:** el mesero observa el estado final Lista del pedido correcto. La posible salida de la tarjeta del tablero de pendientes debe contrastarse con la comanda del mesero.
**Evidencias previstas:** `capturas/C03-P01-cocina-listo.png`, `capturas/C03-P02-mesero-android-listo.png`.

**Limitaci?n local comprobada:** en el checkout principal revisado, la consulta de detalle de la orden del mesero no configura refresco peri?dico. La rama `feat/cal-153-uat-cocina-tiempo-real` s? contiene una correcci?n de refresco, pero no se ha confirmado que el servidor local la ejecute. Si C03 falla, registrar el resultado; no atribuir a esta rama una aprobaci?n de otra versi?n. Cocina consulta peri?dicamente en el c?digo local. Estos hallazgos por lectura de c?digo no sustituyen la prueba manual.

## Registro por completar durante la sesi?n

| Caso | Estado | Folio(s) | Inicio / fin | Resultado obtenido | Evidencia real |
|---|---|---|---|---|---|
| C01 | No ejecutado | Pendiente | Pendiente | Pendiente de confirmar por el usuario | Pendiente |
| C02 | No ejecutado | Pendiente | Pendiente | Pendiente de confirmar por el usuario | Pendiente |
| C03 | No ejecutado | Pendiente | Pendiente | Pendiente de confirmar por el usuario | Pendiente |

Al terminar, registrar observaciones del participante, defectos con severidad y aceptaci?n s?/no. Mantener esta sesi?n separada de la UAT formal y de la verificaci?n en la app m?vil. La siguiente UAT operativa es CAL-154 (cobro); conservar la comanda lista para ella.
