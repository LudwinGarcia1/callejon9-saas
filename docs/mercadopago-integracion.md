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
                                                               tenant_id:order_id:tip:timestamp)
                                                                             │
                                                                             ▼
                                                                  [Mercado Pago Cloud]
                                                                             │
                                                               (Webhook IPN: payment.created
                                                                con headers x-signature y x-request-id)
                                                                             │
                                                                             ▼
[Spring Boot: Webhook Handler] ◄──(POST /api/v1/webhooks/mercadopago)────────┘
   │
   ├─► 1. Valida firma HMAC-SHA256 (x-signature con webhookSecret en prod)
   ├─► 2. En producción, consulta la API oficial de Mercado Pago para verificar status=approved
   ├─► 3. Extrae identificadores seguros (tenantId, orderId, tipPercent)
   ├─► 4. Activa TenantContext y abre transacción RLS (set_config 'app.tenant_id')
   └─► 5. Ejecuta CheckoutService:
            - Registra Sale con PaymentMethod.MERCADOPAGO y propina preservada
            - Registra Payment (provider=MERCADOPAGO, provider_payment_id, status=COMPLETED)
            - Emite Ticket inmutable con folio oficial
            - Marca Order como PAID (candado PESSIMISTIC_WRITE findForCheckout)
            - Libera la mesa (TableService.free)
