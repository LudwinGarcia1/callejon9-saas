# CAL-69 — Normas, Estándares, Leyes y Regulaciones Aplicables

> **Asignatura:** Integradora · **Fecha oficial:** 4 de noviembre de 2026 · **Fecha interna de cierre:** 28 de octubre de 2026\
> **Docentes evaluadores:** Dr. Florencio Barrera Gutiérrez y LA Verónica Hernández González\
> **Proyecto:** RestoHub (Plataforma SaaS Multi-restaurante) · **Equipo:** Callejón 19\
> **Versión:** 2.0 (Subsanación de observaciones de revisión independiente en Linear)

---

## 1. Introducción y Justificación de Alcance

El desarrollo y comercialización de una plataforma tecnológica multi-inquilino (*multi-tenant*) bajo el modelo de Software as a Service (SaaS) como **RestoHub** exige un análisis normativo multidisciplinario que trascienda las buenas prácticas de ingeniería de software convencionales. Al prestar servicios operativos a restaurantes, gestionar cuentas de personal, registrar pedidos en mesas, procesar cobros, emitir comprobantes y recaudar métricas de tráfico y consumo, el sistema entra en contacto directo con marcos regulatorios en materia de:

1. **Protección de datos personales y privacidad** (tratamiento de identidad de empleados, comensales y operadores).
2. **Derecho fiscal, facturación electrónica y medios de pago** (emisión de comprobantes de consumo, régimen de propinas y recaudación de suscripciones).
3. **Protección al consumidor y comercio electrónico** (transparencia de precios, cobros y contratación digital).
4. **Propiedad intelectual, derechos de autor y gobernanza técnica** (titularidad del código y divulgación coordinada de vulnerabilidades).
5. **Estándares internacionales de calidad, seguridad y accesibilidad de software** (ISO/IEC, OWASP, W3C/WCAG, NOMs mexicanas).

Este documento no presenta un catálogo genérico o abstracto de normas; desglosa exclusivamente la regulación nacional (México) e internacional que **aplica directamente al modelo operativo y técnico de RestoHub**, estableciendo para cada disposición la obligación exacta impuesta al equipo de ingeniería, distinguiendo con honestidad técnica entre las **medidas implementadas** en el código actual y las **medidas planeadas** con su correspondiente ticket de seguimiento.

---

## 2. Marco Legal de Protección de Datos Personales y Privacidad (México)

### 2.1. Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP) y Marco Tutelar Vigente

* **Autoridad reguladora:** Congreso de la Unión / **Secretaría Anticorrupción y Buen Gobierno** (órgano del Ejecutivo Federal que asumió las funciones tutelares de protección de datos personales y transparencia tras la extinción del INAI conforme a la reforma constitucional y legal del marco de datos personales y administración pública federal publicada en el DOF).
* **Fundamento legal:** Artículos 6º y 16 de la Constitución Política de los Estados Unidos Mexicanos; **Ley Federal de Protección de Datos Personales en Posesión de los Particulares (publicada en el D.O.F. el 20 de marzo de 2025)** y su normatividad reglamentaria y lineamientos vigentes.

#### Datos Personales Recabados por RestoHub
El sistema recopila, almacena y procesa categorías específicas de datos personales:

1. **Datos de personal y operadores de restaurantes (`ADMIN`, `WAITER`, `KITCHEN`, `CASHIER`):**
   - Nombre completo y correo electrónico (utilizado como identificador de inicio de sesión).
   - Credenciales de autenticación: Contraseña cifrada de forma unidireccional con algoritmo de derivación de claves `BCrypt` (factor de costo 10) con *salt* aleatorio.
   - Rol y asignación de permisos de sucursal.
