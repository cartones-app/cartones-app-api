package com.eliasgonzalez.cartones.distribucion.service.dto;

import lombok.*;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
@Builder
public class EtiquetaDTO {

    private int numeroVendedor;
    private String nombre;
    private String saldo;

    private List<String> seneteRangos;
    private String seneteCartones;
    private String resultadoSenete;

    private List<String> telebingoRangos;
    private String telebingoCartones;
    private String resultadoTelebingo;

}