```

---

## 2. Flujos Implementados

### A. Cobro en Caja POS (`/cashier`)
1. El cajero visualiza la cuenta de la comanda y el desglose de productos y propina.
2. Pulsa el botón **«Cobrar con QR Mercado Pago»**.
3. El sistema despliega un modal con el **código QR dinámico** de Mercado Pago (generado con `qrcode`), el total oficial a cobrar y el enlace directo al checkout.
4. El cajero o el cliente escanea el QR con la app de Mercado Pago o abre el enlace directo.
5. El sistema mantiene un sondeo reactivo en vivo hasta que se confirma el pago; al confirmarse, se cierra automáticamente el modal, emite el ticket y actualiza el saldo de la caja.
6. Cuenta con botón de **«Simular Aprobación (Modo Demo)»** para presentaciones y pruebas sin consumir fondos reales.

### B. Auto-pago del Comensal desde la Mesa (Menú QR `/m/[slug]/[table]`)
1. El comensal ingresa desde su teléfono escaneando el código QR de su mesa física.
2. En la cabecera pulsa el botón **«Cuenta»**.
3. Se despliega el detalle de su consumo en tiempo real, con desglose de platillos y selector interactivo de propina voluntaria (0%, 10%, 15%, 20%).
4. Al pulsar **«Pagar con Mercado Pago»**, se abre el checkout seguro en su celular o se despliega el QR de pago.
5. Al completarse el pago, la mesa se desocupa y el comensal recibe confirmación inmediata en pantalla.

### C. Pasarela Mock Interactiva en Sandbox (`/payment/mercadopago/mock`)
1. En modo mock / desarrollo autónomo, al escanear el QR o pulsar **«Abrir Checkout en nueva pestaña»**, el navegador dirige a `/payment/mercadopago/mock`.
2. Presenta una interfaz oficial estilizada de Mercado Pago Sandbox con el desglose exacto de la comanda, selección de método simulado (Tarjeta Visa Demo o Saldo en cuenta) y botón para aprobar el pago.
3. Al aprobar, dispara la notificación de pago completando el circuito de extremo a extremo sin dependencias externas.

---

## 3. Endpoints Incorporados

| Método | Ruta | Autorización | Propósito |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders/{orderId}/mercadopago/preference` | `CASHIER`, `ADMIN` | Genera preferencia de cobro para comanda en caja |
| `GET` | `/api/v1/orders/{orderId}/payment-status` | `CASHIER`, `ADMIN`, `WAITER` | Consulta estado de pago de una comanda |
| `POST` | `/api/v1/orders/{orderId}/mercadopago/sync` | `CASHIER`, `ADMIN` | Sincroniza y concilia pago contra API de Mercado Pago en vivo |
| `POST` | `/api/v1/orders/{orderId}/mercadopago/simulate-approval` | `CASHIER`, `ADMIN` | Simula aprobación inmediata (pruebas/demos) |
| `GET` | `/api/v1/public/{slug}/tables/{tableNumber}/order` | Público | Consulta comanda activa de la mesa del comensal |
| `POST` | `/api/v1/public/{slug}/tables/{tableNumber}/mercadopago/preference` | Público | Genera preferencia de auto-pago para comensal en mesa |
| `GET` | `/api/v1/public/{slug}/tables/{tableNumber}/payment-status` | Público | Consulta estado de pago de la mesa |
| `POST` | `/api/v1/public/{slug}/tables/{tableNumber}/mercadopago/sync` | Público | Sincroniza y concilia pago de mesa contra API oficial MP |
| `POST` | `/api/v1/public/{slug}/tables/{tableNumber}/mercadopago/simulate-approval` | Público | Simula aprobación en menú comensal (modo demo) |
| `POST` | `/api/v1/webhooks/mercadopago` | Público (Firma IPN) | Receptor oficial de notificaciones IPN de Mercado Pago |
| `GET` | `/payment/mercadopago/mock` | Público (Frontend) | Interfaz visual del checkout sandbox interactivo |

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
      sandbox: ${MERCADOPAGO_SANDBOX:true}
      base-url: ${MERCADOPAGO_BASE_URL:https://api.mercadopago.com}
      frontend-base-url: ${APP_FRONTEND_BASE_URL:http://localhost:3000}
      backend-base-url: ${APP_BACKEND_BASE_URL:http://localhost:8080}
```

* **Modo Mock / Simulación:** Si `MERCADOPAGO_ACCESS_TOKEN` no está configurado o `MERCADOPAGO_MOCK_ENABLED=true`, el sistema opera automáticamente en modo simulado para desarrollo sin dependencias de red.
* **Modo Producción Real:** Configurado mediante archivo local `.env` (ignorado en Git):
  ```env
  MERCADOPAGO_ACCESS_TOKEN=APP_USR-4039383284455548-112721-5932cbdd9ee7da34e755a06c6708d726-1203560041
  MERCADOPAGO_PUBLIC_KEY=APP_USR-7613ff15-8420-429a-8a1e-c27c7d4b9512
  MERCADOPAGO_MOCK_ENABLED=false
  MERCADOPAGO_SANDBOX=false
  ```
  Tanto `scripts/run-dev.ps1` como `.vscode/launch.json` (`envFile`) cargan automáticamente las variables de `.env`.

---

## 5. Validación en Producción (Prueba Real Ejecutada)

La pasarela fue verificada de extremo a extremo con dinero bancario real:
- **Transacción Mercado Pago:** Operación `#190980609107` aprobada en Checkout Pro.
- **Cargo Bancario:** $10.00 MXN a Nubank Mastercard (terminación 3959) a nombre de `MERPAGO*RESTOHUB`.
- **Acreditación:** Cuenta receptora de negocio `1203560041` recibió los fondos netos con opción de reembolso directo («Devolver dinero»).
- **Conciliación Callejón 9:** Comanda `ORD-261128024314` cerrada en estado `PAID`, ticket fiscal `TCK-261128031357` emitido y Mesa 1 liberada automáticamente bajo PostgreSQL RLS.

> [!NOTE] Regla Antifraude de Auto-pago (Self-Payment)
> Por normativa de la CNBV y reglas de pasarela Visa/Mastercard, un vendedor no puede realizar pagos hacia su propia cuenta usando sus propias tarjetas guardadas. En producción, las pruebas deben realizarse como cliente externo (tarjeta o cuenta bancaria no vinculada al titular del negocio cobrador).
