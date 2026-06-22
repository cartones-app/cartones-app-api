package com.eliasgonzalez.cartones.distribucion.service.dto;

import lombok.*;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
@Builder
public class ResumenDTO {

    private int numeroVendedor;
    private String nombre;

    private Map<String, String> seneteDelAl;
    private int cantidadSenete;

    private Map<String, String> telebingoDelAl;
    private int cantidadTelebingo;

}