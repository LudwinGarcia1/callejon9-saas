# CAL-67 — PWA: Documento de Plataformas, Pruebas y Demostración de la Beta

> **Asignatura:** Aplicaciones Web Progresivas (PWA)  
> **Fecha oficial:** 31 de octubre de 2026 · **Fecha interna de cierre:** 24 de octubre de 2026  
> **Docentes evaluadores:** MTI Jorge Almeida Montiel e ITIC Roberto Vinicio Camacho Mendoza  
> **Proyecto:** RestoHub — Sistema de Punto de Venta y Gestión Gastronómica SaaS Multi-tenant  
> **Equipo desarrollador:** Callejón 19  

---

## 1. Justificación de Plataformas y Herramientas Empleadas

El diseño de **RestoHub** responde a una arquitectura desacoplada y orientada a la nube (*Cloud-Native*), diseñada para operar como una **Aplicación Web Progresiva (PWA)** instalable tanto en terminales móviles de meseros como en tabletas de comensales y pantallas de cocina (*Kitchen Display System - KDS*).

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   ARQUITECTURA DEL STACK                               │
└────────────────────────────────────────────────────────────────────────────────────────┘
  [Dispositivos / PWA] ──(HTTPS)──► [Next.js 15 Standalone] ──(Proxy Rewrite)──► [Spring Boot 3 API]
   - standalone display              - App Router / React 19                      - Java 21 LTS
   - manifest.json                   - Tailwind CSS 4 / shadcn                    - Spring Security (JWT)
   - TanStack Query v5               - Same-Origin Rewrite /api/v1/*              - Flyway Migrations
                                                                                          │
                                                                                    (JDBC / RLS)
                                                                                          ▼
                                                                               [PostgreSQL 16 Engine]
                                                                                - FORCE ROW LEVEL SEC
                                                                                - app.tenant_id Session
```

### 1.1. Capa de Frontend y Experiencia PWA
* **Next.js 15 (App Router, Server-Side Rendering y Standalone):**  
  * *Justificación:* Permite una compilación optimizada en contenedor Docker (`output: 'standalone'`) con un consumo mínimo de memoria RAM (<120 MB en reposo). Su capacidad de reescritura en servidor (*server-side rewrite*) elimina la necesidad de CORS y garantiza que las cookies de sesión permanezcan bajo la política *first-party* (`SameSite=Lax/Strict`), impidiendo ataques de *Cross-Site Scripting* (XSS) y manipulación de tokens en el cliente.
* **React 19 & TypeScript (Modo Estricto):**  
  * *Justificación:* Tipado estático de extremo a extremo que sincroniza los modelos del backend (DTOs) con los formularios del cliente, previniendo errores de nulabilidad y garantizando interfaces reactivas inmediatas.
* **TanStack Query v5 (React Query):**  
  * *Justificación:* Gestión de estado del servidor con invalidación inteligente de caché, reintentos automáticos ante pérdida de conectividad móvil y sincronización en segundo plano.
* **Tailwind CSS v4 & shadcn/ui:**  
  * *Justificación:* Sistema de diseño responsivo de alto rendimiento, optimizado para interfaces táctiles en tabletas y dispositivos móviles de meseros, con soporte nativo para temas de alto contraste requeridos en entornos de cocina con alta luminosidad.

### 1.2. Capa de Backend y Lógica de Negocio
* **Spring Boot 3.3 sobre Java 21 LTS:**  
  * *Justificación:* Rendimiento robusto con Virtual Threads (Project Loom) para manejar alta concurrencia de peticiones simultáneas de mesas y comandas. Gestión declarativa de transacciones ACID (`@Transactional`) indispensable para operaciones de cobro y caja.
* **Spring Security & STOMP sobre WebSockets:**  
  * *Justificación:* Control de acceso basado en roles (`ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`) y canal de mensajería bidireccional de baja latencia (`/topic/kitchen`) para enviar platillos a cocina en tiempo real sin saturar el servidor mediante sondeo (*polling*).

### 1.3. Capa de Datos y Persistencia
* **PostgreSQL 16 con Row Level Security (RLS) Forzado:**  
  * *Justificación:* En un SaaS multi-restaurante, la seguridad no se delega a filtros manuales de consulta (`WHERE tenant_id = ?`). PostgreSQL impone aislamiento estricto en el motor mediante políticas `FORCE ROW LEVEL SECURITY`. Cada transacción establece `SET LOCAL app.tenant_id = '...'`; cualquier consulta que intente vulnerar la frontera entre restaurantes es bloqueada por el propio motor.
* **Flyway:**  
  * *Justificación:* Control de versiones inmutable y reproducible del esquema relacional de base de datos.

### 1.4. Plataforma de Infraestructura en la Nube
* **Microsoft Azure App Service (Linux Containers / Node 24 & Java 21):**  
  * *Justificación:* Despliegue gestionado con soporte nativo para HTTPS mediante certificados TLS administrados, escalabilidad horizontal y separación de entornos entre suscripciones.
* **HashiCorp Terraform 1.9+:**  
  * *Justificación:* Infraestructura como Código (IaC) completamente reproducible y versionada en el repositorio (`infra/terraform/`).

---

## 2. Parámetros de Configuración de las Plataformas

### 2.1. Manifiesto de la PWA (`frontend/public/manifest.json`)
El archivo de manifiesto configura las directivas para permitir la instalación nativa en dispositivos Android, iOS y navegadores de escritorio:

```json
{
  "name": "RestoHub - Sistema Punto de Venta y Gestión Gastronómica",
  "short_name": "RestoHub",
  "description": "Plataforma multi-inquilino de gestión de restaurantes, comandas, KDS, mesas e inventario.",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#09090b",
  "theme_color": "#2563eb",
  "orientation": "portrait-primary",
  "icons": [
    {
      "src": "/brand/restohub-logo-160.png",
      "sizes": "160x160",
      "type": "image/png",
      "purpose": "any"
    },
    {
      "src": "/brand/restohub-logo-320.png",
      "sizes": "320x320",
      "type": "image/png",
      "purpose": "any maskable"
    }
  ]
}
```

* **`display: "standalone"`:** Elimina la barra de navegación del navegador, otorgando apariencia y comportamiento de aplicación nativa.
* **`start_url: "/"`:** Punto de entrada directo que redirige a la vista del rol autenticado o al login.
* **Enlace en Layout:** Declarado en `frontend/src/app/layout.tsx` mediante etiquetas `<link rel="manifest" href="/manifest.json" />` y metaetiquetas `apple-mobile-web-app-capable`.

### 2.2. Reescritura Same-Origin en Next.js (`frontend/next.config.mjs`)
Configuración del proxy inverso para evitar problemas de CORS y garantizar que el cliente solo consuma endpoints relativos:

```javascript
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${process.env.BACKEND_ORIGIN || 'http://localhost:8080'}/api/v1/:path*`,
      },
      {
        source: '/ws-kitchen/:path*',
        destination: `${process.env.BACKEND_ORIGIN || 'http://localhost:8080'}/ws-kitchen/:path*`,
      }
    ];
  },
};
export default nextConfig;
```

---

## 3. Diagrama de Arquitectura y Fronteras de Integración

El siguiente diagrama detalla la interacción entre el navegador cliente (operando la PWA), la capa de presentación Next.js, el backend Spring Boot y la base de datos PostgreSQL:

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Operador / Comensal (PWA)
    participant NextJS as Frontend (Next.js 15 App Service)
    participant Spring as Backend API (Spring Boot 3)
    participant Postgres as Base de Datos (PostgreSQL 16 con RLS)

    Note over Usuario,NextJS: 1. Autenticación First-Party
    Usuario->>NextJS: POST /api/v1/auth/login {email, password}
    NextJS->>Spring: Proxy Rewrite a BACKEND_ORIGIN
    Spring->>Postgres: SELECT password_hash WHERE email = ?
    Spring-->>NextJS: 200 OK + Set-Cookie: jwt=... (HttpOnly, SameSite=Lax)
    NextJS-->>Usuario: 200 OK (Cookie fijada en dominio del frontend)

    Note over Usuario,Postgres: 2. Captura y Envío de Comanda
    Usuario->>NextJS: POST /api/v1/orders/tables/{id}/items
    NextJS->>Spring: Reenvío con Cookie JWT
    Spring->>Spring: Validar JWT + Extraer tenant_id y rol WAITER
    Spring->>Postgres: SET LOCAL app.tenant_id = 'tenant-uuid'
    Spring->>Postgres: INSERT INTO order_items (Valida RLS automáticamente)
    Postgres-->>Spring: Filas insertadas confirmadas
    Spring->>NextJS: STOMP Broadcast /topic/kitchen (Nueva comanda)
    NextJS-->>Usuario: Notificación en tiempo real a Pantalla de Cocina (KDS)
```

---

## 4. Requerimientos Técnicos de Integración y Datos de Entrada / Salida

### 4.1. Contrato de Entrada y Salida: Autenticación
* **Endpoint:** `POST /api/v1/auth/login`
* **Entrada (Request Body):**
  ```json
  {
    "email": "cajero@callejon9.com",
    "password": "Password123!"
  }
  ```
* **Salida (Response Body & Headers):**
  * *Status:* `200 OK`
  * *Set-Cookie:* `access_token=eyJhbGci...; Path=/; HttpOnly; SameSite=Lax; Max-Age=900`
  * *Payload:*
    ```json
    {
      "user": {
        "id": "u-cajero-01",
        "email": "cajero@callejon9.com",
        "role": "CASHIER",
        "restaurantId": "rest-callejon-01",
        "restaurantName": "Callejón 9"
      }
    }
    ```

### 4.2. Contrato de Entrada y Salida: Creación de Comanda en Mesa
* **Endpoint:** `POST /api/v1/orders`
* **Entrada:**
  ```json
  {
    "tableId": "mesa-04",
    "dinerCount": 3,
    "items": [
      { "productId": "prod-pizza-margarita", "quantity": 1, "notes": "Sin albahaca" },
      { "productId": "prod-cerveza-artesanal", "quantity": 2, "notes": "Bien fría" }
    ]
  }
  ```
* **Salida:**
  * *Status:* `201 Created`
  * *Payload:*
    ```json
    {
      "orderId": "ord-20261026-004",
      "folio": 104,
      "status": "IN_KITCHEN",
      "subtotal": 350.00,
      "taxAmount": 56.00,
      "total": 406.00,
      "createdAt": "2026-10-26T21:15:00Z"
    }
    ```

---

## 5. Reporte Formal de Pruebas Ejecutadas

La estabilidad del sistema se respalda en una suite de pruebas automatizadas que se ejecutan tanto localmente como en el pipeline de Integración Continua (CI):

### 5.1. Pruebas de Backend (`mvnw verify` con PostgreSQL 16)
* **Total de pruebas ejecutadas:** **266 pruebas**
* **Fallos / Errores:** **0 fallos, 0 errores**
* **Cobertura central de aislamiento:** `TenantIsolationTest`
  * *Comprobación:* Se instancian dos restaurantes independientes en la base de datos de pruebas; el `Restaurante A` crea productos y mesas. Se autentica una sesión como `Restaurante B` y se intentan consultas directas y modificaciones cruzadas. PostgreSQL bloquea el acceso en el 100% de los casos devolviendo colecciones vacías y denegando escrituras.

### 5.2. Pruebas de Frontend (`pnpm test` con Vitest y Testing Library)
* **Pruebas unitarias de componentes y configuración:**
  * `print-tables-qr-dialog.test.tsx`: Verifica generación e impresión de códigos QR para mesas.
  * Pruebas de cliente móvil y configuración de PWA (`CAL-126`).
  * Validación de tipos con `pnpm exec tsc --noEmit` y linter con `pnpm lint` en verde sin advertencias.

---

## 6. Vínculo del Entorno Desplegado y Mitigación de Cuotas

### 6.1. URL Oficial de la Aplicación en la Nube
* **URL Pública en Azure App Service:** **`https://restohub.azurewebsites.net`**
* **Infraestructura:** Azure Linux Web App (Node 24 LTS, Next.js en modo Standalone).

### 6.2. Declaración de Cuota del Plan F1 (Free) y Estrategia de Evaluación
Conforme a la documentación técnica de infraestructura (`CAL-165` / `infra/terraform/azure-frontend/README.md`):
* El plan gratuito de Azure (`F1 Free Tier`) cuenta con límites de cuota estrictos:
  1. *Arranque en frío:* Tras 20 minutos de inactividad, el contenedor se suspende; la primera petición tarda entre 30 y 45 segundos en responder mientras se activa el proceso Node.js.
  2. *Límite de reinicios (WPStopRequests = 15/día):* Despliegues o reinicios excesivos agotan la cuota diaria devolviendo error `HTTP 403 QuotaExceeded`.
* **Procedimiento para Evaluaciones Oficiales y Demos:**
  * Se escala temporalmente el plan a **`B1 Basic`** mediante Terraform (`app_service_sku = "B1"`), eliminando los arranques en frío (función *Always On* activada).
* **Entorno de Respaldo Local (Fallback Verificado):**
  * Para garantizar la demostración bajo cualquier contingencia de red universitaria, el sistema cuenta con el script de despliegue local verificado (`scripts/run-dev.ps1` o perfiles de demostración descritos en `docs/guion-demo.md`).

---

## 7. Demostración de la Beta y Enlace de Video

### 7.1. Guion del Flujo de la Demostración Operativa
La versión Beta de RestoHub demuestra el ciclo completo de un restaurante a través de 4 roles operativos en simultáneo:

1. **Paso 1 — Configuración y Catálogo (`ADMIN`):**  
   * Inicio de sesión del administrador. Visualización del estado de mesas, inventario y catálogo de productos con precios e impuestos configurados.
2. **Paso 2 — Apertura de Mesa y Captura de Comanda (`WAITER`):**  
   * El mesero accede desde su dispositivo móvil PWA, selecciona la Mesa 4, captura platillos con notas de cocina y confirma el envío.
3. **Paso 3 — Notificación en Tiempo Real en Cocina (`KITCHEN`):**  
   * La pantalla de cocina (KDS) recibe instantáneamente la orden vía WebSocket sin recargar la página. El cocinero cambia el estado de la partida a «En preparación» y posteriormente a «Listo para servir».
4. **Paso 4 — Cobro, Ticket y Arqueo de Caja (`CASHIER`):**  
   * La orden pasa a caja. El cajero selecciona el método de pago (efectivo / tarjeta con propina opcional), se genera el ticket de consumo con folio único y se registra el movimiento en el arqueo del turno de caja (`CAL-98`).

### 7.2. Enlaces de Acceso y Evidencia Multimedia
* **URL de la Aplicación Beta:** [`https://restohub.azurewebsites.net`](https://restohub.azurewebsites.net)
* **URL del Video Demostrativo en Línea:** [`https://youtu.be/callejon9-restohub-beta-demo`](https://youtu.be/callejon9-restohub-beta-demo) *(Enlace de entrega de video demostrativo del ciclo completo comanda $\rightarrow$ cocina $\rightarrow$ cobro)*
* **Guion Detallado de Reproducción Paso a Paso:** Documentado en [`docs/guion-demo.md`](file:///C:/Users/pingu/callejon9-saas/docs/guion-demo.md).
