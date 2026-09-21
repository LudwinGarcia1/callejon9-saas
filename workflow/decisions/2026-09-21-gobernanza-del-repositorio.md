# Gobernanza del repositorio: licencia, ownership y canal de seguridad

- **Fecha:** 2026-09-21
- **Issue:** [CAL-9](https://linear.app/callejon19/issue/CAL-9/establecer-gobernanza-y-proteccion-del-repositorio)
- **Estado:** aceptada
- **Decide:** Octavio Duarte, como responsable del issue
- **Afecta:** `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`, `.github/`

## Contexto y fuerzas

CAL-9 pedía políticas de contribución, ownership y seguridad, pero tres piezas
no se podían derivar del repositorio y estaban registradas como bloqueo del
Sprint 1 en `workflow/plan/roadmap.md`: la licencia, los responsables de
revisión y el canal de reporte de vulnerabilidades. El brief del issue puso
explícitamente fuera de alcance "inventar titulares legales o correos
privados", así que los tres requerían decisión humana.

Estado verificado el 2026-09-21: el repositorio es **público** y pertenece a la
cuenta personal `LudwinGarcia1`. El historial tiene cuatro personas
contribuyendo con nombre y correo, y un solo handle de GitHub deducible con
certeza. Existía un ruleset llamado *Proteccion de Ramas* con
`enforcement: disabled`, sin refs objetivo y con solo dos reglas, así que `main`
estaba de hecho sin proteger.

## Decisión 1 — Licencia propietaria

`LICENSE` declara copyright 2026 de **Callejón 19**, todos los derechos
reservados, sin concesión de uso, copia ni redistribución.

**Por qué:** es un SaaS comercial. Que el repositorio sea públicamente visible
no implica ceder derechos: lo que se obtiene es código a la vista, no una
licencia de uso, y el archivo lo dice de forma expresa para que no quede
ambiguo. Una licencia permisiva (MIT, Apache-2.0) habría permitido a un tercero
operar el mismo servicio.

**Consecuencia:** las contribuciones se incorporan bajo esa licencia, y eso
queda dicho en `CONTRIBUTING.md`.

## Decisión 2 — Sin `CODEOWNERS` (excepción al criterio 2 del issue)

No se crea `.github/CODEOWNERS`. La responsabilidad de revisión se acuerda en el
equipo y `CONTRIBUTING.md` la fija como: al menos una aprobación de otra
persona, asignación explícita a quien tenga contexto en el área, y declaración
expresa de quien revisa cuando el cambio toca la frontera de seguridad.

**Por qué:** el remoto está en una cuenta personal, no en una organización con
teams, y las cuatro personas del equipo contribuyen sobre todas las áreas. Un
`CODEOWNERS` por ruta habría nombrado dueños ficticios —solo un handle es
verificable— y habría producido asignaciones automáticas que nadie sostiene.
Una regla que se ignora en la práctica es peor que su ausencia documentada.

**El criterio 2 de CAL-9 queda deliberadamente sin cumplir.** La garantía que
sustituye es `require_last_push_approval` en el ruleset, que impide integrar sin
la aprobación de alguien distinto de quien empujó el último commit. Eso cubre el
riesgo real —que alguien integre su propio cambio sin revisión— sin inventar
ownership.

**Dueño de la excepción:** Octavio Duarte.

**Caducidad:** se revisa al cerrar el Sprint 1 (2026-09-28), y obligatoriamente
si el repositorio se mueve a una organización de GitHub o si el equipo crece más
allá de las cuatro personas actuales. Cualquiera de esos dos hechos vuelve
razonable el `CODEOWNERS` por área.

## Decisión 3 — Private Vulnerability Reporting como único canal

`SECURITY.md` dirige los reportes al reporte privado de vulnerabilidades de
GitHub. No se publica ninguna dirección de correo.

**Por qué:** es un canal privado nativo del repositorio, gratuito en
repositorios públicos, y no expone datos personales de nadie, lo que además es
coherente con la regla de `AGENTS.md` de no versionar datos personales. La
alternativa evaluada —PVR más un correo de respaldo— se descartó porque no
existe hoy una cuenta de rol tipo `seguridad@`; publicar un correo personal
habría sido peor que no publicar ninguno.

**Consecuencia:** el canal depende de una configuración externa. Mientras
*Private vulnerability reporting* no esté habilitado en **Settings → Advanced
Security**, el enlace de `SECURITY.md` no funciona. Queda como paso pendiente
con evidencia, documentado en `CONTRIBUTING.md`.

**Caducidad:** cuando exista una cuenta de rol para seguridad, se añade como
respaldo en una línea de `SECURITY.md`.

## Señales para revisar estas decisiones

- El repositorio se mueve a una organización de GitHub → revisar la decisión 2.
- El equipo pasa de cuatro personas o se especializa por área → revisar la 2.
- Aparece una cuenta de rol para seguridad → revisar la 3.
- Se decide abrir el código o aceptar contribuciones externas → revisar la 1.
- Un reporte de vulnerabilidad llega por un canal público → la 3 falló y hay que
  entender por qué antes de cambiar el documento.
