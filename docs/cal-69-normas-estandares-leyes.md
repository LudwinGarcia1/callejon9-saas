# CAL-69 — Normas, Estándares, Leyes y Regulaciones Aplicables

> **Asignatura:** Integradora · **Fecha oficial:** 4 de noviembre de 2026 · **Fecha interna de cierre:** 28 de octubre de 2026\
> **Docentes evaluadores:** Dr. Florencio Barrera Gutiérrez y LA Verónica Hernández González\
> **Proyecto:** RestoHub (Plataforma SaaS Multi-restaurante) · **Equipo:** Callejón 19\

---

## 1. Introducción y Justificación de Alcance

El desarrollo y comercialización de una plataforma tecnológica multi-inquilino (*multi-tenant*) bajo el modelo de Software as a Service (SaaS) como **RestoHub** exige un análisis normativo multidisciplinario que trascienda las buenas prácticas de ingeniería de software convencionales. Al prestar servicios operativos a restaurantes, gestionar cuentas de personal, registrar pedidos en mesas, procesar cobros, emitir comprobantes y recaudar métricas de tráfico y consumo, el sistema entra en contacto directo con marcos regulatorios en materia de:

1. **Protección de datos personales y privacidad** (tratamiento de identidad de empleados, comensales y operadores).
2. **Derecho fiscal, facturación electrónica y medios de pago** (emisión de comprobantes fiscales digitales y recaudación de suscripciones).
3. **Protección al consumidor y comercio electrónico** (transparencia de precios, cobros y contratación digital).
4. **Propiedad intelectual, derechos de autor y gobernanza técnica** (titularidad del código y divulgación coordinada de vulnerabilidades).
5. **Estándares internacionales de calidad, seguridad y accesibilidad de software** (ISO/IEC, OWASP, W3C/WCAG).

Este documento no presenta un catálogo genérico o abstracto de normas; desglosa exclusivamente la regulación nacional (México) e internacional que **aplica directamente al modelo operativo y técnico de RestoHub**, estableciendo para cada disposición la obligación exacta impuesta al equipo de ingeniería y las medidas técnicas implementadas en la arquitectura.

---

## 2. Marco Legal de Protección de Datos Personales y Privacidad (México)

### 2.1. Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP) y su Reglamento

* **Autoridad emisora:** Congreso de la Unión / Instituto Nacional de Transparencia, Acceso a la Información y Protección de Datos Personales (INAI).
* **Fundamento:** Artículos 6º y 16 de la Constitución Política de los Estados Unidos Mexicanos; LFPDPPP (D.O.F. 05-07-2010) y su Reglamento (D.O.F. 21-12-2011).

#### Datos Personales Recabados por RestoHub
El sistema recopila, almacena y procesa categorías específicas de datos en dos niveles:

1. **Datos de personal y operadores de restaurantes (`ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`):**
   - Nombre completo y alias operativo.
   - Correo electrónico institucional o personal (utilizado como identificador de inicio de sesión).
   - Credenciales de autenticación: Contraseña cifrada de forma unidireccional con algoritmo de derivación de claves `BCrypt` (factor de costo 10) con *salt* aleatorio.
   - Rol y asignación de permisos de sucursal.
2. **Datos de tráfico, seguridad y auditoría (Trazabilidad técnica):**
   - Direcciones IP de origen (`IPv4`/`IPv6`) en cada petición de inicio de sesión, registro y operaciones de caja.
   - Cadena de agente de usuario (*User-Agent*) del navegador o dispositivo móvil.
   - Marcas temporales (*timestamps*) de eventos de seguridad (bloqueos por fuerza bruta, revocación de tokens).
3. **Datos de comensales y clientes finales:**
   - Nombre o apodo para llamado a mesa (en auto-pedido QR).
   - Datos fiscales para emisión opcional de comprobante: Registro Federal de Contribuyentes (RFC), Razón Social, Régimen Fiscal y Código Postal del domicilio fiscal.
   - Correo electrónico para el envío digital del ticket o factura.

#### Doble Rol Jurídico de RestoHub: Responsable vs. Encargado
En términos de los artículos 3º y 49 del Reglamento de la LFPDPPP:
* **RestoHub como Encargado:** Frente a los datos de los empleados del restaurante y los clientes que ordenan en el establecimiento, **el Restaurante es el Responsable**, y **RestoHub actúa como Encargado**, prestando la infraestructura de base de datos bajo estrictos límites de aislamiento.
* **RestoHub como Responsable:** Frente a los directivos del restaurante que contratan la suscripción SaaS, RestoHub es el **Responsable directo** de sus datos de contacto y facturación mercantil.

