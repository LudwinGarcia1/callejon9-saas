# CAL-151 — UAT-06: Captura y Envío de Pedidos del Mesero (Web)

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-06: Captura de Comanda, Modificadores/Notas y Envío a Cocina**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-151](https://linear.app/callejon19/issue/CAL-151/uat-pedidos-del-mesero)
- **Escenario:** UAT-06 — Pedidos del mesero (Web).
- **Participante / Perfil:** Mesero en terminal de salón.
- **Evaluador / Facilitador:** Braulio Hernández Mejía.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El mesero abre la Mesa 5, ingresa un pedido para 4 comensales seleccionando entradas, platos fuertes y bebidas, añade una nota de cocina ("Sin cebolla, término medio") y envía la comanda a producción, evaluando la claridad de folios y la capacidad de corrección previa al envío.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Autonomía en la Captura** | Captura el pedido completo sin ayuda externa | Completó la selección de 6 ítems con notas particulares en 1 min 15 seg de manera fluida. | **Aceptado** |
| **Folio, Total y Mesa Ocupada** | Recibe folio único, total acumulado y la mesa queda marcada como ocupada | El sistema asignó el folio `ORD-2026-0881`, calculó el total exacto ($740.00) y la Mesa 5 se marcó como ocupada en el sistema central. | **Aceptado** |
| **Corrección Previa al Envío** | Si hay error en la comanda antes de enviar a cocina, permite corregir o cancelar | El mesero eliminó un platillo duplicado y modificó la nota de la carne antes de presionar "Enviar a cocina" sin trabas. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"El resumen de la comanda es muy claro antes de mandar a cocina. Poder rectificar una comanda equivocada antes de enviarla evita desperdicios y regaños con el chef."*
- **Firma / Aprobación:** Aprobado para pase a producción.
