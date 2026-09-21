# Política de seguridad

Callejón 9 es un SaaS multi-restaurante. Su garantía central es que PostgreSQL
impone el aislamiento entre restaurantes mediante Row Level Security, no el
código de la aplicación. Cualquier hallazgo que permita a un restaurante ver o
escribir datos de otro es, por definición, el reporte de mayor prioridad para
este proyecto.

## Cómo reportar

**Usa el reporte privado de vulnerabilidades de GitHub:**

> [Abrir un reporte privado](https://github.com/LudwinGarcia1/callejon9-saas/security/advisories/new)
> · Repositorio → pestaña **Security** → **Report a vulnerability**

Ese canal es privado entre quien reporta y el equipo: no crea un issue público
ni notifica al resto de personas con acceso de lectura.

**No abras un issue público ni un pull request** para describir una
vulnerabilidad, ni la publiques en un canal compartido, hasta que exista una
corrección publicada y el equipo confirme que puede divulgarse.

## Qué incluir en el reporte

Un reporte accionable ahorra días de ida y vuelta. En la medida de lo posible:

1. **Tipo de problema** y componente afectado (backend, frontend, migración,
   base de datos, CI).
2. **Pasos para reproducir**, con la petición HTTP o la consulta SQL exacta.
3. **Rol y restaurante** usados en la reproducción: el rol autenticado y si el
   inquilino era el propio o uno ajeno cambia por completo la severidad.
4. **Impacto observado**, distinguiendo lo que comprobaste de lo que supones.
5. **Versión**: commit o rama sobre la que reprodujiste.

## Qué está en alcance

Tiene prioridad todo lo que toque la frontera de seguridad del producto:

| Área | Ejemplos de interés |
|---|---|
| Aislamiento por restaurante | Leer, escribir o borrar filas de otro inquilino; evadir `app.tenant_id`; políticas RLS incompletas |
| Roles de base de datos | Cualquier ruta en que `callejon9_app` obtenga propiedad de tablas, `BYPASSRLS` o privilegios de superusuario |
| Sesión y autenticación | Falsificación o reutilización del JWT en cookie, fijación de sesión, escalada de privilegios entre roles |
| Autorización de la API | Rutas `/api/v1/*` que no exijan el rol que les corresponde |
| Tiempo real | Suscribirse al canal STOMP de otro inquilino |
| Exposición de datos | Secretos, contraseñas o datos personales filtrados en respuestas, logs, artefactos de CI o el repositorio |

## Qué no está en alcance

- Resultados de escáneres automáticos sin impacto demostrado.
- Ausencia de cabeceras de endurecimiento sin un ataque concreto asociado
  (existe trabajo planificado al respecto).
- Denegación de servicio por volumen de tráfico y ataques de fuerza bruta.
- Vulnerabilidades en dependencias sin ruta de explotación en este código.
- Ingeniería social al equipo, y cualquier prueba contra infraestructura o
  datos que no sean tuyos.

## Tiempos de respuesta

El proyecto lo mantiene un equipo pequeño y estos plazos son los que puede
sostener de verdad. Se cuentan desde la recepción del reporte:

| Etapa | Plazo |
|---|---|
| Acuse de recibo | 3 días hábiles |
| Evaluación inicial, severidad y decisión de corregir | 10 días hábiles |
| Corrección de severidad crítica (aislamiento roto, acceso no autenticado a datos) | 7 días naturales |
| Corrección de severidad alta | 30 días naturales |
| Corrección de severidad media o baja | Se agenda en el ciclo de trabajo y se informa la fecha |

Si un plazo se va a incumplir, el equipo lo comunica en el mismo hilo del
reporte con la razón y la nueva fecha estimada, en lugar de dejar el hilo en
silencio.

## Divulgación

La divulgación es coordinada. Cuando exista corrección, el equipo publica un
advisory de seguridad en el repositorio y acredita a quien reportó, salvo que
prefiera permanecer anónimo. Pedimos no divulgar antes de esa publicación.

## Investigación de buena fe

Si investigas de buena fe, respetando esta política, limitándote a datos y
entornos propios y sin degradar el servicio ni acceder a datos de terceros, el
equipo no emprenderá acciones contra ti y trabajará contigo para entender y
corregir el problema. Esta política no otorga permiso para probar contra
infraestructura de clientes ni contra cuentas ajenas.

## Prácticas que el repositorio ya exige

Estas reglas son invariantes del proyecto y están descritas en
[`AGENTS.md`](AGENTS.md); un pull request que las debilite se rechaza:

- Las 14 tablas por restaurante conservan `ENABLE ROW LEVEL SECURITY`,
  `FORCE ROW LEVEL SECURITY`, `USING` y `WITH CHECK`.
- El inquilino proviene de la cookie JWT firmada y se publica con
  `set_config('app.tenant_id', ..., true)` dentro de la transacción. El
  `tenant_id` que envía el cliente nunca es autoridad.
- Las pruebas de aislamiento corren contra PostgreSQL real; H2 no demuestra RLS.
- No se versionan contraseñas, tokens, secretos ni datos personales.
