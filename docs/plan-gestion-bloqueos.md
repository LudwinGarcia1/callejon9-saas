# CAL-174 — Plan de Gestión de Bloqueos del Equipo

## 1. Contexto y Justificación

Durante la retrospectiva del ciclo de pruebas de RestoHub, en el análisis de «Qué no salió como se esperaba», el equipo identificó la **administración de dependencias no explícitas** como causa recurrente de fricción: varias tareas de pruebas de aceptación de usuario (UAT) se iniciaron o planificaron sin que la prueba de integración de sistemas (SIT) correspondiente estuviese concluida y aprobada en el entorno de Azure.

Este plan formaliza el procedimiento operativo para detectar, comunicar, registrar y destrabar cualquier bloqueo que impida el avance fluido del trabajo en el equipo.

---

## 2. Definición Operativa de Bloqueo

Se considera **bloqueo** cualquier evento o condición que detenga total o parcialmente el progreso de una tarea asignada en el ciclo actual:

1. **Bloqueo por dependencia entre tareas (Técnico / Flujo):**
   - Una tarea no puede comenzar o completarse porque requiere que otro issue esté cerrado o aprobado (ej. una prueba UAT depende de la aprobación previa de la SIT del mismo módulo; un despliegue depende de una migración de base de datos).
2. **Bloqueo por entorno e infraestructura:**
   - Recursos de Azure o bases de datos no disponibles, caídas de conectividad, variables de entorno no configuradas o secretos faltantes en Azure Key Vault.
3. **Bloqueo por personas o revisiones:**
   - Espera de revisión de código (PR pendiente de revisión independiente según la política del proyecto), falta de permisos en servicios o ausencia no cubierta.
4. **Bloqueo por decisiones de diseño o producto:**
   - Reglas de negocio ambiguas, discrepancias en criterios de aceptación o decisiones arquitectónicas pendientes que requieren consulta con stakeholders o docentes.

---

## 3. Protocolo de Notificación y Canales

Para garantizar transparencia y velocidad de respuesta:

* **Canal oficial:** Grupo de mensajería del equipo de desarrollo (WhatsApp).
* **Tiempo máximo de notificación:** Máximo **2 horas** tras detectar la imposibilidad de avanzar. Nunca debe dejarse un bloqueo sin reportar hasta la siguiente reunión diaria.
* **Formato estandarizado del mensaje:**
  ```text
  ⚠️ BLOQUEO: [Identificador de Issue, ej. CAL-150]
  - Tarea: [Nombre corto de la tarea]
  - Responsable: @Nombre
  - Motivo del bloqueo: [Dependencia de CAL-XXX / Entorno caído / Revisión de PR #YY]
  - Qué se requiere para destrabar: [Acción concreta requerida]
  - Nivel de impacto: [Crítico / Alto / Medio]
  ```
* **Atención:** El Scrum Master / Coordinador técnico acusará recibo y coordinará la acción inmediata con la persona responsable de liberar la dependencia.

---

## 4. Registro y Trazabilidad en Linear

Todo bloqueo debe quedar reflejado en la herramienta de gestión (Linear) para mantener la visibilidad del tablero:

1. **Relación de dependencia (`Blocked by`):**
   - En el issue bloqueado, registrar la relación formal **«Blocked by»** enlazando al issue bloqueador.
   - Si un issue bloquea a múltiples tareas, debe asegurarse que las dependencias descendientes queden vinculadas.
2. **Comentario explícito de causa:**
   - Dejar un comentario en el issue bloqueado con la etiqueta `[BLOQUEO]`:
     > `[BLOQUEO - AAAA-MM-DD]: Tarea detenida en espera de <causa/issue>. Notificado en canal del equipo.`
3. **Estado en el tablero:**
   - La tarea permanece visible con la advertencia de bloqueo. No se mueve a *In Review* ni a *Done* mientras exista una dependencia insatisfecha.

---

## 5. Seguimiento, Desbloqueo y Escalamiento

1. **Revisión prioritaria en el Daily:**
   - Al inicio de cada sincronización diaria, el primer punto de la orden del día es la revisión del listado de bloqueos abiertos.
2. **Plazo de resolución y escalamiento:**
   - Si un bloqueo persiste más de **12 horas hábiles** (o pone en riesgo un vencimiento académico en las siguientes 48 horas), se activa el escalamiento:
     - El Scrum Master reasigna prioridades o convoca una sesión síncrona inmediata de 15 minutos (pair programming o triaje).
     - Si el bloqueo se debe a una persona ausente, se activa de inmediato el **Plan de Reasignación de Tareas por Ausencia (CAL-175)**.
3. **Cierre del bloqueo:**
   - Una vez resuelta la causa, el responsable del issue bloqueador notifica en el canal y comenta en el issue:
     > `[DESBLOQUEADO - AAAA-MM-DD]: Dependencia liberada mediante <commit/PR/acción>. Se reanuda el trabajo.`

---

## 6. Mapeo de Bloqueos Vigentes del Ciclo de Pruebas

En cumplimiento con los criterios de aceptación de CAL-174, se establecen y formalizan las siguientes dependencias bloqueantes entre las pruebas de integración (SIT) y las pruebas de aceptación (UAT) en Linear:

| Módulo | Issue UAT (Bloqueado) | Dependencia Crítica | Issue SIT (Bloqueador) | Estado SIT Actual |
|---|---|---|---|---|
| **Alta de Restaurantes** | `CAL-147` UAT Alta restaurantes y planes | **Blocked by** | `CAL-134` SIT Alta restaurantes y planes | In review (PR #88) |
| **Usuarios y Roles** | `CAL-148` UAT Usuarios y roles | **Blocked by** | `CAL-135` SIT Usuarios y roles | In review (PR #88) |
| **Cocina Tiempo Real** | `CAL-153` UAT Cocina y tiempo real | **Blocked by** | `CAL-140` SIT Cocina y tiempo real | In review (PR #88) |
| **Catálogo** | `CAL-149` UAT Catálogo | **Blocked by** | `CAL-136` SIT Catálogo | In Progress |
| **Mesas** | `CAL-150` UAT Mesas | **Blocked by** | `CAL-137` SIT Mesas | In Progress |
| **Cobro y Pagos** | `CAL-154` UAT Cobro y pagos | **Blocked by** | `CAL-141` SIT Cobro y pagos | In Progress |
| **Tickets** | `CAL-155` UAT Tickets | **Blocked by** | `CAL-142` SIT Tickets | In Progress |
| **Ventas y Analítica** | `CAL-157` UAT Ventas, historial y analítica | **Blocked by** | `CAL-144` SIT Ventas, historial y analítica | In Progress |
| **Inventario** | `CAL-156` UAT Inventario | **Blocked by** | `CAL-143` SIT Inventario | In Progress |
| **Seguridad y Permisos** | `CAL-158` UAT Aislamiento y permisos | **Blocked by** | `CAL-145` SIT Aislamiento y seguridad transversal | In Progress |
