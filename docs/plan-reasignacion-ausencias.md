# CAL-175 — Plan de Reasignación de Tareas por Ausencia

## 1. Contexto y Justificación

Durante la retrospectiva del ciclo de pruebas de RestoHub, se identificó como problema operativo el **retraso en las tareas por ausencia**: cuando un integrante del equipo no podía conectarse o atender sus responsabilidades durante uno o más días, los issues asignados a su nombre quedaban completamente estancados en el tablero, consumiendo el margen de tiempo para las entregas académicas y técnicas sin que otro compañero pudiese retomarlos oportunamente.

Este plan establece los lineamientos, tiempos de aviso, criterios objetivos de reasignación y protocolos de traspaso técnico (*handoff*) para asegurar la continuidad del proyecto sin fricción interpersonal ni pérdida de contexto.

---

## 2. Aviso Oportuno de Ausencia

Todo integrante del equipo tiene la responsabilidad de comunicar cualquier indisponibilidad prevista o imprevista:

* **Ausencia programada (escolar, personal, laboral):**
  - **Anticipación mínima:** **12 a 24 horas antes** del inicio de la jornada o sesión de trabajo.
  - **Canal oficial:** Grupo de WhatsApp del equipo.
* **Ausencia imprevista o de fuerza mayor (enfermedad, corte de energía o internet):**
  - **Plazo:** Notificar en cuanto sea materialmente posible, preferentemente antes de la hora habitual de la reunión diaria (*daily*).
* **Contenido obligatorio del aviso:**
  ```text
  📢 AVISO DE AUSENCIA: @Nombre
  - Periodo estimado: [Fecha/hora inicio] a [Fecha/hora fin prevista]
  - Tareas asignadas activas: [CAL-XXX, CAL-YYY]
  - Estado del código: [Rama actualizada / Commit WIP subido / Sin cambios locales]
  - Asuntos urgentes o dependencias: [Indicar si bloquea a alguien]
  ```

---

## 3. Criterios de Reasignación de Tareas

No todas las tareas requieren reasignación inmediata. Se reasignará un issue si se cumple **al menos una** de las siguientes condiciones:

1. **Riesgo inminente de fecha límite:** El issue tiene una fecha límite de entrega (*due date*) en las próximas **48 horas**.
2. **Dependencia bloqueante activa:** El issue figura como bloqueador (`Blocking`) de otra tarea en curso y la persona estará ausente más de 12 horas.
3. **Inactividad prolongada:** La tarea permanece en `In Progress` por más de **24 horas** sin commits, comentarios o reportes de avance.
4. **Acuerdo expreso:** El titular ausente solicita proactivamente el traspaso debido a su carga de trabajo.

**¿Quién toma la decisión de reasignar?**
El Scrum Master / Coordinador técnico evaluará la carga actual de los integrantes y designará al nuevo responsable garantizando que no se sobrecargue a un solo miembro.

---

## 4. Protocolo de Traspaso Técnico (*Handoff*)

Para evitar pérdida de trabajo previo y asegurar que el nuevo responsable pueda retomar la tarea en minutos:

### Obligaciones de quien entrega (o del equipo si la salida fue súbita):
1. **Push obligatorio del código:** Subir al repositorio remoto la rama de trabajo con los cambios más recientes, incluso si el trabajo está incompleto, usando un commit descriptivo:
   ```bash
   git commit -m "wip: respaldo de trabajo para traspaso por ausencia (CAL-XXX)"
   git push origin <nombre-rama>
   ```
2. **Comentario de situación en Linear:** Dejar una nota estructurada en el issue:
   - **¿Qué está completado?** (clases, endpoints, componentes o pruebas que ya funcionan).
   - **¿Qué está pendiente o fallando?** (errores de compilación, tests pendientes, dudas de diseño).
   - **Rama y commit base:** Enlace o nombre exacto de la rama remota.

---

## 5. Registro y Trazabilidad en Linear

La reasignación debe mantener intacto el historial de auditoría y esfuerzo:

1. **Actualización del responsable:**
   - Modificar el campo **Assignee** en Linear asignando al nuevo titular.
