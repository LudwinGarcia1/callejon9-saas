# CAL-150 — UAT-05: Distribución de Mesas y Control de Salón

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-05: Registro de Mesas, Capacidad de Comensales y Ocupación Operativa**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-150](https://linear.app/callejon19/issue/CAL-150/uat-mesas)
- **Escenario:** UAT-05 — Mesas.
- **Participantes / Perfiles:** Administrador y Mesero de Salón.
- **Evaluadora / Facilitadora:** Mercado Serrano Valeria.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El administrador configura tres mesas asignando número y capacidad máxima de comensales (ej. Mesa 1 para 2 personas, Mesa 2 para 4 personas y Mesa 3 para 6 personas). Acto seguido, el mesero visualiza el mapa de mesas en su terminal y abre una orden en la Mesa 2 para recibir a un grupo de clientes.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Identificación Clara de Capacidad** | Las mesas aparecen en el salón con su número identificador y capacidad visible | El mesero identificó de inmediato las mesas disponibles y sus capacidades (2, 4 y 6 personas) en tarjetas visuales de alto contraste. | **Aceptado** |
| **Comprensión Visual del Estado** | El estado de cada mesa (libre u ocupada) se distingue de un vistazo | Al abrir la Mesa 2, la tarjeta cambió de verde ("Libre") a rojo oscuro ("Ocupada"), mostrando el folio de la comanda en curso y el tiempo de estancia. | **Aceptado** |
| **Prevención de Errores** | No permite reasignar comensales sobre una mesa ocupada | El mesero intentó tocar por error la mesa ocupada y la interfaz le mostró el detalle de la comanda activa en lugar de permitirle abrir otra. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones de los participantes:**
  > *"Excelente código de color y tamaño de letra. El mesero sabe exactamente qué mesa asignar según el tamaño del grupo de comensales."*
- **Firma / Aprobación:** Aprobado para pase a producción.
