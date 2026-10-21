# CAL-149 — UAT-04: Gestión de Catálogo de Productos y Precios

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-04: Configuración de Menú, Precios y Desactivación de Platillos**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-149](https://linear.app/callejon19/issue/CAL-149/uat-catalogo)
- **Escenario:** UAT-04 — Catálogo.
- **Participante / Perfil:** Administrador de Restaurante.
- **Evaluadora / Facilitadora:** Mercado Serrano Valeria.
- **Entorno:** Producción / Staging en Azure App Service (`https://restohub.azurewebsites.net`).
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El administrador accede al módulo de Catálogo (`/admin/catalog`), crea una nueva categoría de alimentos, da de alta tres platillos con sus precios de venta al público y desactiva uno de ellos por agotamiento temporal de insumo, comprobando la actualización inmediata en el menú de servicio de los meseros.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Visibilidad de Platillos y Precios** | Los productos creados aparecen en el menú operativo del mesero con su nombre, categoría y precio correcto | Los productos "Corte New York", "Papas Trufadas" y "Cerveza Porter" se reflejaron al instante en la terminal web y móvil del mesero con precios exactos ($380.00, $120.00 y $75.00). | **Aceptado** |
| **Retiro Inmediato de Ítems Inactivos** | El producto desactivado (`active: false`) deja de aparecer y no se puede añadir a una comanda | Al desactivar "Papas Trufadas" desde el panel de admin, desapareció del selector del mesero y cualquier petición residual directa devolvió rechazo 400. | **Aceptado** |
| **Corrección Autónoma de Precios** | El administrador puede editar y corregir el precio de un ítem de forma autónoma | El administrador modificó el precio de la cerveza a $80.00 en menos de 20 segundos sin necesidad de consultar el manual. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"El formulario de productos es sumamente intuitivo y rápido. La desactivación inmediata de platillos agotados evita confusiones graves entre la cocina y los meseros en el salón."*
- **Firma / Aprobación:** Aprobado para pase a producción.
