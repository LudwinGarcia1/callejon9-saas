# Cabeceras de seguridad HTTP

## Alcance y criterios

Next.js protege las paginas, incluidas `/login`, `/signup`, rutas protegidas y
errores, y emite las cabeceras comunes tambien sobre assets. Spring protege sus
respuestas API, incluidas las denegaciones de acceso y Swagger. Se conserva el
rewrite `/api/v1/*`, la autenticacion con cookie y el aislamiento RLS.

| Cabecera | Politica |
|---|---|
| Content-Security-Policy | Scripts de Next con nonce aleatorio de 128 bits por respuesta; `frame-ancestors 'none'`, `object-src 'none'`, origen propio para conexiones, fuentes y formularios |
| X-Frame-Options | `DENY` |
| X-Content-Type-Options | `nosniff` |
| Referrer-Policy | `strict-origin-when-cross-origin` |
| Strict-Transport-Security | `max-age=31536000`, sin preload ni alcance automatico a subdominios |

La CSP se aplica, no es `Report-Only`. Next recibe el nonce en la cabecera interna
de peticion para asignarlo a los scripts del framework. El middleware reemplaza
las cabeceras CSP y nonce que pueda suministrar el cliente. Las paginas se
renderizan dinamicamente; no deben almacenarse como HTML compartido en un CDN.

Los estilos inline siguen permitidos por los graficos, Radix, Sonner y el tema del
restaurante. Los scripts inline sin nonce estan bloqueados. `unsafe-eval` y las
conexiones WebSocket de HMR solo se permiten con `NODE_ENV=development`.
Las fuentes de `next/font` se sirven desde el propio origen.

Spring usa una CSP independiente para la API y Swagger: bloquea recursos por
defecto y permite los scripts, estilos, imagenes y conexiones locales que necesita
Swagger. No se agrega una segunda CSP de pagina a las respuestas del rewrite API.

## HTTPS y HSTS

Next emite HSTS en produccion (`pnpm build` y `pnpm start`); los navegadores solo
lo aplican cuando reciben la respuesta mediante HTTPS. Se puede desactivar para
una ejecucion local de produccion con `SECURITY_HSTS_ENABLED=false` antes de
compilar y arrancar. En desarrollo no se emite.

Spring emite HSTS solo para peticiones reconocidas como HTTPS. Si TLS termina en
un proxy, configura el reconocimiento de cabeceras forwarded en el despliegue
(por ejemplo `SERVER_FORWARD_HEADERS_STRATEGY=native` con proxies de confianza
limitados en Tomcat). El proxy debe eliminar las cabeceras forwarded del cliente y
generar las propias. Comprueba HSTS en la URL HTTPS publica antes de dar el
despliegue por validado; localhost HTTP no demuestra su efecto en el navegador.

## Verificacion

1. Abre `http://localhost:3001/login` y `/signup`.
2. En F12, abre Network, recarga y selecciona la peticion del documento.
3. En Response Headers comprueba CSP, X-Frame-Options, X-Content-Type-Options y
   Referrer-Policy. En produccion comprueba tambien HSTS.
4. Recarga: el nonce de CSP debe cambiar. En el HTML los scripts de Next llevan
   ese mismo nonce. Comprueba que no aparecen errores CSP en Console y que los
   formularios, navegacion y graficos siguen funcionando.
5. Un iframe desde otra pagina no debe poder mostrar el login: Console indica el
   rechazo por `frame-ancestors` o X-Frame-Options.

Comprobacion automatica desde la raiz (PowerShell):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-security-headers.ps1 -ExpectFrontendHsts
```

Omite `-ExpectFrontendHsts` con `pnpm dev` o HSTS desactivado. El script exige HTTP
200 en login/registro, comprueba que los scripts tienen nonce, que este cambia
entre peticiones y que el backend y el rewrite conservan cabeceras en HTTP 401.
Para HTTPS usa `-FrontendOrigin https://tu-dominio` y
`-BackendOrigin https://tu-origen-api`, con los certificados validos del entorno.

Pruebas del proyecto: `pnpm test`, `pnpm lint`, `pnpm exec tsc --noEmit`,
`pnpm build`, y `backend/.\mvnw.cmd -B verify` con PostgreSQL real.
En esta maquina el wrapper falla antes de Maven: el equivalente disponible es
`mvn.cmd -B -DDB_PORT=5432 verify` desde `backend`.

Referencias: [CSP en Next.js](https://nextjs.org/docs/app/guides/content-security-policy)
y [cabeceras de Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).
