package com.eliasgonzalez.cartones.distribucion.controller.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private LocalDateTime archivosGeneradosEn;
    private LocalDateTime archivosBorradosEn;
}