#### Obligaciones Concretas para el Proyecto
| Disposición Legal | Principio LFPDPPP | Medida Técnica Implementada en RestoHub |
|---|---|---|
| **Art. 8, 15 y 16 LFPDPPP** | Consentimiento e Información | Presentación obligatoria del **Aviso de Privacidad Integral** en el portal de registro (`/signup`) y del **Aviso de Privacidad Simplificado** en el pie de página de la PWA de auto-pedido QR. |
| **Art. 19 y 20 LFPDPPP** | Seguridad y Confidencialidad | Garantía de aislamiento entre restaurantes mediante **Row Level Security (RLS)** forzado en PostgreSQL a nivel motor (`FORCE ROW LEVEL SECURITY`), impidiendo fugas de datos entre inquilinos. Transmisión cifrada obligatoria vía HTTPS/TLS 1.3. |
| **Art. 13 LFPDPPP** | Proporcionalidad | No se solicitan datos personales sensibles (datos biométricos, médicos o religiosos). Los meseros y cocineros no requieren registrar domicilio ni CURP para operar la comanda. |
| **Art. 28 a 35 LFPDPPP** | Derechos ARCO (Acceso, Rectificación, Cancelación y Oposición) | Mecanismo de **baja lógica** (`is_active = false` o `status = 'INACTIVE'`). Para preservar la integridad referencial y las auditorías contables de tickets pasados, los datos no se borran físicamente mediante `DELETE`, pero se anonimizan o inactivan a solicitud del titular. |

---

## 3. Marco Fiscal, Facturación Electrónica y Medios de Pago

### 3.1. Código Fiscal de la Federación (CFF) y Resolución Miscelánea Fiscal (RMF) — CFDI 4.0

* **Autoridad reguladora:** Servicio de Administración Tributaria (SAT) / Secretaría de Hacienda y Crédito Público (SHCP).
* **Marco aplicable:** Artículos 29 y 29-A del Código Fiscal de la Federación; Anexo 20 de la RMF vigente.

#### Régimen de Comprobantes de Venta en Restaurantes
Todo establecimiento gastronómico en México debe emitir comprobantes de las operaciones que realiza con el público:
1. **Comprobante simplificado / Ticket de consumo:** Documento no fiscal entregado de inmediato al comensal (impreso en papel térmico de 80mm o desplegado digitalmente) que detalla el consumo, precio unitario, subtotal, impuestos trasladados (IVA 16%) y forma de pago.
2. **Comprobante Fiscal Digital por Internet (CFDI 4.0):** Cuando el comensal solicita factura formal, el sistema debe permitir capturar RFC, nombre o razón social registrada en el SAT, código postal del domicilio fiscal y uso del CFDI (ej. `G03 - Gastos en general`), validando la clave de producto/servicio (`90101501 - Restaurantes`).
3. **Factura Global Diaria o Periódica:** Por las ventas de comensales que no solicitaron factura individual, el restaurante debe timbrar una factura global al público en general utilizando el RFC genérico `XAXX010101000`.

#### Obligaciones Concretas para el Proyecto
* **Fórmula de redondeo y consistencia aritmética:** El redondeo de partidas y totales debe seguir el estándar del Anexo 20 (truncamiento y redondeo aritmético a dos decimales, evitando discrepancias de centavos entre partidas sumadas y el total facturado).
* **Desglose de impuestos:** En la base de datos de RestoHub, cada orden y ticket desglosa explícitamente:
  - `subtotal` (monto antes de impuestos).
  - `tax_amount` (cálculo formal de IVA).
  - `total` (importe total pagado).
* **Inmutabilidad de ventas cobradas:** Una vez que un ticket entra en estado `PAID`, no puede ser modificado ni sobreescrito. Las cancelaciones exigen un movimiento de anulación explícito con motivo y reverso de corte de caja (`CAL-98`).
* **Integración con Proveedor Autorizado de Certificación (PAC):** El SaaS debe disponer de la interfaz de salida (JSON estructurado) para comunicarse vía API con un PAC autorizado para el timbrado del XML y generación de la cadena original y sello digital del SAT.

---

### 3.2. Medios de Pago, Ley Fintech y Cumplimiento PCI-DSS

