# Sesión de UAT de caja y cierre del día

Una sola sesión ejecuta los escenarios que dependen uno del otro: UAT-09 cobra la cuenta que capturó UAT-06, UAT-10 usa el ticket de UAT-09, y UAT-12 cuadra los totales de todo lo cobrado. Si se hacen en días distintos, la línea base y los totales dejan de cuadrar.

| Orden | Escenario | Issue | Responsable del issue | Participante | Evidencia |
|---|---|---|---|---|---|
| 0 | Línea base del historial (antes de cobrar nada) | CAL-157 §5 | Octavio Duarte | — (lo toma el facilitador) | `CAL-157-ventas-analitica/README.md` |
| 1 | UAT-09 · Cobro y pagos | [CAL-154](https://linear.app/callejon19/issue/CAL-154) | Octavio Duarte | P1, cajero | `CAL-154-cobro-pagos/README.md` |
| 2 | UAT-10 · Tickets | [CAL-155](https://linear.app/callejon19/issue/CAL-155) | Braulio Hernández Mejía | P1, cajero | Su propia carpeta |
| 3 | UAT-12 · Ventas, historial y analítica | [CAL-157](https://linear.app/callejon19/issue/CAL-157) | Octavio Duarte | P1, cajero; P2, administrador | `CAL-157-ventas-analitica/README.md` |

## Antes de agendarla

- [ ] Las SIT de cobro ([CAL-141](https://linear.app/callejon19/issue/CAL-141)) y de ventas y analítica ([CAL-144](https://linear.app/callejon19/issue/CAL-144)) están **aprobadas**. Ninguna UAT de esta sesión se ejecuta antes.
- [ ] UAT-06 ([CAL-151](https://linear.app/callejon19/issue/CAL-151)) dejó al menos una cuenta lista para cobrar, y hay una segunda cuenta para el caso de pagos insuficientes.
- [ ] Hay un comentario «Despliegue» en Linear con el SHA que está corriendo en Azure.
- [ ] Están asignados los roles de la sesión:
  - Facilitador: ejecutor de la evidencia, distinto de los participantes.
  - P1 y P2: integrantes que no implementaron ni probaron en SIT estos módulos.
  - Revisor posterior: distinto de todos los anteriores.
- [ ] Están impresas o abiertas en otra pantalla las hojas de aceptación y el anexo A de CAL-154.

## Por qué participan integrantes del equipo

`docs/pruebas/README.md` pide participantes externos al equipo. El proyecto no tiene clientes ni restaurantes piloto, así que los participantes son integrantes. Cada evidencia lo declara como desviación en sus secciones 2 y 8, con estas salvaguardas:

- El participante no construyó ni probó el módulo que usa.
- El participante solo ve su guion, nunca la evidencia.
- La pregunta de aceptación se lee literal y sin pistas.
- Toda ayuda queda anotada.

Si antes de la sesión aparece una persona externa dispuesta a participar, por ejemplo el encargado de una fonda o un familiar con experiencia en caja, se prefiere a ella y la desviación desaparece.
