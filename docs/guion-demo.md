# Guion de la demostración

Recorrido verificado paso a paso el 2026-07-30, haciendo clic real en cada pantalla. Los tiempos y las pantallas descritas aquí son lo que efectivamente ocurre, no lo que debería ocurrir.

---

## Antes de empezar

Hacer esto **con quince minutos de margen**, no al momento.

### 1. Levantar todo

```powershell
# Terminal 1 — backend
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:DB_APP_PASSWORD = 'app_dev_pwd'
$env:DB_OWNER_PASSWORD = 'owner_dev_pwd'
$env:JWT_SECRET = 'dev-only-secret-change-me-min-32-bytes-long!!'
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

```powershell
# Terminal 2 — frontend, compilado, NO en modo desarrollo
cd frontend
npm run build
npm start
```

El perfil `demo` extiende el token a dos horas. **Sin él la sesión expira a los quince minutos**, probablemente a media presentación.

Usar `npm start` y no `npm run dev`: en desarrollo Turbopack compila cada ruta la primera vez que se visita, y esa pausa de tres a seis segundos se nota frente a un público. Los dos comandos comparten el directorio `.next`, así que no deben correr a la vez.

### 2. Calentar las rutas

Abrir una vez cada pantalla antes de que entre el jurado: `/login`, `/admin`, `/waiter`, `/kitchen`, `/cashier`, `/platform`. Así ninguna tarda en el momento.

### 3. Comprobar que hay datos

Entrar como `callejon-nueve` y confirmar seis mesas libres, tres categorías y siete productos. Si falta algo, crearlo desde `/admin` — esos formularios existen justo para eso.

### 4. Dejar preparado en pestañas aparte

- `http://localhost:8080/swagger-ui.html`
- Una terminal con `psql` listo para el script de aislamiento

### Cuentas

| Restaurante (slug) | Correo | Contraseña | Para qué |
|---|---|---|---|
| `callejon-nueve` | `admin@callejon9.com` | `Secreto123!` | El recorrido principal |
| `parrilla-norte` | `maria@parrillanorte.com` | `Secreto123!` | El contraste de aislamiento |
| `platform` | `super@callejon9.com` | `Callejon9Demo!` | El panel de plataforma |

---

## El recorrido

Doce minutos si nadie interrumpe. Conviene ensayarlo dos veces: la primera siempre aparece algo.

### 1. Alta de un restaurante · 1 min

Ir a `/signup` y registrar uno nuevo en vivo. El plan viene en **Profesional** por defecto, que es el correcto: el gratuito topa en cinco mesas y estorba.

Al enviar redirige a `/login` **con el identificador ya cargado**.

> Cada restaurante que se da de alta es un inquilino nuevo de la plataforma. En un segundo se creó su registro, su suscripción activa y su usuario administrador.

### 2. Entrar y recargar · 1 min

Iniciar sesión. La barra lateral muestra el nombre del restaurante y el del usuario.

**Recargar la página con F5.** La sesión sobrevive.

> La sesión vive en una cookie `httpOnly`, que JavaScript no puede leer. Al recargar, el frontend le pregunta al servidor quién es el usuario. El token nunca queda expuesto al navegador.

### 3. Preparar el salón · 2 min

En `/admin`, crear una mesa. El contador sube, la tabla se actualiza, aparece la notificación.

Crear una segunda mesa **con el mismo número**. Devuelve un error legible: *"Ya existe una mesa con el numero N."*

> La validación no está en el formulario: viene del servidor, con un `409` en formato RFC 7807, y el frontend solo la muestra. El cliente no duplica reglas que el servidor ya aplica.

### 4. Tomar la comanda · 3 min

En `/waiter`, tocar una mesa libre e indicar los comensales. Se abre la comanda y la mesa pasa a ocupada.

Agregar productos de **dos categorías distintas**, revisar el carrito, y confirmar en una sola operación. El total coincide con la suma de los renglones.

> Los productos se agregan por lote, en una sola petición. Y el precio se congela al agregarse: si mañana sube el precio de la arrachera, esta comanda no cambia. El ticket es un documento inmutable.

Enviar a cocina.

### 5. Cocina · 2 min

En `/kitchen` aparece la comanda con sus renglones en *Pendiente*.

Avanzar cada uno: *Pendiente → En preparación → Listo*. Solo se ofrece el siguiente paso legal; el servidor rechaza cualquier salto o retroceso con un `409`.

Al marcar el último, un aviso: *"Todos los productos están listos. Pasará a Lista en el tablero."* La comanda desaparece del tablero porque ya no está en cocina.

> Esa promoción la decide el servidor, no la pantalla. El tablero se refresca cada cinco segundos. Existe además un canal STOMP en el backend, con validación de inquilino por suscripción, pero la interfaz usa consulta periódica: para una demostración, una conexión que puede caerse es un riesgo que no compensa.

### 6. Cobrar · 2 min

En `/cashier`, seleccionar la comanda. Elegir **15%** de propina y un método de pago. El total estimado se actualiza en vivo.

Cobrar. Aparece el ticket con su folio y el total definitivo.

> El monto que se muestra ahora no es el que calculó el navegador: es el que devolvió el servidor. La vista previa es cortesía; la cifra que vale la calcula el backend dentro de la transacción.