* **Regulaciones:** Ley para Regular las Instituciones de Tecnología Financiera (**Ley Fintech**); Estándar de Seguridad de Datos para la Industria de Tarjetas de Pago (**PCI-DSS v4.0**).
* **Operación en RestoHub:** Pasarela de pago digital para suscripciones del SaaS y cobro en mesa con tarjeta mediante integración con **Mercado Pago**.

#### Obligaciones Concretas para el Proyecto
* **Exclusión de datos de tarjeta en servidores propios (Cero almacenamiento PCI):**
  - **Invariante técnica:** RestoHub **nunca** recibe, transmite ni almacena números de tarjeta completos (PAN), fechas de expiración ni códigos de seguridad (CVV/CVC) en su backend ni en su base de datos PostgreSQL.
  - El procesamiento se delega al 100% mediante componentes SDK oficiales (*Secure Fields* / *Tokenization Checkout*) de Mercado Pago o terminales punto de venta integradas (Point).
  - RestoHub únicamente almacena el identificador de transacción devuelto por la pasarela (`payment_id`), el estado del cobro (`approved`, `rejected`), los últimos 4 dígitos y la marca de la tarjeta con fines de visualización en el ticket.
* **Trazabilidad de pagos divididos y propinas:** Conforme a la legislación bancaria, los cobros divididos (`CAL-97`) deben emitir identificadores individuales de cargo para que el tarjetahabiente reciba el comprobante bancario correspondiente a su aportación.

---

### 3.3. Ley Federal de Protección al Consumidor (LFPC) y Normatividad PROFECO

* **Autoridad:** Procuraduría Federal del Consumidor (PROFECO).
* **Marco aplicable:** Artículos 7, 7 BIS, 10 y 58 de la Ley Federal de Protección al Consumidor.

#### Obligaciones Concretas para el Proyecto
1. **Prohibición de cobro obligatorio de propina:**
   - La ley mexicana prohíbe terminantemente incluir la propina o gratificación de forma automática y obligatoria en el total de la cuenta.
   - **Diseño del sistema en RestoHub:** La interfaz de cobro para el cajero (`CASHIER`) y la pantalla de pago de comensales deben mantener la propina como un concepto **opcional, desglosado y editable** (sugerencias del 10%, 15%, manual o 0%), sin impedir el cierre de la comanda si el comensal decide no otorgarla.
2. **Precios transparentes y totales exhibidos:**
   - Todos los precios mostrados en el catálogo digital y menú QR deben ser los precios finales con impuestos incluidos, sin cargos ocultos por servicio o uso de mesa.

---

## 4. Gobernanza del Código, Licenciamiento y Seguridad Técnica

### 4.1. Ley Federal del Derecho de Autor (LFDA) y Licenciamiento Propietario

* **Marco legal:** Artículos 13 (fracción XI), 101 a 114 de la Ley Federal del Derecho de Autor (México).
* **Régimen de Propiedad:** **Software Propietario — Todos los derechos reservados.** Titularidad exclusiva de **Callejón 19**.

#### Obligaciones Concretas para el Proyecto
* **Protección del código fuente y esquemas:** El código desarrollado en Spring Boot, Next.js, scripts de infraestructura Terraform y migraciones Flyway constituye una obra intelectual protegida. Queda prohibida la redistribución, copia o explotación sin autorización previa y por escrito.
* **Separación de dependencias de terceros:** El uso de bibliotecas de código abierto (Spring Framework bajo Apache 2.0, Next.js bajo MIT, Tailwind CSS bajo MIT, PostgreSQL JDBC bajo BSD-2) se realiza respetando sus términos de licencia sin generar contaminación de código ni comprometer la titularidad propietaria del sistema RestoHub.
* **Aviso de Copyright:** Todo archivo fuente y repositorio debe incluir el encabezado formal de reserva de derechos conforme al archivo `LICENSE`.

### 4.2. Política de Seguridad y Divulgación Coordinada (`SECURITY.md`)

* **Estándar:** RFC 9116 (*A File Format to Aid in Security Vulnerability Reporting*).
* **Canal oficial:** Pestaña **Security** de GitHub mediante *Private Vulnerability Reporting*.