2. **Datos de tráfico y seguridad:**
   - La dirección IP de origen (`IPv4`/`IPv6`) se procesa en memoria mediante la clase `ClientIp` para calcular ventanas de límite de tasa (*rate limit*) ante intentos de autenticación (`/api/v1/auth/login`) y altas (`/api/v1/signup`), mitigando ataques de fuerza bruta y saturación de base de datos.
   - *Nota de alcance implementado:* Actualmente el sistema **no persiste** en base de datos la dirección IP ni la cadena de agente de usuario (*User-Agent*). La creación de una bitácora de auditoría histórica persistente con trazabilidad de IP de acciones administrativas está catalogada como **medida planeada** bajo el issue **CAL-21**.
3. **Datos de comensales y clientes finales:**
   - Identificación operativa de mesa y comanda.
   - Datos fiscales para emisión formal de comprobantes digitales (RFC, Razón Social, Régimen Fiscal y Código Postal): Constituyen una **medida planeada** en el backlog de facturación electrónica (**CAL-40** y **CAL-102**).

#### Doble Rol Jurídico de RestoHub: Responsable vs. Encargado
Conforme a la legislación de protección de datos personales:
* **RestoHub como Encargado:** Frente a los datos de los empleados del restaurante y los consumos de comensales en piso, **el Restaurante es el Responsable**, y **RestoHub actúa como Encargado**, proporcionando la infraestructura tecnológica bajo estrictas garantías contractuales y técnicas de aislamiento.
* **RestoHub como Responsable:** Frente a los directivos o dueños del restaurante que contratan la suscripción SaaS, RestoHub es el **Responsable directo** de sus datos de contacto y facturación mercantil.

#### Obligaciones Concretas y Clasificación de Medidas
| Disposición Legal | Principio LFPDPPP | Clasificación | Medida en RestoHub |
|---|---|---|---|
| **Art. 8, 15 y 16 LFPDPPP** | Consentimiento e Información | **Medida Planeada** (Issue **CAL-62**) | Elaboración y publicación del **Aviso de Privacidad Integral** en el formulario de alta (`/signup`) y del **Aviso de Privacidad Simplificado** en el portal de autoservicio y comensales. |
| **Art. 19 y 20 LFPDPPP** | Seguridad y Confidencialidad | **Medida Implementada** | Aislamiento multi-tenant forzado mediante **PostgreSQL Row Level Security (RLS)** a nivel motor (`FORCE ROW LEVEL SECURITY`, `USING` y `WITH CHECK`), donde el usuario de base de datos (`callejon9_app`) carece de permisos de superusuario o `BYPASSRLS`. Cifrado en tránsito obligatorio vía HTTPS con **TLS 1.2+** fijado en la infraestructura de Azure App Service (`minimum_tls_version = "1.2"` en Terraform). |
| **Art. 13 LFPDPPP** | Proporcionalidad | **Medida Implementada** | No se solicitan datos personales sensibles (biométricos, médicos o ideológicos). Los meseros y cocineros operan únicamente con nombre y correo institucional sin exigir CURP ni domicilio personal. |
| **Art. 28 a 35 LFPDPPP** | Derechos ARCO | **Medida Implementada** | Mecanismo de **baja lógica** (`active = false`). Para preservar la integridad referencial y el registro contable de órdenes pasadas, los usuarios dados de baja no se eliminan físicamente con sentencias destructivas (`DELETE`), pero sus sesiones quedan revocadas y sus credenciales inhabilitadas para ingresar al sistema. |
| **Seguridad de Sesiones** | Confidencialidad | **Medida Implementada** | Cookies de sesión autenticadas con atributos de protección estrictos: `HttpOnly`, `Secure` y `SameSite=Strict` configuradas en `SecurityConfig` y `AuthController`. |

---

## 3. Marco Fiscal, Facturación Electrónica y Medios de Pago

### 3.1. Código Fiscal de la Federación (CFF) y Facturación CFDI 4.0

* **Autoridad reguladora:** Servicio de Administración Tributaria (SAT) / Secretaría de Hacienda y Crédito Público (SHCP).
* **Marco aplicable:** Artículos 29 y 29-A del Código Fiscal de la Federación; Anexo 20 de la Resolución Miscelánea Fiscal (RMF).

