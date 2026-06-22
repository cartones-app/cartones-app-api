-- ============================================================================
-- V7 — Tracking de identidad estable para detectar renames de Keycloak
-- ============================================================================
-- Mantiene el mapeo `sub` (UUID estable de KC) → `preferred_username`
-- (username humano, mutable). El sub es el claim que NUNCA cambia, incluso
-- si en Keycloak le renombran el usuario. El filtro
-- UserIdentityTrackingFilter usa esta tabla para detectar renames y
-- propagarlos a las columnas que referencian al username viejo
-- (created_by, modified_by, preferencias_distribuidor.username).
--
-- first_seen_at / last_seen_at: telemetría útil para auditoría y para
-- diagnosticar problemas de identidad sin pegarle a Keycloak.
-- ============================================================================

CREATE TABLE user_identity (
    sub                          VARCHAR(64)  PRIMARY KEY,
    current_preferred_username   VARCHAR(255) NOT NULL,
    first_seen_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at                 TIMESTAMP    NOT NULL DEFAULT NOW(),
    rename_count                 INTEGER      NOT NULL DEFAULT 0
);

-- Índice para detectar el username viejo desde el current name (poco frecuente
-- pero útil al diagnosticar quién había estado usándolo).
CREATE INDEX idx_user_identity_current_preferred_username
    ON user_identity (current_preferred_username);

COMMENT ON TABLE  user_identity                            IS 'Mapeo sub (estable) → preferred_username (mutable) para detectar renames de Keycloak.';
COMMENT ON COLUMN user_identity.sub                        IS 'Claim sub del JWT — UUID estable de Keycloak. PK.';
COMMENT ON COLUMN user_identity.current_preferred_username IS 'Último preferred_username visto para este sub. Puede cambiar si el admin renombra al user en Keycloak.';
COMMENT ON COLUMN user_identity.rename_count               IS 'Cuántas veces se detectó un cambio de preferred_username — útil para auditoría.';
