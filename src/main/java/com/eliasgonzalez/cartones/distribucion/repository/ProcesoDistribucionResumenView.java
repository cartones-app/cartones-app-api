package com.eliasgonzalez.cartones.distribucion.repository;

import java.time.LocalDateTime;

/**
 * Projection (interface-based) para listados de ProcesoDistribucion sin cargar
 * los bytes de archivos. La query nativa proyecta solo columnas escalares y los
 * timestamps de archivos para que el frontend pueda determinar disponibilidad.
 */
public interface ProcesoDistribucionResumenView {
    String getProcesoId();

    String getEstado();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    String getCreatedBy();

    LocalDateTime getArchivosGeneradosEn();

    LocalDateTime getArchivosBorradosEn();
}
