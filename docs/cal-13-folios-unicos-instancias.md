# CAL-13 — Garantizar folios únicos entre instancias en PostgreSQL con concurrencia segura

## Contexto y Problema

En la implementación inicial de RestoHub, `FolioGenerator` dependía de un `AtomicLong` en memoria de una única JVM para evitar colisiones entre órdenes o tickets creados dentro del mismo segundo UTC (`ORD-yyMMddHHmmss` / `TCK-yyMMddHHmmss`).

Al desplegar múltiples réplicas de la aplicación (por ejemplo, múltiples tareas ECS en AWS conforme a CAL-53), cada contenedor mantenía su propio contador en memoria independiente. Dos instancias que procesaran órdenes para el mismo restaurante en el mismo segundo generaban el mismo folio, chocando contra la restricción de base de datos:
```sql
CONSTRAINT orders_folio_unique_per_tenant UNIQUE (tenant_id, folio)
```
provocando excepciones `DataIntegrityViolationException` (HTTP 500) y fallas no controladas en el flujo de apertura de mesas y cobro.

---

## Solución Implementada

### 1. Migración Flyway `V12__tenant_folio_sequences.sql`

Se introdujo la tabla `tenant_folio_sequences` en el plano de datos por restaurante:

```sql
CREATE TABLE tenant_folio_sequences (
    tenant_id                 uuid        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    prefix                    varchar(10) NOT NULL,
    last_issued_epoch_second  bigint      NOT NULL,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_folio_sequences PRIMARY KEY (tenant_id, prefix)
);

ALTER TABLE tenant_folio_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_folio_sequences FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_folio_sequences
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
```

### 2. Generación Atómica y Segura con PostgreSQL

`FolioGenerator` utiliza una sentencia atómica `INSERT ... ON CONFLICT DO UPDATE`:

```sql
INSERT INTO tenant_folio_sequences (tenant_id, prefix, last_issued_epoch_second, updated_at)
VALUES (?, ?, ?, now())
ON CONFLICT (tenant_id, prefix)
DO UPDATE SET
    last_issued_epoch_second = GREATEST(tenant_folio_sequences.last_issued_epoch_second + 1, EXCLUDED.last_issued_epoch_second),
    updated_at = now()
RETURNING last_issued_epoch_second;
```

#### Propiedades del algoritmo:
1. **Unicidad multi-instancia garantizada**: PostgreSQL serializa a nivel de fila `(tenant_id, prefix)` las transacciones concurrentes mediante el candado exclusivo de `ON CONFLICT DO UPDATE`.
2. **Monotonicidad estricta**: Si dos o más solicitudes coinciden en el mismo segundo, `GREATEST(last_issued + 1, now)` incrementa el segundo en 1 segundo por cada colisión, asegurando folios distintos sin errores.
3. **Alineación con el reloj real**: Tras una ráfaga, cuando el reloj del sistema avanza más allá del último segundo emitido, el generador vuelve a sincronizarse con `now`.
4. **Independencia por inquilino y prefijo**: La fila es por `(tenant_id, prefix)`. Las órdenes (`ORD-`) y tickets (`TCK-`) de diferentes restaurantes nunca bloquean ni interfieren entre sí.
5. **Fallback seguro en memoria**: Si se invoca fuera de Spring o sin contexto de tenant (pruebas unitarias rápidas), utiliza un contador `AtomicLong` local.

---

## Seguridad y Row Level Security (RLS)

- La tabla `tenant_folio_sequences` es la tabla número 15 del plano de datos bajo RLS.
- Posee `ENABLE ROW LEVEL SECURITY` y `FORCE ROW LEVEL SECURITY`.
- Aplica la política null-safe `nullif(current_setting('app.tenant_id', true), '')::uuid`.
- Un inquilino no puede leer ni modificar secuencias pertenecientes a otro inquilino. Sin contexto activo, RLS bloquea cualquier acceso.

---

## Verificación y Pruebas

1. **`FolioConcurrencyTest`**:
   - `multipleInstancesGenerateUniqueFoliosConcurrently`: Simula 5 instancias concurrentes de `FolioGenerator` atendiendo 30 hilos simultáneos para el mismo tenant con `CountDownLatch`. Las 30 órdenes se guardan exitosamente en PostgreSQL sin violar `orders_folio_unique_per_tenant`.
   - `differentPrefixesAdvanceIndependently`: Demuestra independencia entre prefijos `ORD-` y `TCK-`.
   - `tenantIsolationAppliesToFolioSequences`: Demuestra que las filas de secuencias están aisladas por RLS entre inquilinos.
2. **`DatabaseSecurityContractTest`**:
   - Clasificación de `tenant_folio_sequences` dentro del contrato de seguridad de la base de datos (15 tablas RLS forzadas, 0 no clasificadas).
3. **`TestDatabaseSecurityTest`**:
   - Validación de 15 tablas protegidas con RLS en el catálogo de PostgreSQL.
4. **Pruebas de regresión**:
   - `OrderControllerTest`, `OrderChangesDuringCheckoutTest`, `OrderServiceConcurrencyTest`, `CheckoutControllerTest` y `SalesControllerTest` pasan con 0 fallos.
