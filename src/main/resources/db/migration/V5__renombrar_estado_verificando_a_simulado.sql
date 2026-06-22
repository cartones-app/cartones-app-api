-- ============================================================================
-- V5 — Renombrar estado 'verificando' → 'simulado' en proceso_distribucion
-- ============================================================================
-- 'verificando' era confuso: sugería que el sistema estaba haciendo algo en
-- background. En realidad significa "ya simulado, esperando que el usuario
-- descargue". 'simulado' es paralelo a 'pendiente' / 'completado' (participio
-- pasivo) y describe lo que ya ocurrió.
--
-- La columna estado es VARCHAR sin CHECK constraint, así que basta con UPDATE.
-- Idempotente: si no quedan filas con 'verificando' (caso fresh install), el
-- UPDATE afecta 0 filas y no falla.
-- ============================================================================

UPDATE proceso_distribucion SET estado = 'simulado' WHERE estado = 'verificando';
