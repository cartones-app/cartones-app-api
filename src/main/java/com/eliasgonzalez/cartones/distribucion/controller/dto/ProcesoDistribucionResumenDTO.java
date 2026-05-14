package com.eliasgonzalez.cartones.distribucion.controller.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen de un ProcesoDistribucion para listados.
 * NO contiene los BLOBs de PDFs (etiquetas/resumen) — solo metadata
 * y flags de disponibilidad. Mantiene la respuesta liviana incluso si
 * el usuario tiene cientos de procesos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcesoDistribucionResumenDTO {

    private String procesoId;
    private String estado;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private boolean tieneEtiquetas;
    private boolean tieneResumen;
    private long tamanoEtiquetasBytes;
    private long tamanoResumenBytes;
}