Descargar el PDF.

**Volver a `/waiter`: la mesa está libre otra vez.** Cerrar la venta la libera, dentro de la misma transacción que crea el ticket.

### 7. El aislamiento · 3 min

Este es el momento que sostiene el trabajo.

Cerrar sesión —**esperar un segundo**, hay un instante de contenido viejo antes de que redirija— y entrar como `parrilla-norte`.

La misma pantalla `/admin`, otro restaurante:

```
La Parrilla del Norte  ->  Mesas 1   Categorias 0  Productos 0  Ordenes 0
Callejon 9 Centro      ->  Mesas 6   Categorias 3  Productos 7  Ordenes 3
```

> No ve nada del otro restaurante. Y no porque las consultas filtren: **ninguna consulta de este sistema lleva un filtro por inquilino**. Lo impone PostgreSQL.

Ir a la terminal y ejecutar:

```powershell
$env:PGPASSWORD='app_dev_pwd'
& 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -U callejon9_app -p 5433 -d callejon9_test -f scripts\verify-rls.sql
```

```
check_1  sin tenant -> 0
check_2  tenant A ve -> 1
check_3  tenant B ve -> 0
check_4  ERROR: new row violates row-level security policy for table "users"
```

> Sin inquilino activo no se ve ninguna fila. Con el inquilino A se ve lo de A. Con el B, lo de A es invisible. Y el último error es deliberado: es PostgreSQL rechazando que un inquilino escriba dentro de otro.

### 8. Plataforma y API · 2 min

Entrar como `platform`. El panel muestra los tres planes con sus límites.

Abrir `/swagger-ui.html`: la API completa, navegable, con el esquema de autenticación por cookie declarado.

---

## Preguntas probables

**¿Y si un programador olvida el filtro por inquilino?**
No existe filtro que olvidar. Ningún repositorio lleva un predicado de `tenant_id`; la política lo aplica el motor. Ese es justamente el argumento: el sistema Flask original tenía la intención correcta en `BaseModel`, pero solo 2 de 19 modelos la heredaban.

**¿El dueño de la base no se salta las políticas?**
Se las saltaría, y por eso hay dos roles. `callejon9_app` no es dueño de ninguna tabla, y todas llevan `FORCE ROW LEVEL SECURITY`, que somete incluso al dueño. La prueba concreta: la migración `V6` tuvo que publicar el inquilino con `set_config` antes de insertar el super-administrador, porque Flyway corre como dueño y la política lo rechazaba.

**¿Por qué PostgreSQL y no MongoDB, si el original usaba Mongo?**
Cuatro razones. Aislamiento impuesto por el motor. Cobrar toca cinco tablas en una transacción. Planes y suscripciones son datos relacionales puros. Y el modelo original ya era relacional disfrazado: pagaba el costo de Mongo sin usar su ventaja.

**¿Los usuarios tienen que cambiar su contraseña?**
No, y hay una prueba que lo demuestra. `BcryptCompatibilityTest` valida un hash generado con la librería `bcrypt` de Python usando Spring Security.

**¿Por qué no hay Docker?**
La máquina de desarrollo no lo tiene. Las pruebas corren contra un PostgreSQL 16 nativo, y en CI contra un contenedor de servicio de GitHub Actions. No se usa H2 en ningún caso: H2 no soporta RLS, así que probar contra H2 invalidaría exactamente la garantía que el proyecto demuestra.

**¿Por qué Spring Boot 3.4 y no 4?**
Spring Initializr ya solo ofrece la línea 4, pero springdoc —que genera la documentación navegable— no tiene versión para Spring Framework 7. Elegir la 4 habría costado el Swagger y una migración de Spring Security 7 de alcance desconocido, a dos días de la entrega.

**¿Cuántas pruebas hay?**
77, contra PostgreSQL real, verdes en CI sobre un runner de GitHub Actions.

**¿Qué falta?**
Delivery y rastreo, analítica y aprendizaje automático, sensores IoT, MercadoPago real, segundo factor, y el ETL de migración desde MongoDB. Está documentado con su diseño en el README: diferido, no olvidado.

---

## Si algo falla

**Una pantalla tarda.** Es la compilación en caliente. Se evita usando `npm start` en vez de `npm run dev`, y calentando las rutas antes.

**Sesión expirada.** Solo pasa si se arrancó sin el perfil `demo`. Volver a entrar; el sistema muestra un aviso sobrio, no un error.

**El cobro devuelve 409.** La comanda ya se cobró. Abrir una nueva.

**La mesa aparece ocupada y no debería.** Cobrar su comanda desde `/cashier`, o abrirla y continuarla.

**El frontend no responde.** Comprobar que el backend siga arriba: `http://localhost:8080/actuator/health` debe devolver `{"status":"UP"}`. El frontend hace de intermediario hacia él; si el backend cae, ninguna pantalla carga datos.

**Plan de contingencia.** Si el frontend falla por completo, la demostración se puede hacer entera desde Swagger UI más `psql`: alta de dos restaurantes, login en cada uno, y el script de aislamiento. Se pierde el impacto visual, no el argumento.
