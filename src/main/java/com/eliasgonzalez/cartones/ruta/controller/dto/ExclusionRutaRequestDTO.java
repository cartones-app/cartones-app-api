package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExclusionRutaRequestDTO {

    @NotBlank(message = "El nombre de la exclusión es obligatorio")
    private String nombre;

    private String descripcion;

    @Builder.Default
    private Boolean activo = true;
}
