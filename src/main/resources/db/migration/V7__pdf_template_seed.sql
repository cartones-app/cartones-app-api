-- ============================================================================
-- V7 — Seed inicial de pdf_template
-- ============================================================================
-- Inserta un template ETIQUETAS y uno RESUMEN, ambos activos, con un
-- schema mínimo válido (basePdf = BLANK_PDF, una página vacía). El admin
-- después abre cada uno desde /admin/pdf-templates, diseña con el Designer,
-- y persiste el JSON real con PUT.
--
-- Por qué el placeholder en lugar del JSON real:
--   El JSON real lo genera el Designer del frontend con drag & drop. Pegarlo
--   acá hard-coded sería frágil (cambiaría con cada ajuste visual). Mejor que
--   el admin lo construya desde la UI y lo guarde por PUT. Lo único que el
--   seed garantiza es que `findByTipoAndActivoTrue` no devuelva null al
--   arrancar — el caller del frontend ve un template "vacío" y el admin lo
--   completa.
--
-- IDs hardcoded para idempotencia / referencias estables si los usamos en otros
-- seeds futuros (ej. tests E2E que esperan un template específico).
-- ============================================================================

INSERT INTO pdf_template (id, tipo, nombre, schema_json, activo, created_by, modified_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'ETIQUETAS',
    'Etiquetas (default)',
    '{"basePdf":"BLANK_PDF","schemas":[[]]}',
    TRUE,
    'sistema',
    'sistema'
);

INSERT INTO pdf_template (id, tipo, nombre, schema_json, activo, created_by, modified_by)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'RESUMEN',
    'Resumen (default)',
    '{"basePdf":"BLANK_PDF","schemas":[[]]}',
    TRUE,
    'sistema',
    'sistema'
);
