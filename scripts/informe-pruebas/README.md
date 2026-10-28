# Informe de pruebas y anexos de evidencia

Genera los dos entregables de pruebas a partir de la evidencia versionada en `docs/pruebas/`:

| Archivo | Para qué sirve | Contenido |
|---|---|---|
| `Informe de pruebas.docx` | Se lee de corrido | Estrategia, entornos, estado del plan por fase, resultados por prueba (casos, defectos, conclusión y dos figuras representativas), defectos consolidados, métricas y aprobación |
| `Anexos de evidencia.docx` | Se consulta por código | La evidencia completa de cada prueba, sin resumir: todas las tablas, pasos, capturas y respuestas HTTP literales |

Cada prueba ejecutada recibe un código de anexo, la fase más el número del issue (por ejemplo `SIT-138`). El informe lo cita junto con un enlace y un código QR a la carpeta de la evidencia en el repositorio.

## Uso

```powershell
cd scripts/informe-pruebas
pnpm install
node generar.mjs --salida <carpeta> [--estado <estado.json>] [--version 1.0]
```

| Opción | Valor por defecto | Descripción |
|---|---|---|
| `--salida` | `./salida` | Carpeta donde se escriben los dos `.docx` |
| `--docs` | `../../docs/pruebas` | Carpeta con la evidencia (`ut/`, `sit/`, `uat/`) |
| `--estado` | — | JSON con el estado de cada issue del plan, exportado de Linear. Sin él, el informe solo lista las pruebas con evidencia |
| `--version` | `1.0` | Versión que aparece en la portada y en el control del documento |
| `--repo` | `https://github.com/LudwinGarcia1/callejon9-saas` | Base de los enlaces y códigos QR |

El índice y la numeración de páginas son campos de Word: al abrir cada archivo, Word pide actualizarlos. Acepta, o usa *Referencias → Actualizar tabla*, antes de exportar a PDF.

### Formato de `--estado`

El estado cambia todos los días, así que no se versiona; se exporta de Linear al generar.

```json
{
  "fuente": "Linear, team CAL",
  "fecha": "2026-10-21 12:58",
  "issues": [
    { "id": "CAL-138", "title": "SIT · Pedidos del mesero", "status": "In review", "assignee": "Nombre" }
  ]
}
```

El título debe empezar con la fase (`UT · `, `SIT · ` o `UAT · `) para agruparse en el estado del plan.

## Qué lee de cada evidencia

El generador no interpreta redacción libre: depende de la estructura de `docs/pruebas/plantilla-evidencia.md`.

- **Título** (`# Evidencia de prueba · CAL-XXX — <Tipo> · <Módulo>`) para el nombre del anexo.
- **Sección 1** (tabla de identificación) para commit, entorno y dispositivo.
- **Casos** (`### C01 — …` dentro de la sección 6) con su tabla de campo y valor: criterio, ejecución, estado y defecto.
- **Sección 7** (tabla de defectos) y **sección 8** (resumen y «Conclusión del ejecutor»).
- **Capturas** enlazadas como `capturas/*.png` y **respuestas** en `respuestas/*.txt|json`.

Una evidencia que se aparta de la plantilla sigue entrando completa en los anexos, pero puede quedar incompleta en el informe.
