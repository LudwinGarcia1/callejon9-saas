# Ruta a producción y entrega académica

Sincronizado con el proyecto de Linear [Callejón 9 SaaS — Ruta a producción y entrega académica](https://linear.app/callejon19/project/callejon-9-saas-ruta-a-produccion-y-entrega-academica-d72584b2b157) el 2026-09-26.

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

Alcance: CAL-16, CAL-18 a CAL-20, CAL-22, CAL-35, CAL-51, CAL-52, CAL-55 y CAL-89 a CAL-91. Incluye paginación, zona horaria, cobertura, análisis estático, salud, RDS con RLS, OpenAPI, checks de Jenkins y SonarQube.

Salida: cada pull request supera controles visibles, el quality gate de SonarQube protege el código nuevo, los datos preservan RLS y los contratos son verificables.

## Fase 4 — Entorno productivo observable

Objetivo: desplegar y operar el SaaS con trazabilidad, recuperación y señales accionables.

Alcance: CAL-21, CAL-23 a CAL-25, CAL-34, CAL-53, CAL-54, CAL-56 a CAL-58 y CAL-92. Incluye auditoría, logs, métricas, ECS/ALB/TLS, frontend same-origin, rollback, CloudWatch, restauración de RDS y pruebas de carga reproducibles con k6.

Salida: entorno remoto operable, observable y recuperable, con una línea base de rendimiento reproducible sin debilitar same-origin ni RLS.

## Fase 5 — Beta funcional y evidencia académica

Objetivo: demostrar una beta integrada con evidencia técnica y académica suficiente para evaluación.

Alcance: CAL-26 a CAL-28, CAL-30, CAL-37 y CAL-64 a CAL-81. Incluye recorridos críticos automatizados con Selenium, despliegue verificado, runbook, notificaciones y entregables intermedios de las asignaturas.

Salida: beta recorrible y comprobable, acompañada de documentos y evidencias intermedias.

## Fase 6 — Cierre académico y preparación comercial

Objetivo: completar las entregas finales y dejar listas las capacidades mínimas para operar comercialmente.

Alcance: CAL-29, CAL-31, CAL-32, CAL-59 a CAL-63 y CAL-82 a CAL-84. Incluye cobro, suscripción, verificación de correo, costos AWS, accesibilidad, privacidad, semilla multiplataforma, errores de cliente y entregables finales.

Salida: documentación final entregable, operación responsable y una base comercial verificable.

## Backlog posterior

CAL-36 y CAL-38 a CAL-44 permanecen sin fase ni ciclo hasta que exista capacidad, decisiones de producto o arquitectura suficientes. Incluyen configuración operativa, TOTP, inventario/recetas, ETL, facturación, analítica, CHANGELOG e impresión térmica.
