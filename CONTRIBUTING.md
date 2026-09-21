# Cómo contribuir a Callejón 9

Este documento describe el proceso humano: cómo se nombra una rama, qué debe
traer un pull request, quién revisa y qué tiene que estar verde antes de
integrar.

Las **reglas del código** —invariantes de aislamiento, convenciones de
arquitectura y comandos de verificación— viven en [`AGENTS.md`](AGENTS.md) y no
se duplican aquí. Si una regla aparece en los dos archivos y difieren, manda
`AGENTS.md`.

## Antes del primer cambio

1. Lee [`AGENTS.md`](AGENTS.md): la tesis del proyecto es que PostgreSQL impone
   el aislamiento entre restaurantes, y esa garantía condiciona todo lo demás.
2. Deja el entorno corriendo siguiendo [`README.md`](README.md). Las pruebas del
   backend necesitan un PostgreSQL 16 real; **H2 no demuestra RLS** y por eso no
   se usa.
3. Si trabajas con asistencia de agentes, entra por
   [`workflow/README.md`](workflow/README.md).

## Ramas

`main` es la base y es la rama desplegable. No se trabaja directamente sobre
ella; los cambios entran por pull request.

| Prefijo | Para |
|---|---|
| `feat/` | Funcionalidad nueva |
| `fix/` | Corrección de un defecto |
| `docs/` | Documentación, sin cambio de comportamiento |
| `chore/` | Herramientas, harness, dependencias, configuración |

Usa ramas cortas y de un solo tema: `feat/rotacion-refresh-tokens`, no
`feat/varias-cosas`. Rebasa sobre `main` en lugar de acumular merges de vuelta.

Cuando el trabajo viene de un issue de Linear, **la rama que sugiere el propio
issue no sigue esta convención**: Linear propone `persona/cal-9-titulo-largo`.
Usa el prefijo por tipo de esta tabla y deja el identificador del issue en el
título del pull request, que es lo que Linear vincula de todas formas.

## Commits