#### Obligaciones Concretas para el Proyecto
* **Prohibición de divulgación pública de vulnerabilidades:** Ningún integrante o colaborador debe publicar issues abiertos ni commits públicos que detallen fallos de seguridad o debilidades de aislamiento RLS antes de que exista un parche probado y liberado.
* **SLA de remediación técnica:**
  - Fallos de severidad crítica (rotura de aislamiento multi-tenant, acceso no autenticado a datos de otro restaurante): Máximo **7 días naturales**.
  - Fallos de severidad alta: Máximo **30 días naturales**.

---

## 5. Estándares Técnicos Internacionales de Calidad y Arquitectura

### 5.1. ISO/IEC 25010:2011 — Modelo de Calidad del Producto Software

El sistema RestoHub adopta las características del modelo de calidad ISO/IEC 25010 como directrices de diseño y verificación:

| Característica de Calidad | Subcaracterística | Implementación Concreta en RestoHub |
|---|---|---|
| **Adecuación Funcional** | Completitud y Exactitud | Flujo operativo integral sin fisuras: apertura de mesa, captura de comanda, notificación en cocina vía STOMP WebSocket, cobro, arqueo de caja y analítica. |
| **Fiabilidad** | Tolerancia a fallos y Recuperabilidad | Persistencia ACID transaccional; reintentos automáticos en frontend vía React Query; estado recuperable en cortes de energía locales. |
| **Seguridad** | Confidencialidad e Integridad | Aislamiento forzado por RLS en PostgreSQL; contraseñas con hash BCrypt; cookies seguras (`HttpOnly`, `SameSite=Lax`, `Secure`). |
| **Eficiencia de Desempeño** | Comportamiento temporal | Reescritura *first-party* en Next.js para eliminar cabeceras CORS pesadas; comunicación en tiempo real ligera con STOMP sobre WebSockets. |
| **Mantenibilidad** | Modularidad y Testabilidad | Separación limpia entre capas (Controller, Service, Repository); suite de pruebas unitarias (JUnit 5, Mockito, Vitest) y pruebas de integración (Testcontainers). |

### 5.2. OWASP Top 10 API Security (2023)

El backend REST y la capa de servicios de RestoHub implementan contramedidas contra los principales riesgos de seguridad en APIs:

1. **API1:2023 — Broken Object Level Authorization (BOLA):**\
   * *Riesgo:* Que un usuario de un restaurante acceda a órdenes o productos de otro restaurante manipulando el `id` numérico o UUID en la URL.\
   * *Mitigación RestoHub:* Invariante crítica del proyecto. La base de datos aplica RLS forzado donde cada sentencia SQL se ejecuta bajo el contexto de `app.tenant_id`. Si un atacante solicita un recurso que no pertenece a su inquilino, PostgreSQL devuelve un conjunto vacío (0 filas) o rechaza la inserción, haciendo imposible el acceso cruzado.
2. **API2:2023 — Broken Authentication:**\
   * *Mitigación RestoHub:* Autenticación mediante tokens JWT firmados con clave simétrica robusta (`HMAC-SHA256`). Los tokens se almacenan en cookies protegidas inaccesibles para scripts maliciosos de JavaScript (`HttpOnly`).
3. **API4:2023 — Unrestricted Resource Consumption:**\
   * *Mitigación RestoHub:* Límite de tasa (*rate limit*) estricto en los endpoints de inicio de sesión (`/api/v1/auth/login`) y registro (`/api/v1/auth/signup`) para mitigar ataques de fuerza bruta y saturación de base de datos.
4. **API5:2023 — Broken Function Level Authorization (BFLA):**\
   * *Mitigación RestoHub:* Matriz estricta de autorización por roles (`@PreAuthorize("hasRole('ADMIN')")`). Un mesero (`WAITER`) tiene restringido a nivel método el acceso a reportes de corte de caja o configuración de sucursales.

### 5.3. WCAG 2.1 — Directrices de Accesibilidad para el Contenido Web (Nivel AA)

* **Autoridad:** World Wide Web Consortium (W3C).
* **Aplicación en RestoHub:** PWA de comensales (menú digital) y paneles de visualización de comandas en cocina.
* **Medidas técnicas:**
  - Contraste de color mínimo de 4.5:1 en elementos tipográficos sobre fondo para garantizar legibilidad en ambientes de cocina con alta iluminación o bajo luz solar directa.
  - Soporte de navegación por teclado en formularios administrativos y pedidos.
  - Etiquetas descriptivas (`aria-label`) en botones de acción rápida, íconos de estado de platillos y alertas de semáforo de cocina.

---

## 6. Matriz Consolidada de Obligaciones Técnicas

