<!--
Antes de abrir: lee CONTRIBUTING.md. Pon el identificador del issue en el
TÍTULO del PR (p. ej. "feat(auth): rotar el refresh token · CAL-6") para que
Linear lo vincule. Borra las secciones que de verdad no apliquen, pero no
borres la de RLS y roles: si no aplica, dilo explícitamente.
-->

## Issue

<!-- CAL-000, o el enlace al brief local si no hay issue -->

Resultado que cierra:

## Qué cambia

<!-- Dos o tres frases: qué hace el sistema ahora que antes no hacía. -->

### Fuera de alcance

<!-- Lo que alguien podría esperar de este PR y deliberadamente no está. -->

## Criterios de aceptación cubiertos

<!-- Copia los del issue y marca solo los que este PR satisface. -->

- [ ] 1.
- [ ] 2.

## RLS, roles y aislamiento

> La garantía central del proyecto es que PostgreSQL impone el aislamiento entre
> restaurantes. Esta sección no se marca sin haberla mirado.

- [ ] Este PR **no** toca políticas RLS, roles de base de datos, `TenantFilter`,
      `TenantAwareTransactionManager`, sesión ni autorización de la API.
- [ ] Sí las toca, y a continuación explico el efecto y la evidencia.

Si las toca:

- [ ] Toda tabla de datos por restaurante nueva o modificada conserva
      `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, `USING` y
      `WITH CHECK`.
- [ ] `callejon9_app` sigue sin ser propietario de tablas y sin `BYPASSRLS` ni
      privilegios de superusuario.
- [ ] El inquilino sigue viniendo de la cookie JWT firmada; ningún `tenant_id`
      del cliente se trata como autoridad.
- [ ] Ejecuté `scripts/verify-rls.sql` como `callejon9_app` y pego el resultado.

Efecto y evidencia:

## Autorización por rol

- [ ] Las rutas nuevas o modificadas declaran el rol que les corresponde en el
      backend.
- [ ] No aplica: este PR no añade ni modifica rutas.

Roles afectados:

## Migraciones

- [ ] No hay migraciones en este PR.
- [ ] Añadí `V{n}__descripcion.sql` con el número siguiente y **no reescribí**
      ninguna migración ya aplicada.
- [ ] La migración corre limpia sobre una base vacía y sobre una base ya migrada.

Migraciones añadidas:

## Pruebas ejecutadas

> Declara solo lo que corriste de verdad, con su resultado. Una puerta que no se
> ejecutó se reporta como no ejecutada, no se deja en blanco.

| Puerta | Ejecutada | Resultado |
|---|---|---|
| `cd backend; .\mvnw.cmd -B verify` | | |
| `cd frontend; pnpm lint` | | |
| `cd frontend; pnpm exec tsc --noEmit` | | |
| `cd frontend; pnpm build` | | |
| RLS manual (`scripts/verify-rls.sql`) | | |

Pruebas añadidas o actualizadas:

<!-- Nombres de las pruebas. Si el PR no añade ninguna, di por qué. -->

Puertas no ejecutadas y por qué:

## Interfaz

- [ ] Este PR no cambia la interfaz.
- [ ] Sí la cambia y adjunto capturas.

<!--
Adjunta capturas de antes y después, en claro y en oscuro. Si el cambio es de
interacción, un clip corto vale más que una captura.
-->

- [ ] Reutilicé los tokens de `globals.css`, el tema de `tenant-theme.ts` y los
      componentes de `components/ui` en lugar de crear variantes nuevas.
- [ ] Revisé el comportamiento en el ancho mínimo de tablet que usa el servicio.

## Documentación

- [ ] No hacía falta tocar documentación.
- [ ] Actualicé la documentación afectada (indico cuál abajo).

## Riesgos y deuda que deja

<!-- Qué podría romperse, qué queda sin resolver y dónde quedó registrado. -->

## Revisión

Asigno a:

<!--
Este repositorio no usa CODEOWNERS. Elige a alguien con contexto en el área y
di por qué si no es evidente. Nadie aprueba su propio PR.
-->

- [ ] Verifiqué que el diff no incluye contraseñas, tokens, secretos ni datos
      personales.