Se usan [commits convencionales](https://www.conventionalcommits.org/es/):

```
<tipo>(<ámbito opcional>): <descripción en imperativo>
```

Los tipos en uso son `feat`, `fix`, `docs`, `chore`, `refactor`, `test` y
`perf`. El ámbito es el área tocada: `backend`, `frontend`, `rls`, `harness`,
`planning`, o el paquete concreto.

```
feat(auth): rotar el refresh token en cada renovación
fix(order): rechazar productos inactivos al agregar items
docs(security): publicar la política de reporte de vulnerabilidades
```

Reglas prácticas:

- Descripción en minúscula, sin punto final, en imperativo y **en español**.
- Un commit hace una cosa. Si el mensaje necesita una "y", probablemente son dos.
- Referencia el issue cuando exista, en el cuerpo o en el título del PR
  (`CAL-9`). El identificador en el título del PR es lo que Linear vincula.
- No se versionan contraseñas, tokens, secretos ni datos personales. Revisa el
  diff antes de confirmar.

No hay validación automática del formato del mensaje: es una convención que se
sostiene en la revisión.

## Pull requests

**Todo cambio en `main` pasa por un pull request.** No hay excepciones
permanentes; una excepción puntual necesita dueño y fecha de caducidad
registrados en el propio PR.

Al abrirlo se carga la plantilla de
[`.github/pull_request_template.md`](.github/pull_request_template.md), que pide
issue, alcance, efecto sobre RLS y roles, migraciones, pruebas ejecutadas y
capturas cuando hay interfaz. Rellénala: las casillas están ahí porque cada una
corresponde a algo que ya se rompió alguna vez.

Un buen PR en este repositorio:

- Es **vertical y pequeño**: satisface un criterio de aceptación completo, de la
  base de datos a la pantalla, en lugar de media capa.
- Declara las pruebas que **sí se ejecutaron**, con su resultado. Afirmar que una
  puerta pasó sin haberla corrido es el único error que se considera grave por sí
  mismo.
- Nombra la deuda que deja, en lugar de dejarla implícita.
- Se queda en *draft* mientras no esté listo para revisión.

## Revisión

Este repositorio **no usa `CODEOWNERS`**: el remoto está en una cuenta personal
y las cuatro personas del equipo contribuyen sobre todas las áreas, así que una
asignación automática por ruta daría un dueño ficticio. La responsabilidad se
acuerda en el equipo. Consecuencia práctica:

- Todo PR necesita **al menos una aprobación de otra persona**. Nadie aprueba su
  propio cambio.
- Quien abre el PR **asigna explícitamente** a quien tenga contexto en el área
  tocada, y lo dice en el PR si la elección no es obvia.
- Un cambio que toque la frontera de seguridad —políticas RLS, roles de base de
  datos, `TenantFilter`, `TenantAwareTransactionManager`, sesión, autorización de
  la API— requiere que quien revisa lo diga de forma expresa: la casilla de RLS
  de la plantilla no se marca sin haberlo mirado.
- Revisar es leer el diff, no el resumen. El resumen de quien contribuye —persona
  o agente— no es evidencia de que una prueba pasó.

Si esta decisión deja de sostenerse —por ejemplo, si el repositorio se mueve a
una organización— se revisa el registro en
[`workflow/decisions/2026-09-21-gobernanza-del-repositorio.md`](workflow/decisions/2026-09-21-gobernanza-del-repositorio.md).

## Puertas de calidad

Corre la comprobación más estrecha durante el desarrollo y cierra con las
puertas del área que tocaste. Los comandos canónicos están en
[`workflow/project-config.yaml`](workflow/project-config.yaml) y están escritos
para PowerShell, que es el shell del entorno de desarrollo:

| Puerta | Comando | Cuándo |
|---|---|---|
| Backend | `cd backend; .\mvnw.cmd -B verify` | Cualquier cambio en `backend/` |
| Frontend · lint | `cd frontend; pnpm lint` | Cualquier cambio en `frontend/` |
| Frontend · tipos | `cd frontend; pnpm exec tsc --noEmit` | Cualquier cambio en `frontend/` |
| Frontend · producción | `cd frontend; pnpm build` | Cualquier cambio en `frontend/` |
| RLS manual | `scripts/verify-rls.sql` como `callejon9_app` | Cambios en políticas, roles o tenancy |

Un cambio de documentación no dispara puertas. Un cambio en seguridad o tenancy
exige además evidencia de RLS, no solo que el backend compile.

En CI, [`.github/workflows/ci.yml`](.github/workflows/ci.yml) ejecuta los jobs
`backend` y `frontend` en cada pull request y en push a las cuatro familias de
rama. Son los checks que `main` exige.

## Migraciones

Las migraciones Flyway son **append-only**. Una migración ya aplicada no se
reescribe: se agrega `V{n}__descripcion.sql` con el número siguiente en
`backend/src/main/resources/db/migration/`.

Si la migración crea una tabla de datos por restaurante, tiene que traer sus
políticas: `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, `USING` y
`WITH CHECK`. Una tabla nueva sin políticas es una fuga de aislamiento, aunque
el código de la aplicación filtre correctamente.

Flyway corre como `callejon9_owner`. `callejon9_app` no debe quedar como
propietario de ninguna tabla ni obtener `BYPASSRLS`.

## Frontend

El gestor de paquetes es **pnpm** y el lockfile versionado es
`frontend/pnpm-lock.yaml`. `npm install` deja el `node_modules` a medias y hay
que borrarlo para recuperarse; no lo mezcles.

El navegador llama rutas relativas `/api/v1/*` y `frontend/next.config.ts` las
reescribe al backend. No introduzcas llamadas directas a `:8080`, CORS
innecesario ni almacenamiento del token en JavaScript.

Reutiliza los tokens de `frontend/src/app/globals.css`, el tema de
`frontend/src/lib/tenant-theme.ts` y los componentes de
`frontend/src/components/ui` antes de crear variantes nuevas.

## Idioma

El código fuente y sus identificadores van **en inglés**; la interfaz, la
documentación y los mensajes de commit, **en español**. Es la convención
habitual en Java y evita mezclar idiomas dentro de una clase.

## Seguridad

**No reportes una vulnerabilidad en un issue ni en un pull request.** Usa el
canal privado descrito en [`SECURITY.md`](SECURITY.md).

## Configuración externa de GitHub

Parte de la gobernanza no vive en archivos y por eso no se demuestra con un
diff. Esta es la configuración que `main` debe tener, y solo puede aplicarla
quien administre el repositorio:

| Ajuste | Valor |
|---|---|
| Pull request obligatorio | Sí, con 1 aprobación mínima |
| Descartar aprobaciones al empujar cambios | Sí |
| Aprobación de alguien distinto de quien hizo el último push | Sí |
| Checks requeridos | `backend`, `frontend` |
| Rama al día con `main` antes de integrar | Sí |
| Conversaciones resueltas antes de integrar | Sí |
| Push forzado | Bloqueado |
| Borrado de la rama | Bloqueado |
| Reporte privado de vulnerabilidades | Habilitado |

La configuración está versionada en
[`.github/branch-protection.json`](.github/branch-protection.json) para que sea
revisable y reproducible, en lugar de un recuerdo de quien la aplicó. Con `gh`
autenticado como administrador del repositorio:

```powershell
gh api --method PUT repos/LudwinGarcia1/callejon9-saas/rulesets/20891153 --input .github/branch-protection.json
```

El identificador `20891153` es el del ruleset **Proteccion de Ramas** que ya
existe en el repositorio. Si alguien lo borró, créalo de nuevo con el mismo
archivo:

```powershell
gh api --method POST repos/LudwinGarcia1/callejon9-saas/rulesets --input .github/branch-protection.json
```

La evidencia se captura leyendo el estado resultante:

```powershell
gh api repos/LudwinGarcia1/callejon9-saas/rulesets/20891153
```

Lo que se espera es `"enforcement": "active"`, `conditions.ref_name.include`
apuntando a `~DEFAULT_BRANCH` y las reglas `pull_request`,
`required_status_checks`, `non_fast_forward` y `deletion` presentes. El reporte
privado de vulnerabilidades se habilita en **Settings → Advanced Security →
Private vulnerability reporting**.

## Licencia

Este repositorio es de código propietario: consulta [`LICENSE`](LICENSE). Las
contribuciones se incorporan bajo esa misma licencia. Aunque el repositorio sea
visible públicamente, no se concede licencia de uso ni de redistribución.
