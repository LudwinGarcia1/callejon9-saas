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

## 6. Bloqueos vigentes del ciclo de pruebas

Las diez relaciones «Blocked by» de esta tabla están registradas en Linear: cada UAT está bloqueada por la SIT de su módulo (verificado el 2026-10-28).

Estado al **2026-10-28 12:45** (America/Mexico_City). La primera versión de esta tabla (26-oct) quedó desactualizada: el 27-oct se aprobaron CAL-134, 135 y 140, y se reabrieron por revisión CAL-136, 137 y 141 a 145.

| Módulo | UAT (bloqueada) | Estado UAT | SIT (bloqueadora) | Estado SIT | Observación |
|---|---|---|---|---|---|
| Alta de restaurantes | `CAL-147` | In Progress | `CAL-134` | Done | Desbloqueada |
| Usuarios y roles | `CAL-148` | In Progress | `CAL-135` | Done | Desbloqueada |
| Cocina y tiempo real | `CAL-153` | In Progress | `CAL-140` | Done | Desbloqueada |
| Catálogo | `CAL-149` | In Progress | `CAL-136` | In Progress | **Bloqueada.** No se ejecuta ni avanza a In review hasta aprobar la SIT |
| Mesas | `CAL-150` | In Progress | `CAL-137` | In Progress | **Bloqueada.** Igual que CAL-149 |
| Inventario | `CAL-156` | In Progress | `CAL-143` | In Progress | **Bloqueada.** Igual que CAL-149 |
| Cobro y pagos | `CAL-154` | Todo | `CAL-141` | In Progress | Bloqueada |
| Tickets | `CAL-155` | Todo | `CAL-142` | In Progress | Bloqueada |
| Ventas y analítica | `CAL-157` | Todo | `CAL-144` | In Progress | Bloqueada |
| Aislamiento y permisos | `CAL-158` | Todo | `CAL-145` | In Progress | Bloqueada |

> Este plan permite que una tarea bloqueada siga visible en *In Progress* (sección 4), pero no que avance a *In review* ni a *Done*. Además, `docs/pruebas/README.md` rechaza toda UAT ejecutada antes de aprobar la SIT de su módulo.

---

## Acuerdo del equipo

Acordado el **2026-10-28** en el grupo de WhatsApp del equipo. El Scrum Master compartió este documento y los puntos clave a las 12:41, y la encuesta «¿de acuerdo?» de las 12:43 quedó con **3 votos a favor y 0 en contra**, de 4 integrantes; un voto quedó pendiente. Puntos aprobados:

- Un bloqueo se avisa en el grupo en máximo 2 h y se marca en Linear con «Blocked by».
- Si un bloqueo sigue más de 12 h hábiles, se escala y el Scrum Master reasigna.
- Las ausencias se avisan con 12 a 24 h de anticipación, con la rama subida y un comentario en el issue.
- Una tarea se reasigna si vence en 48 h, si lleva 24 h sin avance o si bloquea a alguien.

La captura de la encuesta la conserva el Scrum Master y no se sube al repositorio, porque muestra números de teléfono.
