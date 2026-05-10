package com.eliasgonzalez.cartones.distribucion.repository;

import java.time.LocalDateTime;

/**
 * Projection (interface-based) para listados de ProcesoDistribucion sin cargar
 * los BLOBs de PDFs. La query nativa proyecta solo columnas escalares + el
 * tamaño calculado con OCTET_LENGTH para que el driver no traiga los bytes
 * a la JVM.
 *
 * Es el equivalente "ligero" de ProcesoDistribucion para los endpoints de
 * listado (GET /api/distribuciones, GET /api/admin/distribuciones).
 */
public interface ProcesoDistribucionResumenView {
    String getProcesoId();

    String getEstado();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    String getCreatedBy();

    Long getTamanoEtiquetasBytes();

    Long getTamanoResumenBytes();
}
