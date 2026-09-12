# Roadmap de profesionalización

Sincronizado con el proyecto de Linear [Callejón 9 SaaS — Plan de profesionalización](https://linear.app/callejon19/project/callejon-9-saas-plan-de-profesionalizacion-d72584b2b157) el 2026-09-11.

## Baseline revisada

- Código: `feat/identidad-por-restaurante@b036e4d`.
- El documento de entrada había sido generado contra `main@232555d`; cada issue se volvió a contrastar con controllers, services, migraciones, pruebas, frontend, CI y documentación del checkout actual.
- Los puntos siguen siendo una estimación inicial para un equipo de tres personas. Deben calibrarse durante refinamiento.
- Se mantienen seis fases de dos semanas como estructura de compromiso. Cada fase está representada por un project milestone y asignada al cycle equivalente de Linear.

## Fase 1 — Cerrar la puerta · Cycle 1 · 14–28 sep 2026 · 23 SP

| Issue | Resultado |
|---|---|
| CAL-5 | Cerrar la matriz de autorización de la API |
| CAL-6 | Rotación segura de refresh tokens |
| CAL-7 | Contrato de autorización exigible en CI |
| CAL-8 | Cabeceras y cookies en la frontera pública |
| CAL-9 | Gobernanza y protección del repositorio |
| CAL-10 | Rechazar productos inactivos en comandas |

## Fase 2 — Que corra en cualquier máquina · Cycle 2 · 28 sep–12 oct 2026 · 27 SP

| Issue | Resultado |
|---|---|
| CAL-11 | Entorno completo con Docker Compose |
| CAL-12 | Puertas de frontend en CI |
| CAL-13 | Folios únicos entre instancias |
| CAL-14 | Escaneo de dependencias y código |
| CAL-15 | Configuración productiva y secretos seguros |
| CAL-17 | Imágenes productivas mínimas/no root |

## Fase 3 — Escala y exactitud · Cycle 3 · 12–26 oct 2026 · 23 SP

| Issue | Resultado |
|---|---|
| CAL-16 | Paginación/orden de listados crecientes |
| CAL-18 | Cobertura útil con JaCoCo |
| CAL-19 | Zona horaria por restaurante |
| CAL-20 | Formato y análisis estático |
| CAL-22 | Liveness/readiness y salud de esquema |

## Fase 4 — Saber qué pasó · Cycle 4 · 26 oct–9 nov 2026 · 24 SP

| Issue | Resultado |
|---|---|
| CAL-23 | Logs estructurados y request context seguro |
| CAL-21 | Bitácora de auditoría inmutable |
| CAL-24 | Métricas y panel operable |
| CAL-25 | Cambio de contraseña por autoservicio |

## Fase 5 — Confianza de extremo a extremo · Cycle 5 · 9–23 nov 2026 · 24 SP

| Issue | Resultado |
|---|---|
| CAL-28 | Recorridos críticos con Playwright |
| CAL-27 | Entorno remoto desplegado/verificado |
| CAL-26 | Límite de intentos de login |
| CAL-30 | Runbook de operación probado |

## Fase 6 — Que pueda cobrar · Cycle 6 · 23 nov–7 dic 2026 · 26 SP

| Issue | Resultado |
|---|---|
| CAL-29 | Cobro recurrente con Mercado Pago |
| CAL-31 | Ciclo de vida de suscripción exigible |
| CAL-32 | Verificación de correo antes de activar |

## Backlog posterior · 81 SP

- CAL-33 recuperación de contraseña.
- CAL-38 TOTP.
- CAL-34 optimización medida de consultas.
- CAL-35 pruebas unitarias de frontend.
- CAL-43 CHANGELOG y cifras reproducibles.
- CAL-36 configuración operativa; la identidad visual ya está implementada en la rama revisada.
- CAL-41 portal de facturación.
- CAL-39 inventario/recetas al iniciar preparación.
- CAL-42 analítica por hora/día; Pareto, ventas diarias y mezcla de pago ya existen.
- CAL-37 notificaciones realtime/push.
- CAL-44 impresión térmica con arquitectura/hardware por decidir.
- CAL-40 ETL MongoDB reanudable.

## Dependencias críticas

- CAL-7 depende de CAL-5; CAL-25 depende de CAL-6.
- CAL-17 depende de CAL-11 y CAL-13; CAL-27 depende de CAL-15 y CAL-17.
- CAL-21 y CAL-24 dependen de CAL-23.
- CAL-28 depende de CAL-11; CAL-29 y CAL-30 dependen de CAL-27.
- CAL-31 depende de CAL-29; CAL-33 depende de CAL-32.
- CAL-34 depende de CAL-16/CAL-19; CAL-36 y CAL-42 dependen de CAL-19.
- CAL-39 depende de CAL-10; CAL-41 depende de CAL-29/CAL-31; CAL-43 depende de CAL-9.

Antes de iniciar una fase hay que revalidar los issues contra `main`, confirmar capacidad y resolver las decisiones marcadas como bloqueantes. Las fechas corresponden a los cycles habilitados en Linear al 2026-09-11.
