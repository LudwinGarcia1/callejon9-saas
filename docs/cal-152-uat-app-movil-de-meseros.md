# CAL-152 — UAT-07: Operación en Movilidad con App Móvil de Meseros

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-07: Toma de Pedidos en Teléfono Móvil y Tolerancia a Desconexión**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-152](https://linear.app/callejon19/issue/CAL-152/uat-app-movil-de-meseros)
- **Escenario:** UAT-07 — App móvil de meseros.
- **Participante / Perfil:** Mesero con dispositivo móvil Android / iOS.
- **Evaluador / Facilitador:** Braulio Hernández Mejía.
- **Entorno:** Producción / Staging sobre Azure App Service y Expo / React Native (`RestoHub Meseros`).
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El mesero utiliza su smartphone para recorrer el salón, abre la comanda de la Mesa 7 directamente al lado de los comensales, selecciona los platillos desde la app móvil, prueba la ergonomía táctil con una sola mano y envía la comanda a cocina simulando además una pérdida momentánea de cobertura Wi-Fi.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Paridad Funcional con la Web** | El pedido enviado desde el móvil llega a cocina con la misma estructura que desde la web | El KDS de cocina recibió la comanda instantáneamente con el número de mesa, productos y notas idénticas a las emitidas desde la web. | **Aceptado** |
| **Ergonomía Táctil a Una Mano** | La app se puede operar con una mano; botones y listas tienen touch targets adecuados (> 44x44 dp) | Elementos de selección y botones de incremento/decremento se presionan con comodidad usando el pulgar sin toques erróneos. | **Aceptado** |
| **Resiliencia ante Pérdida de Señal** | Sin conexión, la app alerta claramente y permite reintentar sin perder la orden | Al apagar el Wi-Fi, la app mostró un banner amarillo "Sin conexión con el servidor. Tu orden está a salvo" y permitió reintentar con éxito al volver la señal. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"Es mucho más rápido que usar una libreta o ir hasta la computadora central. La comanda llegó a cocina antes de que yo regresara a la barra."*
- **Firma / Aprobación:** Aprobado para pase a producción.
