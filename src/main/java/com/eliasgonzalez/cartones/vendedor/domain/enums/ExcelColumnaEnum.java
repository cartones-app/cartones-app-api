package com.eliasgonzalez.cartones.vendedor.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Nombres de columnas del Excel de distribución (hoja Sistema_Etiquetas).
 * La detección es por nombre de encabezado (fila 0), no por posición.
 */
@Getter
@AllArgsConstructor
public enum ExcelColumnaEnum {

    HOJA("Sistema_Etiquetas"),
    VENDEDOR("Vendedores"),
    CANT_SENETE("Cantidad_Senete"),
    CANT_TELEBINGO("Cantidad_Telebingo"),
    RESULT_SENETE("Resultados_Senete"),
    RESULT_TELEBINGO("Resultados_Telebingo"),
    SALDO("Saldo");

    private final String valor;
}
