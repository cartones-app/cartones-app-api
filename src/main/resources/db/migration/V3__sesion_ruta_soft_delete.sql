-- ============================================================================
-- V3 — Soft delete híbrido en sesion_ruta + estado ARCHIVADA
-- ============================================================================
-- LimpiezaSesionRutaJob (cron diario) archiva sesiones COMPLETADA/ABANDONADA
-- viejas: pone archivo_excel=NULL (libera el BLOB pesado), estado=ARCHIVADA
-- y deleted_at=now(). El @SQLRestriction de la entidad SesionRuta filtra
-- automáticamente las archivadas en queries normales.
--
-- Las queries de reportes/analytics que quieran ver sesiones archivadas
-- deben usar SQL nativo o un repo separado sin la restricción.
-- ============================================================================

ALTER TABLE sesion_ruta
    ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_sesion_ruta_deleted_at ON sesion_ruta(deleted_at);
