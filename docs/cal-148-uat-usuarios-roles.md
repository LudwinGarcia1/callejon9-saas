# CAL-148 — UAT-03: Usuarios y Roles

Formato oficial de validación y aceptación de usuario final (UAT) correspondiente al escenario **UAT-03: Gestión de Usuarios, Asignación de Roles y Control de Acceso**.

---

## 1. Datos Generales de la Prueba
- **Ticket Linear:** [CAL-148](https://linear.app/callejon19/issue/CAL-148/uat-usuarios-y-roles)
- **Escenario:** UAT-03 — Usuarios y Roles.
- **Participante / Perfil:** Administrador de Restaurante.
- **Evaluador / Facilitador:** García Gaytán Ludwin.
- **Entorno:** Producción / Staging en Azure App Service.
- **Fecha de Ejecución:** 2026-10-20.

---

## 2. Descripción del Escenario
El administrador accede al módulo de configuración de personal (`/admin/users`), registra sucesivamente tres perfiles con funciones distintas (un Mesero, un Cocinero y un Cajero), comprueba los accesos y vistas diferenciadas de cada uno, evalúa las validaciones de fortaleza de credenciales y finalmente procede a desactivar a uno de los colaboradores verificando la revocación inmediata de sus privilegios.

---

## 3. Criterios de Aceptación y Resultados

| Criterio de Aceptación | Comportamiento Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :---: |
| **Separación de Funciones (RBAC)** | Cada rol ingresa y visualiza exclusivamente los módulos autorizados por su perfil | - **Mesero:** Acceso exclusivo a salón, comandas y menú de servicio. Bloqueado a finanzas/config.<br>- **Cocinero:** Acceso exclusivo a KDS (Kitchen Display System). Sin acceso a cobro ni mesas.<br>- **Cajero:** Acceso a cuentas, cobro y facturación. | **Aceptado** |
| **Revocación Inmediata de Acceso** | Al desactivar un usuario (`active: false`), este pierde el acceso de manera instantánea | El mesero fue desactivado desde el panel de admin. Al recargar la página o enviar su siguiente petición API, el token fue rechazado con `401 Unauthorized` ("Usuario inactivo o suspendido"), redirigiendo al login. | **Aceptado** |
| **Políticas de Contraseñas Seguras** | Mensajes comprensibles en tiempo real cuando la contraseña no cumple directrices de seguridad | El indicador de fortaleza alertó con claridad los requisitos faltantes: mínimo 8 caracteres, al menos una mayúscula, un número y un caracter especial. Mensajes claros y sin tecnicismos opacos. | **Aceptado** |

---

## 4. Registro y Formato de Aceptación

- **Resultado Global:** **ACEPTADO (SÍ)**
- **Defectos encontrados:** 0 defectos bloqueantes, 0 defectos mayores.
- **Observaciones del participante:**
  > *"Es muy intuitivo dar de alta al personal y asignarle su rol. Se comprobó que el cocinero solo ve los pedidos de cocina y el cajero solo el módulo de caja. La desactivación inmediata al suspender a un empleado brinda mucha confianza para la seguridad operativa."*
- **Firma / Aprobación:** Aprobado para pase a producción.
