package com.eliasgonzalez.cartones.ruta.controller.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionRutaResponseDTO {

    private Long id;
    private String sesionId;
    private String fechaFiltro;
    private String estado;
    private Integer totalRegistros;
    private Integer registrosCompletados;
    private LocalDateTime createdAt;
    private String createdBy;
}
