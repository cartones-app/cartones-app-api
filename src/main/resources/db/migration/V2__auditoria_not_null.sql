-- ============================================================================
-- V2 — Auditoría: forzar NOT NULL DEFAULT 'sistema' en created_by/modified_by
-- ============================================================================
-- AuditorAware siempre setea valor (principal del JWT o 'sistema'), pero la DB
-- permitía null. Se rellenan filas existentes (si las hay) y se agrega la
-- restricción para garantizar el invariante a nivel de schema.
-- ============================================================================

-- vendedor
UPDATE vendedor SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE vendedor SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE vendedor
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;

-- proceso_distribucion
UPDATE proceso_distribucion SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE proceso_distribucion SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE proceso_distribucion
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;

-- proceso_distribucion_vendedor
UPDATE proceso_distribucion_vendedor SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE proceso_distribucion_vendedor SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE proceso_distribucion_vendedor
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;

-- sesion_ruta
UPDATE sesion_ruta SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE sesion_ruta SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE sesion_ruta
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;

-- sesion_ruta_registro
UPDATE sesion_ruta_registro SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE sesion_ruta_registro SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE sesion_ruta_registro
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;

-- exclusion_ruta
UPDATE exclusion_ruta SET created_by = 'sistema' WHERE created_by IS NULL;
UPDATE exclusion_ruta SET modified_by = 'sistema' WHERE modified_by IS NULL;
ALTER TABLE exclusion_ruta
    ALTER COLUMN created_by SET DEFAULT 'sistema',
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN modified_by SET DEFAULT 'sistema',
    ALTER COLUMN modified_by SET NOT NULL;
