-- ============================================================
-- Secuencias de folio por inquilino y prefijo.
--
-- CAL-13: Garantiza folios unicos entre multiples instancias
-- concurrentes sin depender del reloj o memoria de una sola JVM.
--
-- Cada fila mantiene el ultimo segundo emitido (epoch second)
-- para una combinacion (tenant_id, prefix), por ejemplo
-- (tenant_a, 'ORD-') o (tenant_a, 'TCK-').
--
-- Mediante INSERT ... ON CONFLICT DO UPDATE con GREATEST(...)
-- PostgreSQL garantiza de forma atomica y serializada que
-- cada invocacion recibe un segundo estrictamente mayor al
-- anterior o al instante actual, previniendo colisiones
-- con la restriccion UNIQUE(tenant_id, folio).
-- ============================================================

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
