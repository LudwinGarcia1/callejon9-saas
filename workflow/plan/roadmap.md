# Ruta a producción y entrega académica

Sincronizado con el proyecto de Linear [Callejón 9 SaaS — Ruta a producción y entrega académica](https://linear.app/callejon19/project/callejon-9-saas-ruta-a-produccion-y-entrega-academica-d72584b2b157) el 2026-09-26 y actualizado el 2026-10-17: la nube del proyecto es Azure. Los issues escritos para AWS se cancelaron o se marcaron como duplicados de CAL-108 (CAL-49 a CAL-53 y CAL-59), y los que siguen vigentes se adaptaron a Azure (CAL-27, CAL-54, CAL-57 y CAL-58).

## Estructura de ejecución

Las fases son hitos de resultado; los ciclos de Linear son sprints semanales y no equivalen necesariamente a una fase. Cada ticket conserva su ciclo para la planeación de corto plazo y su fase para mostrar el resultado al que contribuye.

## Replanificación del 2026-11-06: sprints S5 a S9

Meta: alcance técnico cerrado el 11 de diciembre de 2026. Los entregables académicos (CAL-67, CAL-73 a CAL-78, CAL-82 y CAL-83) y las liberaciones quedan fuera de este plan: siguen sus propias fechas, pero consumen la misma capacidad del equipo.

Capacidad: 20 pts por persona por ciclo de dos semanas (10 pts por semana), 80 pts para el equipo. Todo el alcance de abajo se concentra en el Cycle 7 (6–19 nov).

Prioridad: monetización (CAL-29 y CAL-31) y calidad en CI (CAL-185, CAL-18, CAL-91, CAL-20, CAL-90 y CAL-55). Después siguen UAT y seguridad.

| Bloque | Issues | Pts |
|---|---|---|
| Monetización | CAL-188, CAL-189, CAL-190 (CAL-29); CAL-191, CAL-192 (CAL-31) | 21 |
| Calidad en CI | CAL-185, CAL-18, CAL-91, CAL-20, CAL-90, CAL-55 | 23 |
| Seguridad | CAL-182, CAL-109, CAL-107, CAL-184 | 11 |
| Operación | CAL-57, CAL-58 | 8 |
| UAT y cierre de lo que está en progreso | CAL-146 a CAL-158, CAL-117; CAL-16, CAL-19, CAL-183, CAL-170 a CAL-172, CAL-86, CAL-89, CAL-186 | 17 |

Reparto del Cycle 7 (puntos como los cuenta Linear; los issues sin estimación cuentan 1):

| | Semana 1 · vence 13 nov | Semana 2 · vence 19 nov | Total |
|---|---|---|---|
| Octavio — monetización y frontend | CAL-186, CAL-187, CAL-188 · 7 | CAL-189, CAL-190, CAL-192, CAL-184, CAL-154, CAL-157 · 15 | 22 |
| Braulio — Azure, seguridad y suscripción | CAL-170 a CAL-172, CAL-183, CAL-182, CAL-191 · 10 | CAL-57, CAL-109 · 10 | 20 |
| Ludwin — CI | CAL-67, CAL-185, CAL-18 · 9 | CAL-91, CAL-90, CAL-147 · 11 | 20 |
| Valeria — calidad y UAT | CAL-20, CAL-107, CAL-150, CAL-155 · 7 | CAL-55, CAL-58, CAL-146, CAL-148, CAL-153, CAL-158 · 10 | 17 |

Total del Cycle 7: 79 pts. CAL-16, CAL-19, CAL-86 y CAL-89 vencen el 13 nov, pero quedan fuera del ciclo porque su trabajo se hizo en ciclos anteriores y solo falta revisarlos.

Reparto del Cycle 8 (20 nov – 3 dic; coincide con las liberaciones del 1 al 5 de diciembre):

| | Semana 1 · vence 26 nov | Semana 2 · vence 3 dic | Total |
|---|---|---|---|
| Octavio — frontend y verificación | CAL-60, CAL-25, CAL-166 · 10 | CAL-61, CAL-27, CAL-177, CAL-168, CAL-117 · 12 | 22 |
| Braulio — observabilidad y datos | CAL-23, CAL-24 · 10 | CAL-62, CAL-180, CAL-34 · 10 | 20 |
| Ludwin — despliegue y operación | CAL-56, CAL-63 · 8 | CAL-92, CAL-30, CAL-43 · 11 | 19 |
| Valeria — producto y E2E | CAL-36, CAL-179, CAL-151, CAL-152 · 8 | CAL-28 · 8 | 16 |

Total del Cycle 8: 77 pts, más los entregables académicos CAL-73 a CAL-75 que ya estaban en el ciclo sin estimación. CAL-92 depende de CAL-24; CAL-27 y CAL-30 se apoyan en CAL-56 y CAL-58. El Cycle 9 (4–17 dic) queda como reserva hasta el 11 de diciembre.

Dependencias: CAL-188 → CAL-189 → CAL-190; CAL-189 y CAL-191 → CAL-192; CAL-185 → CAL-18 → CAL-91 → CAL-90. CAL-29 ya no depende de CAL-27, porque la URL HTTPS para webhooks existe desde CAL-108.

Lo que no cupo en los dos ciclos sigue en Backlog: CAL-21, CAL-32, CAL-33, CAL-37, CAL-47 y CAL-99. Si el avance no alcanza, se recorta CAL-190 a conciliación manual; monetización y calidad en CI no se recortan.

Reglas del tramo: revisión de otra persona antes de integrar, máximo dos issues en progreso por persona, el asignado en Linear es quien hace el trabajo, y revisión del sprint cada viernes contra su objetivo.

## Fase 1 — Base segura y estable

Objetivo: recuperar una base confiable para operar y desarrollar sobre `main`.

Alcance: CAL-5, CAL-7 a CAL-10, CAL-45, CAL-46 y CAL-85. Cierra autorización y rutas autenticadas, valida redirecciones, rechaza productos inactivos y deja el frontend compilable.

Salida: rutas y roles críticos protegidos por pruebas, reglas de negocio validadas en backend y `main` compilable.

## Fase 2 — Plataforma reproducible y CI

Objetivo: construir, probar y ejecutar el sistema de forma segura, repetible y sin depender de una estación de trabajo.

Alcance: CAL-6, CAL-11 a CAL-15, CAL-17, CAL-33, CAL-47 a CAL-50 y CAL-86 a CAL-88. Incluye sesiones seguras, Docker, CI, configuración, correo, imágenes, Terraform para la infraestructura de CI en Azure, Jenkins privado y Testcontainers.

Inicio y cierre destacados: CAL-48 inicia con el primer `terraform apply` revisado y termina al validar red, identidad, persistencia, estado remoto y destrucción de prueba. CAL-86 entrega Jenkins privado y recuperable; CAL-87 termina cuando el pipeline demuestra una ejecución verde y un fallo inducido.

Salida: entorno y pipeline reproducibles, credenciales mínimas y PostgreSQL real que permite demostrar RLS.

## Fase 3 — Despliegue seguro y calidad exigible

Objetivo: hacer obligatorias la calidad, la seguridad y la compatibilidad de datos antes de integrar o desplegar.

Alcance: CAL-16, CAL-18 a CAL-20, CAL-22, CAL-35, CAL-55, CAL-89 a CAL-91, CAL-108 y CAL-119. Incluye paginación, zona horaria, cobertura, análisis estático, salud, PostgreSQL gestionado en Azure con RLS y secretos en Key Vault (CAL-108), OpenAPI, checks de Jenkins, SonarQube y los huecos de pruebas unitarias por módulo (CAL-119, con sus sub-issues CAL-120 a CAL-132).

Salida: cada pull request supera controles visibles, el quality gate de SonarQube protege el código nuevo, los datos preservan RLS y los contratos son verificables.

## Fase 4 — Entorno productivo observable

Objetivo: desplegar y operar el SaaS con trazabilidad, recuperación y señales accionables.

Alcance: CAL-21, CAL-23 a CAL-25, CAL-34, CAL-54, CAL-56 a CAL-58 y CAL-92. Incluye auditoría, logs, métricas, frontend same-origin en Azure App Service (CAL-54, hecho), despliegue con rollback hacia App Service, Azure Monitor (Application Insights y Log Analytics), restauración de Azure Database for PostgreSQL y pruebas de carga reproducibles con k6. El backend en App Service con TLS lo entrega CAL-108.

Salida: entorno remoto operable, observable y recuperable, con una línea base de rendimiento reproducible sin debilitar same-origin ni RLS.

## Fase 5 — Beta funcional y evidencia académica

Objetivo: demostrar una beta integrada con evidencia técnica y académica suficiente para evaluación.

Alcance: CAL-26 a CAL-28, CAL-30, CAL-37, CAL-64 a CAL-81, CAL-117 y CAL-118. Incluye recorridos críticos automatizados con Selenium, despliegue verificado, runbook, notificaciones y entregables intermedios de las asignaturas. También abarca la integración de sistemas en Azure (SIT, CAL-118 con CAL-133 a CAL-145) y la aceptación con usuarios por rol (UAT, CAL-117 con CAL-146 a CAL-158), planeadas en el ciclo 6.

Salida: beta recorrible y comprobable, acompañada de documentos y evidencias intermedias.

## Fase 6 — Cierre académico y preparación comercial

Objetivo: completar las entregas finales y dejar listas las capacidades mínimas para operar comercialmente.

Alcance: CAL-29, CAL-31, CAL-32, CAL-60 a CAL-63 y CAL-82 a CAL-84. Incluye cobro, suscripción, verificación de correo, accesibilidad, privacidad, semilla multiplataforma, errores de cliente y entregables finales. El control de costo de la nube ya está en Azure: presupuesto con alertas en Terraform y apagado del backend con `scripts/azure-backend-power.ps1`.

Salida: documentación final entregable, operación responsable y una base comercial verificable.

## Backlog posterior

CAL-36 y CAL-38 a CAL-44 permanecen sin fase ni ciclo hasta que exista capacidad, decisiones de producto o arquitectura suficientes. Incluyen configuración operativa, TOTP, inventario/recetas, ETL, facturación, analítica, CHANGELOG e impresión térmica.

La replanificación del 2026-11-06 movió además a Backlog, sin ciclo, lo que no cabe antes del 11 de diciembre: CAL-21, CAL-32, CAL-33, CAL-37, CAL-47 y CAL-99. El resto de lo recortado ese día volvió al Cycle 8. Conservan su fase para cuando se retomen.
