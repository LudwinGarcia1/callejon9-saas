# Integración de Pasarela de Pagos: Mercado Pago (RestoHub)

> **Módulo:** Pagos y Cobro en Línea · **Estado:** Implementado y Verificado  
> **Flujos soportados:** Cobro en Caja POS y Auto-pago de Comensales por Menú QR en Mesa

---

## 1. Arquitectura de la Solución

La integración de **Mercado Pago** en RestoHub preserva estrictamente el aislamiento multi-tenant mediante PostgreSQL Row Level Security (RLS) y opera desacoplada con soporte para modo Sandbox / Mock y producción en vivo.

```
[Comensal en Mesa / Cajero en POS]
            │
            ▼
[Frontend: Next.js 15] ──(POST /api/v1/.../mercadopago/preference)──► [Spring Boot API]
                                                                             │
                                                              (Crea Preference con metadata:
                                                               tenant_id:order_id:timestamp)
                                                                             │
                                                                             ▼
                                                                  [Mercado Pago Cloud]
                                                                             │
                                                               (Webhook IPN: payment.created)
                                                                             │
                                                                             ▼
[Spring Boot: Webhook Handler] ◄──(POST /api/v1/webhooks/mercadopago)────────┘
   │
   ├─► 1. Extrae identificadores seguros (tenant_id y order_id)
   ├─► 2. Activa TenantContext (set_config 'app.tenant_id')
   └─► 3. Ejecuta CheckoutService:
            - Registra Sale con PaymentMethod.MERCADOPAGO
            - Registra Payment (provider=MERCADOPAGO, status=COMPLETED)
            - Emite Ticket inmutable con folio oficial
            - Marca Order como PAID
            - Libera la mesa (TableService.free)
```

---

## 2. Flujos Implementados

### A. Cobro en Caja POS (`/cashier`)
1. El cajero visualiza la cuenta de la comanda y el desglose de productos y propina.
2. Pulsa el botón **«Cobrar con QR Mercado Pago»**.
3. El sistema despliega un modal con el **código QR dinámico** de Mercado Pago (generado con `qrcode`), el total oficial a cobrar y el enlace directo al checkout.
4. El cajero o el cliente escanea el QR con la app de Mercado Pago.
5. El sistema mantiene un sondeo reactivo en vivo hasta que se confirma el pago; al confirmarse, se cierra automáticamente el modal, emite el ticket y actualiza el saldo de la caja.
6. Cuenta con botón de **«Simular Aprobación (Modo Demo)»** para presentaciones y pruebas sin consumir fondos reales.

### B. Auto-pago del Comensal desde la Mesa (Menú QR `/m/[slug]/[table]`)
1. El comensal ingresa desde su teléfono escaneando el código QR de su mesa física.
2. En la cabecera pulsa el botón **«Cuenta»**.
3. Se despliega el detalle de su consumo en tiempo real, con desglose de platillos y selector interactivo de propina voluntaria (0%, 10%, 15%, 20%).
4. Al pulsar **«Pagar con Mercado Pago»**, se abre el checkout seguro en su celular o se despliega el QR de pago.
5. Al completarse el pago, la mesa se desocupa y el comensal recibe confirmación inmediata en pantalla.

---

## 3. Endpoints Incorporados

| Método | Ruta | Autorización | Propósito |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders/{orderId}/mercadopago/preference` | `CASHIER`, `ADMIN` | Genera preferencia de cobro para comanda en caja |
| `GET` | `/api/v1/orders/{orderId}/payment-status` | `CASHIER`, `ADMIN`, `WAITER` | Consulta estado de pago de una comanda |
| `POST` | `/api/v1/orders/{orderId}/mercadopago/simulate-approval` | `CASHIER`, `ADMIN` | Simula aprobación inmediata (pruebas/demos) |
| `GET` | `/api/v1/public/{slug}/tables/{tableNumber}/order` | Público | Consulta comanda activa de la mesa del comensal |
| `POST` | `/api/v1/public/{slug}/tables/{tableNumber}/mercadopago/preference` | Público | Genera preferencia de auto-pago para comensal en mesa |
| `GET` | `/api/v1/public/{slug}/tables/{tableNumber}/payment-status` | Público | Consulta estado de pago de la mesa |
| `POST` | `/api/v1/public/{slug}/tables/{tableNumber}/mercadopago/simulate-approval` | Público | Simula aprobación en menú comensal (modo demo) |
| `POST` | `/api/v1/webhooks/mercadopago` | Público (Firma IPN) | Receptor oficial de notificaciones IPN de Mercado Pago |

---

## 4. Configuración y Credenciales

En `application.yml` (y variables de entorno en producción):

```yaml
app:
  payment:
    mercadopago:
      access-token: ${MERCADOPAGO_ACCESS_TOKEN:}
      public-key: ${MERCADOPAGO_PUBLIC_KEY:}
      webhook-secret: ${MERCADOPAGO_WEBHOOK_SECRET:}
      mock-enabled: ${MERCADOPAGO_MOCK_ENABLED:true}
      base-url: ${MERCADOPAGO_BASE_URL:https://api.mercadopago.com}
      frontend-base-url: ${APP_FRONTEND_BASE_URL:http://localhost:3000}
      backend-base-url: ${APP_BACKEND_BASE_URL:http://localhost:8080}
```

* Si `MERCADOPAGO_ACCESS_TOKEN` no está configurado o `MERCADOPAGO_MOCK_ENABLED=true`, el sistema opera automáticamente en **modo mock autónomo**, permitiendo pruebas y demos fluidas.
* Para producción, basta proporcionar el `MERCADOPAGO_ACCESS_TOKEN` de producción o sandbox de la cuenta de Mercado Pago.
