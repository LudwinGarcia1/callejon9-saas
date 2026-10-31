# Canal en tiempo real por el mismo origen del frontend

- **Estado:** aceptada
- **Fecha:** 2026-10-30
- **Decide:** Braulio Hernández Mejía, al corregir los defectos de la SIT CAL-140
- **Issues:** CAL-172 (cliente web de `/ws`), CAL-171 (origen permitido en Azure), CAL-170 (vista del mesero)

## Contexto

La SIT CAL-140 encontró que el backend publica los eventos de cocina en `/topic/tenant.{id}.kitchen`, pero ningún cliente web abre `/ws`: la cocina consulta cada 5 s y el mesero tiene que recargar. CAL-172 pedía decidir cómo llega el canal al navegador antes de implementar.

Hechos que condicionan la decisión:

- La sesión vive en la cookie `access_token`: `httpOnly`, `SameSite=Strict` y emitida para el dominio del frontend, porque el navegador llama a `/api/v1/*` en ese dominio y Next lo reenvía. El handshake de `/ws` se autentica con esa misma cookie (`JwtHandshakeInterceptor`).
- `AGENTS.md` prohíbe guardar el token en JavaScript y agregar CORS innecesario.
- Next 15.5 reenvía el upgrade de WebSocket de un rewrite a un origen externo (`upgradeHandler` en `router-server.js`, con `ws: true` y `changeOrigin: true`), también en la salida `standalone`.
- El backend valida el `Origin` del handshake contra `APP_ALLOWED_ORIGINS`.

## Opciones consideradas

| Opción | Cookie | Consecuencia |
|---|---|---|
| Rewrite de `/ws` en Next, mismo origen | Sigue `SameSite=Strict` y de primera parte | Sin cambios en la sesión ni CORS; el frontend debe tener WebSockets activados y cada conexión pasa por Next |
| Navegador directo al host de la API | Tendría que ser `SameSite=None` y cruzar sitios | Debilita la sesión de todo el sistema para un solo canal |
| Token en un parámetro o en una trama STOMP | El token pasa por JavaScript | Lo prohíbe `AGENTS.md` |
| Solo consulta periódica | Sin cambios | No cumple el criterio de CAL-172 ni aprovecha los eventos que ya publica el backend |

## Decisión

El navegador abre `/ws` en el origen del frontend y `next.config.ts` lo reenvía al backend, igual que `/api/v1/*`. Los clientes se suscriben al tópico de su restaurante y, con cada evento, invalidan sus consultas en vez de mezclar el evento en la cache. La consulta periódica se queda como respaldo.

## Consecuencias

- `APP_ALLOWED_ORIGINS` debe ser el origen público del frontend (`frontend_origin` en `azure-backend`, CAL-171).
- El App Service del frontend necesita `websockets_enabled = true`. En F1, Azure admite 5 conexiones WebSocket simultáneas por app: alcanza para la demo, no para un restaurante en servicio.
- Cada conexión pasa por Next y por el App Service del frontend. Si eso limita, la alternativa es exponer la API bajo un subdominio del mismo sitio, lo que conservaría `SameSite=Strict`.
- La app móvil no usa este canal (criterio 3 de CAL-170 fuera de alcance).

## Señales para revisarla

- Más de 5 conexiones simultáneas en Azure, o desconexiones del canal atribuibles al proxy de Next.
- Un cambio de dominio que ponga el frontend y la API en el mismo sitio.
