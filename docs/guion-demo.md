# Guion de la demostración

Recorrido verificado paso a paso haciendo clic real en cada pantalla. Los tiempos y las pantallas descritas aquí son lo que efectivamente ocurre, no lo que debería ocurrir.

Última verificación: 2026-07-31, sobre 120 pruebas en verde y 25 rutas de API.

---

## Antes de empezar

Hacer esto **con veinte minutos de margen**, no al momento.

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

Usar `npm start` y no `npm run dev`: en desarrollo Turbopack compila cada ruta la primera vez que se visita, y esa pausa de varios segundos se nota. Los dos comandos comparten el directorio `.next` y no deben correr a la vez.

### 2. Preparar tres ventanas separadas

La parte más fuerte de la demostración son tres roles trabajando al mismo tiempo, y eso **no funciona con tres pestañas**: comparten la cookie de sesión y la última que inicie sesión desconecta a las anteriores.

Hacen falta tres contextos aislados. Cualquiera de estas dos formas sirve:

- Tres ventanas de incógnito distintas, o
- Tres perfiles de Chrome (`chrome.exe --profile-directory="Perfil 1"`, y así)

Dejarlas abiertas, en `http://localhost:3000/login`, y con las tres cuentas ya escritas antes de que entre el jurado.

### 3. Calentar las rutas

Visitar una vez cada pantalla: `/login`, `/admin`, `/waiter`, `/kitchen`, `/cashier`, `/history`, `/platform`.

### 4. Dejar preparado aparte

- `http://localhost:8080/swagger-ui.html`
- Una terminal con `psql` lista para el script de aislamiento

### Cuentas

| Restaurante (slug) | Correo | Contraseña | Rol |
|---|---|---|---|
| `callejon-nueve` | `admin@callejon9.com` | `Secreto123!` | `ADMIN` |
| `callejon-nueve` | `mesero@callejon9.com` | `Secreto123!` | `WAITER` |
| `callejon-nueve` | `cocina@callejon9.com` | `Secreto123!` | `KITCHEN` |
| `callejon-nueve` | `caja@callejon9.com` | `Secreto123!` | `CASHIER` |
| `parrilla-norte` | `maria@parrillanorte.com` | `Secreto123!` | `ADMIN` |
| `platform` | `super@callejon9.com` | `Callejon9Demo!` | `SUPER_ADMIN` |

---

## El recorrido

Unos quince minutos. Conviene ensayarlo dos veces: la primera siempre aparece algo.

### 1. Alta de un restaurante · 1 min

En `/signup`, registrar uno nuevo en vivo. El plan viene en **Profesional** por defecto, que es el correcto: el gratuito topa en cinco mesas y en tres usuarios.

Al enviar redirige a `/login` **con el identificador ya cargado**.

> Cada alta crea un inquilino nuevo con su suscripción activa y su administrador.

### 2. Dar de alta al personal · 2 min

Entrar como administrador de `callejon-nueve` e ir a `/admin`, pestaña **Usuarios**. Ahí están el mesero, la cocinera y la cajera.

Crear uno más en vivo, y después intentar dos cosas que fallan a propósito:

- Repetir un correo ya usado → *"Ya existe un usuario con el correo … en este restaurante."*
- Desactivarse a sí mismo → *"Un administrador no puede desactivarse a si mismo."*

> El sistema también impide desactivar al último administrador activo. Sin esa regla, un clic deja al restaurante sin nadie que lo administre y sin forma de recuperarse desde la interfaz.

Y si el jurado pregunta por los planes: en un restaurante del plan gratuito, el cuarto usuario devuelve *"Alcanzaste el limite de 3 usuarios del plan FREE."*

### 3. Los tres roles, al mismo tiempo · 4 min

**Este es el momento central. Tres ventanas visibles a la vez.**

| Ventana | Cuenta | Lo que ve |
|---|---|---|
| 1 | `mesero@callejon9.com` | Solo *Mesas* |
| 2 | `cocina@callejon9.com` | Solo *Cocina* |
| 3 | `caja@callejon9.com` | Solo *Caja* e *Historial* |

Que cada barra lateral muestre únicamente su sección no es una decisión de diseño de la pantalla: es lo que el servidor autoriza. Un mesero pidiendo la lista de usuarios recibe `403`.

En la ventana del mesero: tocar una mesa libre, indicar comensales, agregar productos de dos categorías, y enviar a cocina.

**Sin tocar la ventana de cocina, la comanda aparece sola.** El tablero consulta cada cinco segundos.

En la ventana de cocina: avanzar cada renglón, *Pendiente → En preparación → Listo*. Solo se ofrece el siguiente paso legal; el servidor rechaza saltos y retrocesos con `409`. Al marcar el último, la comanda pasa a *Lista* por decisión del servidor y desaparece del tablero.

En la ventana de caja: seleccionar la comanda, 15% de propina, método de pago, cobrar. Aparece el ticket con su folio.

Volver a la ventana del mesero: **la mesa está libre otra vez**.

### 4. Equivocarse y arreglarlo · 3 min

Lo que separa un sistema de una demostración es poder deshacer.