#### Régimen de Comprobantes de Venta en Restaurantes
Todo establecimiento gastronómico en México debe emitir comprobantes de las operaciones que realiza con el público:
1. **Comprobante simplificado / Ticket de consumo (Implementado):**
   - El sistema genera y emite un comprobante de consumo con OpenPDF (`CAL-142`), registrando folio único, mesa, fecha y hora de emisión, partidas consumidas (producto, cantidad, precio unitario), subtotal y total pagado.
   - El total se concilia con las sesiones de caja abiertas obligatorias para el cobro (`CAL-98`).
2. **Desglose formal de impuestos y Facturación CFDI 4.0 (Medida Planeada):**
   - En el esquema de base de datos actual (`orders`, `sales`), se almacenan las líneas con su precio e importe total. La adición de columnas formales de impuestos desglosados (`tax_amount` para IVA 16%) y la captura de datos fiscales para timbrado digital ante un Proveedor Autorizado de Certificación (PAC) están reservadas para los issues de facturación en el backlog (**CAL-40** y **CAL-102**).
3. **Inmutabilidad de ventas cobradas (Implementado):**
   - Una vez que una orden entra en estado `PAID`, no puede ser modificada ni sobreescrita (`BusinessRuleException` HTTP 409 ante intentos de re-cobro).

---

### 3.2. Medios de Pago y Cumplimiento PCI-DSS v4.0

* **Regulaciones:** Estándar de Seguridad de Datos para la Industria de Tarjetas de Pago (**PCI-DSS v4.0**); Ley para Regular las Instituciones de Tecnología Financiera (**Ley Fintech**).
* **Operación en RestoHub:** Registro de métodos de pago en terminales de caja y planeación de pasarela para suscripciones.

