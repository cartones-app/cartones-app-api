-- ============================================================================
-- V6 — Preferencias de impresión por distribuidor
-- ============================================================================
-- Guarda la configuración de generación de etiquetas (layout + orden) por
-- distribuidor. El PK es el `preferred_username` del JWT — mismo string que
-- termina en `created_by` de las entidades auditables (ver
-- SecurityConfig.jwtAuthenticationConverter, que setea
-- principalClaimName=preferred_username). Eso permite relacionar un proceso
-- con la preferencia de su creador sin tabla `users` ni FK contra Keycloak.
--
-- Sin row → defaults aplicados en código (TRES_POR_HOJA + SECUENCIAL),
-- equivalente al comportamiento previo a esta feature.
-- ============================================================================

CREATE TABLE preferencias_distribuidor (
    username             VARCHAR(255) PRIMARY KEY,
    layout_etiqueta      VARCHAR(32)  NOT NULL,
    orden_etiqueta       VARCHAR(32)  NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255),
    CONSTRAINT preferencias_distribuidor_layout_chk
        CHECK (layout_etiqueta IN ('TRES_POR_HOJA', 'CUATRO_POR_HOJA')),
    CONSTRAINT preferencias_distribuidor_orden_chk
        CHECK (orden_etiqueta  IN ('SECUENCIAL', 'INTERCALADO'))
);

COMMENT ON TABLE  preferencias_distribuidor IS 'Preferencias de impresión de etiquetas por distribuidor. PK = preferred_username del JWT (mismo string que created_by).';
COMMENT ON COLUMN preferencias_distribuidor.username        IS 'preferred_username del JWT de Keycloak. Coincide con created_by de entidades auditables.';
COMMENT ON COLUMN preferencias_distribuidor.layout_etiqueta IS 'Cantidad de etiquetas por hoja A4. TRES_POR_HOJA | CUATRO_POR_HOJA.';
COMMENT ON COLUMN preferencias_distribuidor.orden_etiqueta  IS 'Orden de impresión. SECUENCIAL = 1,2,3,4,... por hoja. INTERCALADO = orden tal que al apilar y cortar las pilas quedan en orden.';