| Marco Normativo / Estándar | Tipo de Norma | Ámbito / Objeto | Obligación Concreta en el Código o Proceso de RestoHub |
|---|---|---|---|
| **LFPDPPP y Reglamento** | Ley Federal | Privacidad y Datos | Publicar Aviso de Privacidad en login/PWA; implementar borrado lógico/anonimización para derechos ARCO; cifrado de datos en tránsito (TLS 1.3). |
| **CFF y RMF (Anexo 20)** | Código / Resolución | Fiscal y Facturación | Desglose formal de subtotal e IVA; inmutabilidad de tickets cobrados; validación aritmética de redondeo a 2 decimales; preparación para timbrado CFDI 4.0. |
| **LFPC (PROFECO)** | Ley Federal | Derechos del Consumidor | Propina estrictamente opcional y desglosada; precios finales obligatorios en menú QR sin cargos ocultos de servicio. |
| **PCI-DSS v4.0** | Estándar de la Industria | Medios de Pago | Delegación completa de procesamiento de tarjeta a Mercado Pago; prohibición absoluta de almacenar PAN o CVV en servidores propios. |
| **LFDA** | Ley Federal | Propiedad Intelectual | Declaración de titularidad de software propietario a favor de Callejón 19; protección de código fuente, algoritmos y esquemas. |
| **OWASP API Security** | Estándar de Seguridad | Arquitectura de Software | Aislamiento BOLA mediante PostgreSQL RLS con `tenant_id`; protección contra fuerza bruta con rate limiting; control de acceso basado en roles (RBAC). |
| **ISO/IEC 25010** | Estándar Internacional | Calidad de Software | Pruebas automatizadas unitarias y de integración en pipeline de CI; arquitectura modular desacoplada entre frontend Next.js y backend Spring Boot. |
| **W3C WCAG 2.1 AA** | Estándar de Accesibilidad | Experiencia de Usuario | Contraste de colores accesible en interfaz PWA y pantalla de cocina; semántica accesible en componentes shadcn/ui. |

---

## 7. Declaración Formal de Huecos Legales y Técnicos Pendientes

En estricto apego al Criterio de Aceptación 5 y a la Definition of Done de **CAL-69**, se identifican las decisiones y aspectos regulatorios que requieren asesoría legal o contable especializada externa, junto con el responsable designado dentro del equipo para su seguimiento:

| Identificador | Asunto / Hueco Pendiente | Causa de la Pendencia | Impacto Técnico / Operativo | Responsable de Seguimiento | Fecha Límite de Resolución |
|---|---|---|---|---|---|
| **HUECO-LEG-01** | **Elección y contratación del PAC (Proveedor Autorizado de Certificación)** | Requiere cotización mercantil, alta fiscal como empresa y contratación de paquete de timbres fiscales para emisión de CFDI 4.0 directo. | Bloquea la implementación técnica de los endpoints de timbrado en los issues `CAL-101` y `CAL-102`. | **Octavio Duarte** (PM / Representante) | Antes del arranque del ciclo de facturación (Diciembre 2026) |
| **HUECO-LEG-02** | **Términos y Condiciones del Servicio SaaS (B2B)** | Requiere revisión por abogado corporativo para delimitar acuerdos de nivel de servicio (SLA), exclusión de responsabilidad por lucro cesante ante caídas de internet y cláusulas de rescisión. | Necesario para la comercialización abierta de planes de suscripción a restaurantes ajenos al grupo inicial. | **Octavio Duarte** | Previo al lanzamiento comercial |
| **HUECO-LEG-03** | **Validación Contable del Esquema de Factura Global del Restaurante** | Requiere que un contador fiscalista dictamine si el corte diario de caja del SaaS cumple plenamente con los requisitos de la declaración periódica de ventas al público en general. | Define la estructura exacta del reporte de ventas acumuladas por corte de caja (`CAL-98` / `CAL-102`). | **Valeria Mercado** (Líder del módulo de Caja y Ventas) | 15 de Noviembre de 2026 |
| **HUECO-LEG-04** | **Contrato de Adhesión registrado ante PROFECO** | La PROFECO exige en ciertos giros comerciales el registro de contratos de adhesión de servicios digitales. | Determinar si el contrato SaaS entre Callejón 19 y los restaurantes suscriptores requiere registro formal obligatorio ante la Subprocuraduría de Servicios. | **Octavio Duarte** | Fase previa a la incorporación de clientes externos |
