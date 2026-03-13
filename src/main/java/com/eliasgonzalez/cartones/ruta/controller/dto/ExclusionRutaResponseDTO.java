package com.eliasgonzalez.cartones.ruta.controller.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExclusionRutaResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private LocalDateTime createdAt;
    private String createdBy;
}
