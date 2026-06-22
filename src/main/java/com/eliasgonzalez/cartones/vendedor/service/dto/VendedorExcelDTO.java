package com.eliasgonzalez.cartones.vendedor.service.dto;

import lombok.*;

/**
 * DTO intermedio para transportar los datos leídos de una fila de Excel.
 */
@Getter @Setter
@AllArgsConstructor
@Builder
public class VendedorExcelDTO {

    // Información de seguimiento para errores
    private int filaActual;

    // Datos del Vendedor
    private final String nombre;
    private final String deudaStr; // Deuda como String para validación posterior

    // Datos de Senete
    private final Integer cantidadSenete;
    private final Integer resultadoSenete;

    // Datos de Telebingo
    private final Integer cantidadTelebingo;
    private final Integer resultadoTelebingo;
}
