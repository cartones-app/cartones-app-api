-- ============================================================================
-- V6 — Tabla pdf_template
-- ============================================================================
-- Almacena los templates de PDF editables por admin desde /admin/pdf-templates.
-- El JSON serializado tiene la forma del Template de pdfme (basePdf + schemas).
-- El cliente lo deserializa con JSON.parse y se lo pasa a @pdfme/generator.
--
-- En Fase 1 hay como máximo UN activo por tipo (índice parcial). En Fase 3
-- se relaja para permitir varios y elegir al simular.
-- ============================================================================

CREATE TABLE pdf_template (
    id           VARCHAR(36)  PRIMARY KEY,
    tipo         VARCHAR(16)  NOT NULL,            -- ETIQUETAS | RESUMEN
    nombre       VARCHAR(128) NOT NULL,
    schema_json  TEXT         NOT NULL,            -- Template de pdfme serializado
    activo       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    modified_by  VARCHAR(255),
    CONSTRAINT pdf_template_tipo_chk
        CHECK (tipo IN ('ETIQUETAS', 'RESUMEN'))
);

-- Índice parcial: a lo sumo UN activo por tipo en Fase 1. Cuando Fase 3
-- introduzca multi-template (selector al simular), este índice se borra.
CREATE UNIQUE INDEX pdf_template_activo_unico
    ON pdf_template (tipo) WHERE activo = TRUE;

COMMENT ON TABLE  pdf_template IS 'Templates de PDF editables desde la UI admin. Generación client-side via pdfme.';
COMMENT ON COLUMN pdf_template.tipo        IS 'ETIQUETAS o RESUMEN. CHECK enforced.';
COMMENT ON COLUMN pdf_template.schema_json IS 'JSON serializado del Template de pdfme: { basePdf, schemas: [[{name, type, position, ...}]] }';
COMMENT ON COLUMN pdf_template.activo      IS 'Si true, el cliente lo usa para generar PDFs de ese tipo. Solo 1 activo por tipo (índice parcial).';
