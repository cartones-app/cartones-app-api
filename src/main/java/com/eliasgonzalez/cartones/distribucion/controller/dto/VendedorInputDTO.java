package com.eliasgonzalez.cartones.distribucion.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorInputDTO {

    @NotNull
    private Long id;
    private String nombre;

    // Configuración Senete
    private Integer cantidadSenete;
    private Integer terminacionSenete; // Puede ser null

    // Configuración Telebingo
    private Integer cantidadTelebingo;
    private Integer terminacionTelebingo; // Puede ser null

}