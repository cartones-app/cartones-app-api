package com.eliasgonzalez.cartones.distribucion.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class VendedorSimuladoDTO {
    private Long id;
    private String nombre;

    // Listas de Strings listas para mostrar: ["100-150", "200-210"]
    private List<String> rangosSenete;
    private List<String> rangosTelebingo;

}