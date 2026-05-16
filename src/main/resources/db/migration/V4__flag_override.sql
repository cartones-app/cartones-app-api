-- ============================================================================
-- V4 — Overrides runtime de feature flags
-- ============================================================================
-- Capa mutable encima de openflags (que es read-only). Permite a un admin
-- togglear flags en caliente desde /api/admin/feature-flags sin redeploy.
-- Si la fila existe, su valor pisa al de flags.yml; si no, se usa el del YAML.
--
-- Cuando openflags exponga una API mutable oficial, esta tabla puede
-- retirarse y la implementación de FeatureFlagsService migrarse sin
-- impacto a callers (todos consumen la interface, no esta tabla).
-- ============================================================================

CREATE TABLE flag_override (
    flag_key     VARCHAR(128) PRIMARY KEY,
    value_type   VARCHAR(16)  NOT NULL,            -- BOOLEAN | STRING | LONG
    value_text   TEXT         NOT NULL,            -- valor serializado como texto
    reason       TEXT,                             -- motivo del cambio (auditoría)
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    modified_by  VARCHAR(255),
    CONSTRAINT flag_override_value_type_chk
        CHECK (value_type IN ('BOOLEAN', 'STRING', 'LONG'))
);

COMMENT ON TABLE  flag_override IS 'Overrides runtime de feature flags. Pisan el valor de classpath:flags.yml.';
COMMENT ON COLUMN flag_override.flag_key   IS 'Clave del flag (mismo namespace que openflags). Ej: ruta.enabled, page.upload.enabled.';
COMMENT ON COLUMN flag_override.value_text IS 'Valor serializado como texto. Para BOOLEAN: "true"/"false". Para LONG: entero. Para STRING: literal.';
COMMENT ON COLUMN flag_override.reason     IS 'Motivo declarado por el admin al setear el override.';
