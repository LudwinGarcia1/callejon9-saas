# CAL-156 — UAT-11: Cierre de Turno y Control de Inventarios

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-11: Salidas Operativas de Insumos y Ajustes de Inventario por Conteo Físico**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-156](https://linear.app/callejon19/issue/CAL-156/uat-inventario)
- **Escenario:** UAT-11 — Inventario.
- **Participantes / Perfiles:** Cocinero y Administrador de Restaurante.
- **Evaluadora / Facilitadora:** Mercado Serrano Valeria.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
Al finalizar el turno vespertino, el cocinero registra una merma de preparación de 2 kg de carne molida. Posteriormente, el administrador realiza el corte de almacén, audita las existencias resultantes y ejecuta un ajuste por conteo físico para conciliar faltantes de almacén.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Conciliación Matemática de Saldos** | La existencia final coincide exactamente con los movimientos registrados (kardex) | El stock bajó de 15.00 kg a 13.00 kg tras la salida de 2 kg, quedando la bitácora con usuario, hora y motivo. | **Aceptado** |
| **Alertas de Nivel Mínimo / Reorden** | El administrador identifica con claridad qué insumos están por debajo de su umbral mínimo | El sistema marcó en ámbar el insumo con etiqueta "Por debajo del mínimo (5 kg requeridos)", permitiendo planificar la compra. | **Aceptado** |
| **Ajuste por Conteo Físico** | El administrador puede ajustar la existencia real tras el recuento físico | Se ajustó a 12.50 kg justificando "Merma en merma de descongelación", recalculando el nivel de existencias con éxito. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones de los participantes:**
  > *"Tener el registro de quién y por qué sacó insumos de la cocina resuelve los problemas de pérdidas en el almacén al final del día."*
- **Firma / Aprobación:** Aprobado para pase a producción.
