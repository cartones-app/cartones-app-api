package com.eliasgonzalez.cartones.vendedor.controller.dto;

import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Setter @ToString
@Builder
public class CargaVendedoresResponseDTO {

    private List<String> filasIgnoradas;
    private String procesoId;

}
