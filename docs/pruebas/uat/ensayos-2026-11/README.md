# Ensayos UAT del 18 y 19 de noviembre de 2026

Responsable: Braulio Hernández Mejía.

Esta carpeta guarda los ensayos de 11 de las 13 pruebas UAT de CAL-117. Los documentos se generaron el 18 y el 19 de noviembre y se suben al repositorio después, para que quede evidencia del trabajo.

**Qué es un ensayo.** Cada paso se ejecutó de verdad contra producción en Azure, y las capturas y bitácoras son reales. La ejecución fue automatizada y **sin participantes del negocio**. Por eso un ensayo sirve para encontrar defectos y preparar la sesión, pero **no cuenta como aceptación del usuario**. Así lo dicen también los propios documentos.

## Qué pruebas incluye

| Grupo | Issues | Ensayo del 18-nov | Resultado final del 19-nov |
|---|---|---|---|
| Checklist de aceptación | CAL-146, 151, 152, 155, 158 | 15 pasos aprobados y 5 que no cumplen (CAL-194, CAL-195, notas y quitar productos) | Los 5 pasos que fallaban se repitieron y pasan |
| Operación del restaurante | CAL-147, 148, 150, 153, 154, 157 | 19 de 20 pasos aprobados; el paso 8.3 de CAL-153 no cumple | 20 de 20 pasos aprobados |

CAL-149 (Catálogo) y CAL-156 (Inventario) no se ensayaron aquí. Valeria Mercado las ejecutó y documentó en sus propias carpetas.

### Operación del restaurante, por issue

| Issue | Escenario | 18-nov | 19-nov |
|---|---|---|---|
| CAL-147 Alta de restaurantes y planes | UAT-02 | 4 de 4 pasan | Sin reensayo; conserva el resultado |
| CAL-148 Usuarios y roles | UAT-03 | 4 de 4 pasan, con un hallazgo: el aviso «Sesión expirada» aparece sin sesión previa | Hallazgo corregido en `main` |
| CAL-150 Mesas | UAT-05 | 3 de 3 pasan | Sin reensayo; conserva el resultado |
| CAL-153 Cocina y tiempo real | UAT-08 | 2 de 3 pasan: el mesero no ve «Lista» sin recargar | 3 de 3 pasan (corregido con CAL-170 y CAL-172) |
| CAL-154 Cobro y pagos | UAT-09 | 4 de 4 pasan | Sin reensayo; conserva el resultado |
| CAL-157 Ventas, historial y analítica | UAT-12 | 2 de 2 pasan | Sin reensayo; conserva el resultado |

## Estructura

```
18-11-ensayo/
  checklist-aceptacion/    documento combinado, uno por issue, capturas y bitácora
  operacion-restaurante/   documento combinado y capturas
19-11-reensayo/            después de publicar main (66f32e0)
  checklist-aceptacion/    documentos v1.1, capturas y bitácora del reensayo
  operacion-restaurante/   documento v1.1, capturas y bitácora del reensayo
```

Las capturas sueltas son las mismas que van dentro de los documentos Word. Del ensayo de operación del 18-nov no se conservó la bitácora en texto aparte; su contenido está en el anexo del documento.

## Entorno

- Web: https://restohub.azurewebsites.net. El 18-nov corría el frontend `68cf1f0`; el 19-nov ya corría `main` en `66f32e0`.
- API: App Service en México Central, contra la base de producción y el restaurante `pruebas`.
- App móvil: teléfono Android conectado por USB (CAL-152).
- Cuentas: solo las de prueba del restaurante `pruebas`. Las capturas y bitácoras no tienen contraseñas ni datos personales.

## Lo que falta para la aceptación

Cada issue pide que la prueba la ejecute un participante del negocio y registre si acepta o no. Estos ensayos dejan listo el guion y los resultados esperados, pero esa sesión con participantes sigue pendiente.