**Corregir un precio.** En `/admin` → Productos, editar la arrachera y subirla de $289 a $320. Después abrir una comanda anterior: **sigue en $289**.

> El precio se copió al renglón cuando se agregó. Cambiar el catálogo no reescribe cuentas ya tomadas, y el ticket emitido es un documento inmutable.

**Retirar un platillo.** Desactivar uno y mostrar que desaparece del selector del mesero pero sigue apareciendo en las comandas que ya lo incluyen. En la lista del administrador queda marcado como inactivo, y se puede reactivar.

> Nunca se borra una fila: los productos y las mesas están referenciados por el histórico. Un botón que dijera "Eliminar" estaría mintiendo.

**Cancelar una comanda.** Abrir una en una mesa, y cancelarla desde la pantalla del mesero. La mesa vuelve a libre.

> `CANCELED` existía en el modelo desde la primera migración y hasta este incremento ningún camino del sistema lo alcanzaba. Una comanda equivocada no tenía salida.

### 5. El historial · 1 min

En `/history`: las ventas del rango con su total, el cajero que cobró cada una, y la búsqueda de un ticket por folio.

> Antes, una vez cobrada, la comanda desaparecía de todas las pantallas.

### 6. El aislamiento · 3 min

Este es el momento que sostiene el trabajo.

Entrar como `parrilla-norte`. La misma pantalla `/admin`, otro restaurante, otros números. Su historial de ventas está vacío aunque el otro restaurante tenga varias cobradas hoy.

> No ve nada del otro restaurante. Y no porque las consultas filtren: **ninguna consulta de este sistema lleva un filtro por inquilino**. Lo impone PostgreSQL.

Ir a la terminal:

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

> Sin inquilino activo no se ve ninguna fila. Y el último error es deliberado: PostgreSQL rechazando que un inquilino escriba dentro de otro.

### 7. Plataforma y API · 1 min

Entrar como `platform`: los tres planes con sus límites. Abrir `/swagger-ui.html`: las 25 rutas navegables con el esquema de autenticación por cookie declarado.

---

## Preguntas probables

**¿Y si un programador olvida el filtro por inquilino?**
No existe filtro que olvidar. Ningún repositorio lleva un predicado de `tenant_id`; la política la aplica el motor. Es justo el argumento: el sistema Flask original tenía la intención correcta en `BaseModel`, pero solo 2 de 19 modelos la heredaban.

**¿El dueño de la base no se salta las políticas?**
Se las saltaría, y por eso hay dos roles. `callejon9_app` no es dueño de ninguna tabla, y todas llevan `FORCE ROW LEVEL SECURITY`. La prueba concreta: la migración `V6` tuvo que publicar el inquilino con `set_config` antes de insertar el super-administrador, porque Flyway corre como dueño y la política lo rechazaba.

**¿Qué pasa si dos meseros abren la misma mesa a la vez?**
Uno recibe un conflicto. Se cierra con un bloqueo pesimista sobre la fila de la mesa, y hay una prueba con dos hilos que falla sin él. El mismo patrón protege el tope de usuarios del plan y la regla del último administrador: sin serializar por inquilino, dos peticiones simultáneas podían dejar un restaurante sin nadie que lo administre.

**¿Por qué PostgreSQL y no MongoDB, si el original usaba Mongo?**
Aislamiento impuesto por el motor; cobrar toca cinco tablas en una transacción; planes y suscripciones son relacionales puros; y el modelo original ya era relacional disfrazado, pagando el costo de Mongo sin usar su ventaja.

**¿Los usuarios tienen que cambiar su contraseña?**
No. `BcryptCompatibilityTest` valida un hash generado con la librería `bcrypt` de Python usando Spring Security.

**¿Cuántas pruebas hay?**
120, contra PostgreSQL real, verdes en CI sobre un runner de GitHub Actions. No se usa H2: no soporta RLS, así que probar contra H2 invalidaría la garantía que el proyecto demuestra.

**¿Qué falta?**
Delivery y rastreo, analítica y aprendizaje automático, sensores IoT, MercadoPago real, segundo factor, y el ETL desde MongoDB. Documentado con su diseño en el README: diferido, no olvidado.

---

## Si algo falla

**Una pantalla tarda.** Compilación en caliente. Se evita con `npm start` en vez de `npm run dev`, y calentando las rutas antes.

**Sesión expirada.** Solo pasa sin el perfil `demo`. Volver a entrar; el sistema muestra un aviso sobrio.

**El cobro devuelve 409.** La comanda ya se cobró. Abrir otra.

**Una mesa aparece ocupada y no debería.** Cobrar su comanda desde `/cashier`, o cancelarla desde la pantalla del mesero.

**Las tres ventanas se desconectan entre sí.** Están compartiendo cookie: no son perfiles ni incógnitos separados. Ver el paso 2 de la preparación.

**El frontend no responde.** Comprobar el backend en `http://localhost:8080/actuator/health`; debe devolver `{"status":"UP"}`. El frontend solo hace de intermediario hacia él.

**Plan de contingencia.** Si el frontend falla por completo, la demostración se puede hacer entera desde Swagger UI más `psql`: alta de dos restaurantes, login en cada uno, y el script de aislamiento. Se pierde el impacto visual, no el argumento.
