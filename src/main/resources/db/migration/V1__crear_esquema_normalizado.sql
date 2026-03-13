-- =============================================================
-- V1 — Esquema normalizado: vendedor maestro + tablas de proceso
--      y módulo de ruta (sesión + registros + exclusiones)
-- =============================================================

-- Tabla maestra de vendedores (identidad)
CREATE TABLE IF NOT EXISTS vendedor (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    modified_by  VARCHAR(255)
);

-- Tabla de procesos de distribución (reemplaza PROCESOS_PDF)
CREATE TABLE IF NOT EXISTS proceso_distribucion (
    proceso_id    VARCHAR(255) PRIMARY KEY,
    estado        VARCHAR(50)  NOT NULL DEFAULT 'PENDIENTE',
    pdf_etiquetas BYTEA,
    pdf_resumen   BYTEA,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    modified_by   VARCHAR(255)
);

-- Datos de distribución por vendedor y proceso (reemplaza VENDEDORES)
CREATE TABLE IF NOT EXISTS proceso_distribucion_vendedor (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vendedor_id           BIGINT       NOT NULL REFERENCES vendedor(id),
    proceso_id            VARCHAR(255) NOT NULL REFERENCES proceso_distribucion(proceso_id),
    cantidad_senete       INT          NOT NULL DEFAULT 0,
    terminacion_senete    INT,
    resultado_senete      INT          NOT NULL DEFAULT 0,
    rangos_senete         TEXT,
    cantidad_telebingo    INT          NOT NULL DEFAULT 0,
    terminacion_telebingo INT,
    resultado_telebingo   INT          NOT NULL DEFAULT 0,
    rangos_telebingo      TEXT,
    deuda                 DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255),
    CONSTRAINT uq_pdv_vendedor_proceso UNIQUE (vendedor_id, proceso_id)
);

-- Sesión de recorrido de ruta
CREATE TABLE IF NOT EXISTS sesion_ruta (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sesion_id             VARCHAR(255) NOT NULL UNIQUE,
    fecha_filtro          VARCHAR(255) NOT NULL,
    estado                VARCHAR(50)  NOT NULL DEFAULT 'ACTIVA',
    total_registros       INT          NOT NULL DEFAULT 0,
    registros_completados INT          NOT NULL DEFAULT 0,
    archivo_excel         BYTEA,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

-- Registros individuales de un recorrido (se populan al exportar)
CREATE TABLE IF NOT EXISTS sesion_ruta_registro (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sesion_ruta_id           BIGINT       NOT NULL REFERENCES sesion_ruta(id) ON DELETE CASCADE,
    vendedor_id              BIGINT       NOT NULL REFERENCES vendedor(id),
    fecha                    DATE         NOT NULL,
    senete_total_enviado     INT,
    telebingo_total_enviado  INT,
    ref_senete               INT,
    ref_telb                 INT,
    dev_sen                  INT,
    dev_telb                 INT,
    pago1                    DECIMAL(10,2),
    pago2                    DECIMAL(10,2),
    nota                     TEXT,
    completado               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(255),
    modified_by              VARCHAR(255),
    CONSTRAINT uq_srr_sesion_vendedor UNIQUE (sesion_ruta_id, vendedor_id)
);

-- Exclusiones dinámicas del flujo de ruta (gestionadas por ADMIN)
CREATE TABLE IF NOT EXISTS exclusion_ruta (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(255) NOT NULL UNIQUE,
    descripcion  VARCHAR(500),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    modified_by  VARCHAR(255)
);

-- Datos pre-cargados de exclusiones por defecto
INSERT INTO exclusion_ruta (nombre, descripcion, activo, created_at, updated_at)
VALUES
    ('RECIBO DE CARTONES', 'Fila especial del Excel de ruta, no corresponde a un vendedor real', TRUE, NOW(), NOW()),
    ('VENTA LOCAL', 'Fila especial del Excel de ruta, no corresponde a un vendedor real', TRUE, NOW(), NOW())
ON CONFLICT (nombre) DO NOTHING;