#### Obligaciones Concretas y Estado de Implementación
* **Principio de Cero Almacenamiento PCI (Implementado):**
  - **Invariante técnica:** RestoHub **nunca** recibe, transmite ni almacena números de tarjeta completos (PAN), fechas de vencimiento ni códigos de seguridad (CVV/CVC) en sus servidores o base de datos.
  - El checkout en punto de venta registra únicamente el método de pago mediante el enum `PaymentMethod` (`CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `MERCADO_PAGO`) y los montos desglosados en pagos divididos (`CAL-97`), cumpliendo el nivel más estricto de exclusión del alcance de evaluación PCI-DSS (Self-Assessment Questionnaire A).
* **Integración con Pasarela de Pago Digital (Medida Planeada):**
  - La integración con el SDK oficial de **Mercado Pago** (mediante *Checkout Pro* / *Tokenization Web Components*) para el cobro automatizado de las mensualidades de suscripción a los restaurantes está planeada y asignada al issue **CAL-29** (Fase 6). En el estado actual del repositorio, solo existe la constante en el enumerador de métodos de pago.

---

### 3.3. Ley Federal de Protección al Consumidor (LFPC) y Criterios PROFECO

* **Autoridad:** Procuraduría Federal del Consumidor (PROFECO).
* **Marco aplicable:** Artículos 7, 7 BIS, 10 y 58 de la Ley Federal de Protección al Consumidor.

#### Obligaciones Concretas para el Proyecto
1. **Prohibición de cobro obligatorio de propina (Implementado):**
   - El **Artículo 10 de la LFPC** prohíbe a los proveedores de bienes o servicios llevar a cabo prácticas coercitivas, desleales o aplicar condiciones abusivas o impuestas. PROFECO ha establecido de manera vinculante que **la propina es una gratificación voluntaria** que no puede ser cargada de manera automática a la cuenta ni exigida como condición para el servicio.
   - **Medida implementada en RestoHub:** La interfaz de cobro en caja (`checkout-panel.tsx`) y los servicios de cálculo (`CheckoutService`) mantienen la propina (`tip`) como un monto estrictamente **opcional, desglosado y editable**. Un comensal o cajero puede registrar propina cero (`0.00`) sin que esto impida el cobro, la emisión del ticket o la liberación de la mesa.
2. **Precios transparentes y finales exhibidos (Implementado):**
   - Conforme al Artículo 7 BIS de la LFPC, los precios mostrados en el menú del mesero y menú QR representan el precio final al consumidor, sin cargos ocultos de cubierto o comisión por uso de plataforma.

---

## 4. Gobernanza del Código, Licenciamiento y Seguridad Técnica

### 4.1. Ley Federal del Derecho de Autor (LFDA) y Licenciamiento Propietario

* **Marco legal:** Artículos 13 (fracción XI), 101 a 114 de la Ley Federal del Derecho de Autor (México).
* **Régimen de Propiedad:** **Software Propietario — Todos los derechos reservados.** Titularidad exclusiva del equipo **Callejón 19**.

#### Obligaciones Concretas y Estado de Implementación
* **Protección de la obra de software (Implementado):** El código fuente en Java/Spring Boot, Next.js/React, scripts de aprovisionamiento en Terraform y esquemas de base de datos Flyway constituyen una obra intelectual protegida que no puede ser redistribuida ni explotada comercialmente sin autorización previa.
* **Separación de dependencias de terceros (Implementado):** Las dependencias de código abierto se gestionan mediante administradores de paquetes estándar (`Maven` en backend y `pnpm` con lockfile congelado en frontend), verificando que sus licencias (Apache 2.0, MIT, BSD) permitan su uso en software propietario sin cláusulas *copyleft* infecciosas.
* **Archivos formales de gobernanza (Medida Planeada):**
  - La incorporación física de los archivos `LICENSE` y `SECURITY.md` en la raíz del repositorio, junto con los encabezados formales de copyright en cada archivo, forma parte de los entregables de gobernanza asignados al issue **CAL-9** ("Establecer gobernanza y protección del repositorio").

### 4.2. Política de Divulgación Coordinada de Vulnerabilidades

* **Estándar:** RFC 9116 (*A File Format to Aid in Security Vulnerability Reporting*).
* **Canal oficial:** Pestaña **Security** de GitHub mediante *Private Vulnerability Reporting*.
* **Compromiso operativo:** Prohibición absoluta de reportar vulnerabilidades o fallos de aislamiento RLS en issues públicos. La atención de incidencias críticas de aislamiento multi-tenant se cataloga con prioridad máxima inmediata.

---

## 5. Estándares Técnicos Internacionales de Calidad y Arquitectura

### 5.1. ISO/IEC 25010:2023 — Modelo de Calidad del Producto Software

El sistema RestoHub adopta las características del modelo de calidad internacional actualizado **ISO/IEC 25010:2023** (Systems and software engineering — SQuaRE — Product quality model):

| Característica de Calidad | Subcaracterística | Implementación Concreta en RestoHub |
|---|---|---|
| **Adecuación Funcional** | Completitud y corrección | Flujo operativo completo: alta de restaurante, gestión de mesas, captura de comanda con notas por producto (`CAL-151`), preparación en cocina con sincronización STOMP, cobro en caja con arqueo (`CAL-98`) y emisión de tickets en PDF. |
| **Fiabilidad** | Tolerancia a fallos y consistencia | Transacciones ACID; concurrencia con candados pesimistas (`PESSIMISTIC_WRITE`) para evitar dobles cobros; consultas resilientes en frontend con TanStack Query. |
| **Seguridad** | Confidencialidad e integridad | Aislamiento estricto impuesto por RLS en PostgreSQL; hashing de contraseñas con BCrypt; cookies protegidas (`HttpOnly`, `SameSite=Strict`, `Secure`). |
| **Eficiencia de Desempeño** | Comportamiento temporal | Reescritura *first-party* en `next.config.ts` evitando sobrecostos CORS; índices de base de datos optimizados sobre `tenant_id` y claves foráneas. |
| **Mantenibilidad** | Modularidad y verificabilidad | Arquitectura limpia desacoplada; suite automatizada de pruebas unitarias (JUnit 5, Mockito, Vitest) y pruebas de integración con Testcontainers PostgreSQL 16. |

### 5.2. OWASP Top 10 API Security (2023)

1. **API1:2023 — Broken Object Level Authorization (BOLA):**\
   * *Mitigación en RestoHub:* Invariante no negociable. La base de datos impone Row Level Security forzado mediante la variable de sesión transaccional `app.tenant_id`. Cualquier consulta que intente acceder a datos de otro restaurante resulta en cero registros o error de integridad, imposibilitando la fuga de información horizontal.
2. **API2:2023 — Broken Authentication:**\
   * *Mitigación en RestoHub:* Tokens JWT firmados criptográficamente mediante HMAC-SHA256, transportados en cookies `HttpOnly` no accesibles por JavaScript del navegador.
3. **API4:2023 — Unrestricted Resource Consumption:**\
   * *Mitigación en RestoHub:* Limitadores de tasa por IP (`login-rate-limit` y `signup-rate-limit`) que protegen los endpoints de autenticación y alta de cuentas contra ataques de denegación de servicio y fuerza bruta.
4. **API5:2023 — Broken Function Level Authorization (BFLA):**\
   * *Mitigación en RestoHub:* Matriz de autorización declarativa obligatoria (`@PreAuthorize("hasAnyRole(...)")`), validada automáticamente por la suite `AuthorizationContractTest` sobre todas las rutas registradas en el backend.

### 5.3. WCAG 2.1 — Directrices de Accesibilidad Web (Nivel AA)

* **Aplicación:** PWA de comensales e interfaces operativas del personal (cocina, mesas, caja).
* **Medidas implementadas:** Contraste tipográfico superior a 4.5:1 en elementos de lectura, soporte de navegación accesible por teclado en componentes shadcn/ui y etiquetas `aria-label` descriptivas en botones de acción rápida e íconos interactivos.

### 5.4. Estándares Complementarios de la Industria

1. **ISO/IEC 27001:2022 (Sistemas de Gestión de Seguridad de la Información):**\
   Adopción de controles del Anexo A en materia de autenticación robusta, control de acceso basado en el principio de mínimo privilegio (el rol de base de datos de la app no tiene privilegios DDL ni `SUPERUSER`), y separación de ambientes.
2. **ISO/IEC/IEEE 12207:2017 (Procesos del Ciclo de Vida del Software):**\
   Control riguroso de configuración mediante ramas Git (`feat/*`, `fix/*`, `docs/*`), commits convencionales, revisiones de código obligatorias previas a integración y puertas de calidad automatizadas en el pipeline de CI.
3. **NOM-151-SCFI-2016 (Conservación de Mensajes de Datos y Digitalización de Documentos):**\
   Norma oficial mexicana aplicable a los documentos comerciales electrónicos generados por el SaaS (folios inmutables, estampas de tiempo UTC sincronizadas y tickets en PDF descargables para soporte de transacciones mercantiles).

---

## 6. Matriz Consolidada de Obligaciones Técnicas

| Marco Normativo / Estándar | Tipo | Ámbito | Estado en RestoHub | Detalle / Issue Asociado |
|---|---|---|---|---|
| **LFPDPPP (DOF 20-03-2025)** | Ley Federal | Privacidad y Datos | **Planeada** | Aviso de Privacidad Integral en `/signup` y PWA (**CAL-62**). |
| **LFPDPPP (Seguridad)** | Ley Federal | Confidencialidad | **Implementada** | Aislamiento por RLS forzado en PostgreSQL y TLS 1.2+ en Azure App Service. |
| **LFPDPPP (Derechos ARCO)** | Ley Federal | Gestión de Datos | **Implementada** | Baja lógica de usuarios (`active = false`) preservando integridad de auditoría. |
| **CFF / RMF (CFDI 4.0)** | Código / RMF | Fiscal y Facturación | **Planeada** | Adición de campos RFC/CFDI, desglose de IVA (`tax_amount`) y timbrado con PAC (**CAL-40**, **CAL-102**). |
| **CFF (Comprobantes)** | Código Fiscal | Consumo en Restaurante | **Implementada** | Emisión de comprobantes en PDF con OpenPDF (`CAL-142`) y turnos de caja obligatorios (`CAL-98`). |
| **LFPC Art. 10 (PROFECO)** | Ley Federal | Consumo | **Implementada** | Propina estrictamente opcional y desglosada; precios finales exhibidos en menú. |
| **PCI-DSS v4.0** | Estándar Industria | Seguridad de Pagos | **Implementada** | Cero almacenamiento de datos de tarjeta (PAN/CVV) en bases de datos o código propio. |
| **Mercado Pago SDK** | Pasarela Digital | Suscripciones SaaS | **Planeada** | Integración del SDK para cobro de mensualidades de planes SaaS (**CAL-29**). |
| **LFDA / Copyright** | Ley Federal | Propiedad Intelectual | **Implementada** | Régimen de software propietario con reserva de derechos para Callejón 19. |
| **Archivos LICENSE/SECURITY** | Gobernanza | Gestión de Repositorio | **Planeada** | Publicación de `LICENSE` y `SECURITY.md` en raíz del repo (**CAL-9**). |
| **OWASP API Security 2023** | Estándar Seguridad | Arquitectura REST | **Implementada** | RLS contra BOLA, Rate Limit por IP en `/signup` y `/login`, RBAC en Spring Boot. |
| **ISO/IEC 25010:2023** | Estándar Calidad | Calidad de Software | **Implementada** | Puertas de verificación automatizada (pruebas unitarias, lint, tipos, build y RLS). |
| **W3C WCAG 2.1 AA** | Estándar Web | Accesibilidad | **Implementada** | Semántica accesible, etiquetas aria y contraste de color en PWA y web. |
| **NOM-151-SCFI-2016** | Norma Oficial Mex. | Documentos Digitales | **Implementada** | Inmutabilidad de folios de ticket y marcas temporales en comprobantes de venta. |

---

## 7. Declaración Formal de Huecos Legales y Técnicos Pendientes

En estricto apego al Criterio de Aceptación 5 y a la Definition of Done de **CAL-69**, se identifican los aspectos regulatorios que requieren asesoría legal o contable especializada externa, con sus respectivos responsables asignados:

| Identificador | Asunto / Hueco Pendiente | Causa de la Pendencia | Impacto Técnico / Operativo | Responsable de Seguimiento | Fecha Límite / Fase |
|---|---|---|---|---|---|
| **HUECO-LEG-01** | **Elección y contratación del PAC (Proveedor Autorizado de Certificación)** | Requiere alta fiscal y contratación de paquete de timbres para emisión de CFDI 4.0 directo. | Bloquea la implementación técnica de timbrado en los tickets de venta (**CAL-40**, **CAL-102**). | **Octavio Duarte** (PM / Representante) | Previo a fase de facturación electrónica |
| **HUECO-LEG-02** | **Aviso de Privacidad y Términos del Servicio SaaS (B2B)** | Requiere validación legal para delimitar acuerdos de nivel de servicio (SLA), tratamiento de datos personales y cláusulas de responsabilidad. | Requisito indispensable para la publicación del portal de alta abierta y autoservicio (**CAL-62**). | **Octavio Duarte** | Sprint 6 (Noviembre 2026) |
| **HUECO-LEG-03** | **Validación Contable del Esquema de Factura Global Diaria** | Requiere que un contador fiscalista dictamine si el reporte acumulado del corte de caja cumple los requisitos del SAT para la factura global periódica. | Define la estructura de exportación contable del módulo de turnos y ventas (**CAL-98**). | **Valeria Mercado** (Líder de Caja y Ventas) | 15 de Noviembre de 2026 |
| **HUECO-LEG-04** | **Contrato de Adhesión ante PROFECO** | Evaluar si el modelo de suscripción mensual a restaurantes requiere registro formal de contrato de adhesión digital ante la Subprocuraduría de Servicios de PROFECO. | Certeza legal en la contratación electrónica de los planes de suscripción RestoHub. | **Octavio Duarte** | Fase previa al lanzamiento comercial |
