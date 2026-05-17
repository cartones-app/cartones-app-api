-- ============================================================================
-- V8 — Migración de PDFs: de BLOBs en DB a filesystem montado como volumen.
--
-- Decisión de diseño: los bytes ya no se persisten en Postgres. En su lugar
-- se trackean timestamps que indican cuándo se generaron y cuándo se borraron
-- los archivos físicos. La política de retención + toggle de borrado vive en
-- la tabla configuracion_archivos (definida más abajo en esta misma migración).
-- ============================================================================

ALTER TABLE proceso_distribucion DROP COLUMN IF EXISTS pdf_etiquetas;
ALTER TABLE proceso_distribucion DROP COLUMN IF EXISTS pdf_resumen;

ALTER TABLE proceso_distribucion ADD COLUMN archivos_generados_en TIMESTAMP NULL;
ALTER TABLE proceso_distribucion ADD COLUMN archivos_borrados_en  TIMESTAMP NULL;

-- Índice parcial para acelerar la query del job de limpieza, que filtra
-- procesos con archivos generados antes de un umbral y aún no borrados.
-- Es partial para mantener el índice pequeño (excluye filas sin archivos
-- y filas ya limpiadas, que son la mayoría con el tiempo).
CREATE INDEX idx_proceso_distribucion_limpieza
    ON proceso_distribucion (archivos_generados_en)
    WHERE archivos_generados_en IS NOT NULL AND archivos_borrados_en IS NULL;

-- ----------------------------------------------------------------------------
-- Tabla singleton de configuración de retención.
-- CHECK (id = 1) hace explícito el contrato: una sola fila siempre.
-- Editable desde la UI admin (/api/admin/configuracion-archivos).
-- ----------------------------------------------------------------------------
CREATE TABLE configuracion_archivos (
    id                  BIGINT       PRIMARY KEY CHECK (id = 1),
    retencion_meses     INT          NOT NULL DEFAULT 3
                                     CHECK (retencion_meses > 0 AND retencion_meses <= 120),
    eliminacion_activa  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

COMMENT ON TABLE  configuracion_archivos IS 'Singleton (id=1) de configuración de política de retención de archivos en filesystem.';
COMMENT ON COLUMN configuracion_archivos.retencion_meses    IS 'Meses que se conservan los archivos antes de borrarlos. Mínimo 1, máximo 120.';
COMMENT ON COLUMN configuracion_archivos.eliminacion_activa IS 'Si FALSE, el job de limpieza loguea y sale sin borrar nada.';

INSERT INTO configuracion_archivos (id, retencion_meses, eliminacion_activa, created_at, updated_at)
VALUES (1, 3, TRUE, NOW(), NOW());