2. **Comentario formal de reasignación:**
   - Publicar un comentario con la plantilla:
     > `🔄 [REASIGNACIÓN POR AUSENCIA - AAAA-MM-DD]`  
     > - **Titular anterior:** `@NombreOriginal`  
     > - **Nuevo titular:** `@NuevoNombre`  
     > - **Motivo:** Ausencia programada/imprevista hasta `AAAA-MM-DD` y mitigación de riesgo para entrega `CAL-XXX`.  
     > - **Rama de trabajo:** `nombre-de-la-rama`
3. **Preservación del historial:** No se crearán issues duplicados; el trabajo continúa sobre el issue original.

---

## 6. Procedimiento para la Reincorporación

Cuando el integrante reanuda sus actividades:

1. **No despojar tareas en curso:** Si la tarea reasignada ya fue tomada y se encuentra en desarrollo activo o en revisión, el nuevo titular concluye el issue para evitar duplicación de esfuerzos o conflictos de merge.
2. **Asignación de nuevo trabajo:**
   - El integrante reincorporado tomará prioritariamente tareas en estado `Todo` del ciclo en curso.
   - O apoyará en la revisión independiente de código (*code review*) y ejecución de pruebas SIT/UAT para acelerar el flujo hacia *Done*.

---

## 7. Revisión de issues abiertos en riesgo

### Revisión original (26 de octubre de 2026)

En aplicación inmediata de este plan, se revisó el estado de los issues con fecha de entrega comprometida en la semana actual:

| Issue | Vencimiento | Estado | Asignado Actual | Acción / Veredicto |
|---|---|---|---|---|
| **CAL-67** (PWA video Beta) | 24-oct-2026 *(Vencido)* | Todo | *Sin asignar* | **Requiere asignación inmediata** para grabar video Beta y documentar plataformas. |
| **CAL-174** (Plan de bloqueos) | 26-oct-2026 *(Hoy)* | Todo | Octavio Duarte | En proceso de atención y formalización con este documento. |
| **CAL-175** (Plan de ausencias) | 26-oct-2026 *(Hoy)* | Todo | Octavio Duarte | En proceso de atención y formalización con este documento. |
| **CAL-69** (Normas y regulaciones) | 28-oct-2026 *(Mié)* | Todo | *Sin asignar* | Asignar antes del 27-oct para evitar retraso en entrega de Integradora. |
| **CAL-70** (Software funcional CI) | 31-oct-2026 *(Sáb)* | Todo | *Sin asignar* | Vinculado a la cadena técnica Jenkins (CAL-86/87/89) de Ludwin G. |

### Actualización al 2026-10-28 12:45 (America/Mexico_City)

| Issue | Vencimiento | Estado | Asignado | Situación |
|---|---|---|---|---|
| **CAL-67** (PWA, video de la Beta) | 24-oct *(vencido)* | In Progress | García Gaytán Ludwin | Reabierto el 27-oct por revisión: URL del video inválida y configuración que no corresponde al repositorio |
| **CAL-69** (Normas y regulaciones) | 28-oct *(hoy)* | In Progress | García Gaytán Ludwin | Cambios solicitados el 27-oct en PR #95: cita la LFPDPPP abrogada |
| **CAL-70** (Software con integración continua) | 31-oct | In Progress | García Gaytán Ludwin | Cambios solicitados el 27-oct en PR #97: los logs de Jenkins no corresponden al repositorio |
| **CAL-174** y **CAL-175** (estos planes) | 26-oct | Done | Octavio Duarte | Acordados por el equipo el 28-oct (ver abajo) |

Ninguno de esos issues tiene un comentario de reasignación con la plantilla de la sección 5. Si alguno se reasignó el 26-oct, falta dejar ese registro.

---

## Acuerdo del equipo

Acordado el **2026-10-28** en el grupo de WhatsApp del equipo. El Scrum Master compartió este documento y los puntos clave a las 12:41, y la encuesta «¿de acuerdo?» de las 12:43 quedó con **3 votos a favor y 0 en contra**, de 4 integrantes; un voto quedó pendiente. Puntos aprobados:

- Un bloqueo se avisa en el grupo en máximo 2 h y se marca en Linear con «Blocked by».
- Si un bloqueo sigue más de 12 h hábiles, se escala y el Scrum Master reasigna.
- Las ausencias se avisan con 12 a 24 h de anticipación, con la rama subida y un comentario en el issue.
- Una tarea se reasigna si vence en 48 h, si lleva 24 h sin avance o si bloquea a alguien.

La captura de la encuesta la conserva el Scrum Master y no se sube al repositorio, porque muestra números de teléfono.
