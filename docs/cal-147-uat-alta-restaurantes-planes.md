# CAL-147 — UAT-02: Alta de Restaurantes y Planes

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-02: Onboarding de Restaurante y Selección de Plan SaaS**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-147](https://linear.app/callejon19/issue/CAL-147/uat-alta-de-restaurantes-y-planes)
- **Escenario:** UAT-02 — Alta de Restaurante y Planes.
- **Participante / Perfil:** Dueño / Encargado de restaurante ("Restaurante El Portal Gastronómico").
- **Evaluador / Facilitador:** García Gaytán Ludwin.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El usuario ingresa al portal de autoservicio desde la página principal de registro, completa el formulario con la información comercial de su restaurante, selecciona un plan de suscripción acorde a sus necesidades (ej. Plan Crecimiento / Estándar) y realiza el primer inicio de sesión como Administrador.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Tiempo / Métrica Objetivo | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Autonomía y Rapidez** | Terminar el alta sin asistencia técnica en < 5 min | Completado en 2 min 40 seg mediante asistente guiado (Stepper) | **Aceptado** |
| **Claridad de Planes** | Entiende transparentemente qué incluye el plan seleccionado (límites de usuarios concurrentes, mesas activas, reportes) | La tarjeta comparativa detalla claramente: "Hasta 15 mesas, 8 usuarios concurrentes, inventario avanzado". Sin cargos ocultos ni confusión de términos. | **Aceptado** |
| **Estado Inicial Limpio** | Al finalizar el alta, aterriza en el panel de su restaurante, en estado vacío (Empty State) listo para configurar | Redirección exitosa a `/admin/dashboard`. Pantalla de bienvenida con checklist de primeros pasos (Crear primera categoría, dar de alta mesas). Sin datos residuales de otros inquilinos. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"El flujo de registro fue muy claro y rápido. La explicación de cuántas mesas permite cada plan evitó dudas durante la selección. Al entrar al panel, el asistente de inicio rápido facilita saber qué configurar primero."*
- **Firma / Aprobación:** Aprobado para pase a producción.
