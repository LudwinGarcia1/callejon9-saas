# Evidencia de pruebas

Esta guía define cómo documentar la evidencia de las issues de prueba (etiqueta `Testing`: UT, SIT y UAT). El objetivo es que cualquier integrante, docente o evaluador pueda leer la evidencia, entender qué se probó y **reproducir la prueba** sin preguntarle a quien la ejecutó.

La regla de fondo está en [`workflow/workflow.md`](../../workflow/workflow.md#estados-de-linear): una prueba que no se ejecutó no cuenta como evidencia, aunque el formato esté lleno, y quien revisa no es quien ejecutó.

## Estructura

Cada issue de prueba tiene su propia carpeta:

```text
docs/pruebas/
├── README.md                     ← esta guía
├── plantilla-evidencia.md        ← la plantilla que se copia
├── ut/
├── sit/
│   └── CAL-140-cocina-tiempo-real/
│       ├── README.md             ← la evidencia, a partir de la plantilla
│       ├── capturas/
│       │   ├── C01-P01-tablero-vacio.png
│       │   └── C01-P03-pedido-recibido.png
│       └── respuestas/
│           ├── health.txt
│           └── C03-handshake-ws.txt
└── uat/
```

- Carpeta: `CAL-<número>-<módulo-en-minúsculas-con-guiones>`.
- Capturas: `C<caso>-P<paso>-<descripción-corta>.png`, por ejemplo `C02-P04-rechazo-409.png`.
- Respuestas HTTP: `C<caso>-<descripción>.txt`, con la salida literal de `curl -i` o copiada de DevTools.

## Flujo

1. **Antes de ejecutar.** Mueve el issue a In Progress, copia la plantilla a su carpeta y llena las secciones 1 a 5: identificación, responsables, fechas planeadas, criterios y preparación. En SIT y UAT, confirma que el entorno esté encendido y toma el commit desplegado del registro de despliegue (ver abajo); en UT, el commit es el head del PR.
2. **Durante la ejecución.** Sigue los pasos y llena cada caso **en el momento**: resultado obtenido, hora, captura y respuesta. Si algo falla, el caso queda ❌ Fallido; no se reintenta en silencio hasta que pase.
3. **Al terminar.** Registra los defectos en Linear, llena el resumen y la declaración de autenticidad, y abre un PR con la carpeta. Mueve el issue a In review y adjunta en Linear el enlace al PR y las capturas principales.
4. **Revisión.** Otro integrante revisa con la lista de la sección 10. Si aprueba, el issue puede pasar a Done una vez fusionado el PR; si pide cambios, vuelve a In Progress.

## Registro de despliegue

El backend y el frontend se despliegan a mano (`az webapp deploy`) y la aplicación no expone qué commit está corriendo. Por eso quien despliega deja un comentario «Despliegue» en el issue de prueba, o en su padre CAL-117 o CAL-118, con el SHA de `main`, qué se desplegó (API, frontend o ambos), la fecha y la hora. Ese comentario es la fuente del commit probado en SIT y UAT. Si no existe, el commit no es verificable: se registra el despliegue antes de ejecutar, o los casos quedan ⛔ Bloqueados.

## Capturas de pantalla

Una captura es evidencia solo si demuestra **dónde** y **cuándo** se tomó.

- Ventana completa del navegador con la **barra de direcciones visible** y el **reloj del sistema** en la barra de tareas. En el teléfono, la barra de estado con la hora.
- Formato PNG, sin recortes que oculten la URL. Se permite resaltar con un recuadro rojo el elemento relevante.
- Una captura por paso significativo: el estado antes, la acción y el resultado.
- Para el tiempo real o la red, captura también DevTools → Network (pestaña WS o la petición), con el código de estado visible.
- **Tapa** contraseñas, valores de cookies, tokens y cualquier dato personal antes de guardarla. Nada más se edita.
- **Capturas automatizadas** (Playwright u otra herramienta): no muestran la barra de direcciones ni el reloj, así que el script estampa en la parte superior una banda con la URL, la fecha y la hora reales al momento de la captura. El script se versiona dentro de la carpeta de la evidencia (por ejemplo `playwright/`) para que cualquiera pueda volver a ejecutarlo, y lee las credenciales de variables de entorno.

## Respuestas HTTP

- Copia la respuesta **completa y literal**: línea de estado, cabeceras y cuerpo. Usa `curl -i` o «Copy response» en DevTools.
- Reemplaza el valor de `Set-Cookie` y `Cookie` por `<redactado>` y conserva el resto.
- No redactes respuestas «esperadas» como si fueran obtenidas. Si no tienes la respuesta real, el caso está ⏭️ No ejecutado.
- La API usa la cookie de sesión; nunca hay `Authorization: Bearer` ni `X-Tenant-ID` en una evidencia real de este proyecto.

## Fechas y responsables

- Todas las horas en `America/Mexico_City` con formato `AAAA-MM-DD HH:MM`.
- La sección 3 registra fechas planeadas y reales de la historia padre, de la tarea y de cada fase. La fecha real debe coincidir con el historial del issue en Linear.
- El ejecutor y el revisor son personas distintas. En UAT, el participante no puede ser integrante del equipo y se identifica por perfil (P1, P2…), no por nombre.

## Estados de un caso

| Estado | Cuándo |
|---|---|
| ✅ Aprobado | El resultado obtenido coincide con el esperado en todos los pasos |
| ❌ Fallido | Al menos un paso dio un resultado distinto; hay un defecto registrado |
| ⛔ Bloqueado | No se pudo ejecutar por una causa externa (entorno caído, dependencia sin terminar); se anota la causa |
| ⏭️ No ejecutado | No se intentó; se explica por qué |

## Severidad de los defectos

| Severidad | Criterio | Ejemplo |
|---|---|---|
| Crítica | Rompe el aislamiento entre restaurantes, expone datos o impide operar el turno | Un restaurante ve las órdenes de otro |
| Alta | Un flujo principal falla sin alternativa razonable | No se puede cobrar una orden con pago dividido |
| Media | Falla con alternativa, o un mensaje engaña al usuario | El tablero de cocina requiere recargar para ver un pedido |
| Baja | Detalle visual o de redacción que no afecta la operación | Un texto cortado en la tarjeta de una mesa |

Un defecto crítico o alto bloquea el cierre de la fase (SIT o UAT) hasta su re-prueba.

## Qué rechaza la revisión

Estos son los motivos por los que se reabrieron evidencias del ciclo 6; sirven como lista de comprobación antes de pedir revisión.

- Hosts, rutas, códigos o mensajes que no existen en el código. El revisor los contrasta con los controllers y servicios.
- Respuestas redactadas a mano o marcadas «esperada y obtenida» sin la salida real.
- Falta de capturas cuando el issue las pide, o capturas sin URL ni hora.
- Participantes sin perfil, citas genéricas o tiempos no medidos en UAT.
- Funciones descritas que la aplicación no tiene.
- UAT ejecutada antes de aprobar la SIT del mismo módulo.
- Pruebas unitarias que no prueban la lógica del issue (solo constantes o constructores) o que no fallan al romper la regla.
- Issue padre en Done con hijos abiertos.

## Entrega académica

Para un entregable en PDF, exporta el `README.md` de cada carpeta con sus imágenes; no se redacta un informe aparte con datos distintos. Un informe consolidado solo resume y enlaza evidencias ya revisadas, y su fecha de corte es posterior a la última revisión aprobada.
