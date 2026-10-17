# Ruta a producción y entrega académica

Sincronizado con el proyecto de Linear [Callejón 9 SaaS — Ruta a producción y entrega académica](https://linear.app/callejon19/project/callejon-9-saas-ruta-a-produccion-y-entrega-academica-d72584b2b157) el 2026-09-26 y actualizado el 2026-10-17: la nube del proyecto es Azure. Los issues escritos para AWS se cancelaron o se marcaron como duplicados de CAL-108 (CAL-49 a CAL-53 y CAL-59), y los que siguen vigentes se adaptaron a Azure (CAL-27, CAL-54, CAL-57 y CAL-58).

## Estructura de ejecución

Las fases son hitos de resultado; los ciclos de Linear son sprints semanales y no equivalen necesariamente a una fase. Cada ticket conserva su ciclo para la planeación de corto plazo y su fase para mostrar el resultado al que contribuye.

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
