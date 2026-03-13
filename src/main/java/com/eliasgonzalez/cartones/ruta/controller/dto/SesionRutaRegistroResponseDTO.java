package com.eliasgonzalez.cartones.ruta.controller.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionRutaRegistroResponseDTO {

    private Long id;
    private String vendedorNombre;
    private LocalDate fecha;

    // Columnas de entrada del Excel de ruta
    private Integer seneteTotalEnviado;
    private Integer telebingoTotalEnviado;
    private Integer refSenete;
    private Integer refTelb;
    private Integer devSen;
    private Integer devTelb;
    private BigDecimal pago1;
    private BigDecimal pago2;

    // Nota del distribuidor (no va al Excel exportado)
    private String nota;

    private Boolean completado;
    private LocalDateTime createdAt;
}
