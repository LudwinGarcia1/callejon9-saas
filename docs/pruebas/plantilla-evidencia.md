# Evidencia de prueba · CAL-XXX — <Tipo> · <Módulo>

> Copia este archivo a `docs/pruebas/<tipo>/CAL-XXX-<modulo>/README.md` y llénalo **durante** la ejecución, no al final.
> Borra estas notas en cita y las secciones marcadas con otro tipo de prueba.
> Las reglas de captura, nombres y revisión están en [`docs/pruebas/README.md`](README.md).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-XXX](https://linear.app/callejon19/issue/CAL-XXX) — <título exacto del issue> |
| Historia o issue padre | [CAL-YYY](https://linear.app/callejon19/issue/CAL-YYY) — <título> |
| Tipo de prueba | UT · SIT · UAT *(deja solo uno)* |
| Módulo | <p. ej. Cobro y pagos> |
| Entorno | Local · CI · Azure *(deja solo uno)* |
| URL del frontend | <p. ej. `https://restohub.azurewebsites.net`> |
| URL de la API | <host real del App Service, tal como responde> |
| Commit probado | `<SHA de 7+ caracteres>` de `main` *(el que está desplegado o el que corrió CI)* |
| Ejecución de CI | <enlace al build o al check del PR, si aplica> |
| Navegador / dispositivo | <p. ej. Chrome 131 en Windows 11; Expo Go 52 en Android 14> |
| Restaurante de prueba | `pruebas` *(nunca uno con datos reales)* |
| Versión de esta evidencia | v1 · <fecha> *(sube la versión si se re-ejecuta)* |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | <nombre> | Asignado en Linear |
| Ejecutor de la prueba | <nombre> | Quien realizó los pasos y tomó las capturas |
| Revisor | <nombre> | **Distinto del ejecutor.** Llena la sección 10 |
| Participante (solo UAT) | P1 — <perfil, p. ej. «encargado de restaurante, externo al equipo»> | Sin nombre ni datos personales en el repositorio; la hoja firmada se guarda fuera |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-YYY) | | | | | |
| Esta tarea (CAL-XXX) | | | | | |
| Preparación del entorno y datos | | | | | |
| Ejecución de los casos | | | | | |
| Re-prueba de defectos *(si hubo)* | | | | | |

> El inicio y fin reales del issue deben coincidir con los cambios de estado en Linear (In Progress → In review). Si hay desviación, explica la causa en la sección 8.

## 4. Alcance y criterios cubiertos

**Objetivo.** <Una o dos frases: qué se quiere demostrar y por qué importa.>

**Fuera de alcance.** <Lo que esta prueba no cubre, para que nadie lo dé por probado.>

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. <copia literal del criterio de aceptación> | C01 |
| 2. <…> | C02, C03 |

> Todo criterio del issue aparece en esta tabla. Si alguno no se pudo probar, se marca «No ejecutado» con la razón; no se omite.

## 5. Preparación

**Precondiciones del entorno**

- [ ] Backend encendido y `/actuator/health` en `UP` *(adjunta la respuesta en `respuestas/`)*
- [ ] Frontend accesible en la URL indicada
- [ ] Commit desplegado verificado: `<SHA>`
- [ ] <otras: plan B1 activo para la sesión, app móvil apuntando a la API, etc.>

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Cuenta por rol | `mesero@pruebas.com`, `cocina@pruebas.com`, … | Semilla del restaurante `pruebas` |
| <producto, mesa, insumo…> | <nombre e ID> | <paso o script que lo creó> |

> Nunca escribas contraseñas, tokens ni el valor de cookies. Si una captura los muestra, tápalos antes de subirla.

## 6. Casos de prueba

Repite este bloque por cada caso. Los pasos deben permitir que otra persona reproduzca la prueba sin preguntarte nada.

### C01 — <título corto en forma de resultado esperado>

| Campo | Valor |
|---|---|
| Criterio cubierto | <número del criterio> |
| Prioridad | Alta · Media · Baja |
| Rol que ejecuta | <ADMIN · WAITER · KITCHEN · CASHIER> |
| Inicio / fin de ejecución | `AAAA-MM-DD HH:MM` / `AAAA-MM-DD HH:MM` |
| Estado | ✅ Aprobado · ❌ Fallido · ⛔ Bloqueado · ⏭️ No ejecutado |
| Defecto vinculado | <CAL-ZZZ o «—»> |

**Precondición.** <Estado exacto antes del paso 1: sesión iniciada como X, orden en estado Y, mesa Z ocupada…>

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | <verbo + elemento de la interfaz o petición exacta> | <valor> | <qué debe pasar> | <qué pasó, literal> | `capturas/C01-P01-<desc>.png` |
| 2 | | | | | |

**Respuesta HTTP literal** *(SIT; cuando el caso involucra la API)*

Pega la salida tal como la devolvió el servidor, sin editar ni «limpiar». Guarda también el archivo en `respuestas/C01-<desc>.txt`.

```text
$ curl -i <URL completa> ...
HTTP/1.1 ...
<cabeceras y cuerpo reales; valores de cookie reemplazados por <redactado>>
```

**Observaciones.** <Comportamiento inesperado aunque el caso pase, tiempos de respuesta, dudas.>

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| CAL-ZZZ | C02 | Alta | <qué falla> | Abierto · Corregido · Verificado | <fecha o «—»> |

> Cada defecto se registra como issue en Linear con severidad, pasos, resultado esperado, obtenido y su captura. La escala de severidad está en la guía.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| | | | | |

**Desviaciones y limitaciones.** <Qué no salió como se planeó, qué se probó distinto y por qué. Si no hay, escribe «Ninguna».>

**Conclusión del ejecutor.** <¿Los criterios del issue se cumplen? ¿Qué falta?>

## 9. Anexos por tipo de prueba

*Conserva solo el anexo del tipo de esta evidencia.*

### UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/src/test/java/...`, `frontend/src/test/...` |
| Comando local | p. ej. `cd backend; .\mvnw.cmd -B verify` |
| Resultado local | <«Tests run: N, Failures: 0» copiado de la salida> |
| Resultado en CI | <enlace al check en verde> |
| Prueba de que fallan sin el comportamiento | <qué línea se rompió a propósito, qué prueba falló y su mensaje> |
| Cobertura del módulo *(si se mide)* | <antes → después> |

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | | |
| App móvil ↔ API | | |
| API ↔ PostgreSQL (RLS en la base real) | | |
| API ↔ tiempo real (`/ws`) | | |

### UAT — aceptación de usuario

| Campo | Valor |
|---|---|
| Perfil del participante | P1 — <perfil>; ¿conocía el sistema? Sí / No |
| Modalidad | Presencial · Remota (con pantalla compartida) |
| Facilitador | <nombre del integrante> |
| Duración medida | <minutos, con cronómetro> |
| Ayuda brindada | <«Ninguna» o en qué paso y qué se dijo> |
| Comentarios del participante | <palabras del participante, tomadas al momento> |
| ¿Acepta el escenario? | Sí · No · Con observaciones |
| Hoja de aceptación firmada | Guardada fuera del repositorio por <responsable> |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL, la fecha y hora del sistema y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado existe en `main` y corresponde al desplegado
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| <nombre> | `AAAA-MM-DD` | Aprobado · Cambios requeridos | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** <nombre> · **Fecha:** `AAAA-MM-DD`
