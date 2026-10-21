# CAL-153 — UAT-08: Gestión de Producción en KDS de Cocina

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-08: Recepción Reactiva, Priorización y Avance de Estados en Cocina**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-153](https://linear.app/callejon19/issue/CAL-153/uat-cocina-y-tiempo-real)
- **Escenario:** UAT-08 — Cocina y tiempo real.
- **Participante / Perfil:** Jefe de Cocina / Cocinero.
- **Evaluadora / Facilitadora:** Mercado Serrano Valeria.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El cocinero opera frente a la pantalla de cocina (KDS). Recibe los pedidos enviados en los escenarios UAT-06 y UAT-07 sin tocar el navegador, evalúa el semáforo de antigüedad, avanza cada producto de "En espera" a "En preparación" y finalmente a "Listo para servir".

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Recepción Reactiva sin Recarga** | Los pedidos entran automáticamente al tablero sin necesidad de presionar F5 o refrescar | Las comandas se agregaron en tiempo real con una alerta sonora sutil y animación visual. | **Aceptado** |
| **Priorización y Semáforo de Espera** | Distingue claramente qué comanda llegó primero y cuánto tiempo lleva en espera | Las tarjetas se ordenan cronológicamente (FIFO) y el indicador de tiempo muestra claramente "recién enviada", "hace 8 min", cambiando a amarillo a los 15 min. | **Aceptado** |
| **Sincronización Inmediata con Salón** | Al marcar un platillo como listo, el mesero lo ve inmediatamente | El cocinero tocó "Listo" y el mesero recibió en su pantalla el aviso para recoger la comanda caliente. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"Es muy cómodo trabajar sin tickets de papel grasientos que se pierden. El semáforo ayuda a no retrasar mesas que llevan mucho tiempo esperando."*
- **Firma / Aprobación:** Aprobado para pase a producción.
