package com.eliasgonzalez.cartones.ruta.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Nombres exactos de las columnas en el Excel de ruta (hoja PLANILLA DOMINGO).
 * Las columnas se detectan por nombre de encabezado (fila 1), no por posición.
 * Si el orden cambia en el Excel, el sistema sigue funcionando.
 */
@Getter
@AllArgsConstructor
public enum RutaColumnaEnum {

    HOJA("PLANILLA DOMINGO"),

    FECHA("FECHA"),
    VENDEDOR("VENDEDOR"),

    // Columnas de entrada — el backend las escribe al exportar
    SENETE_TOTAL_ENVIADO("SENETE_TOTAL_ENVIADO"),
    TELEBINGO_TOTAL_ENVIADO("TELEBINGO_TOTAL_ENVIADO"),
    REF_SENETE("REF-SENETE"),
    REF_TELB("REF-TELB"),
    DEV_SEN("DEV-SEN"),
    DEV_TELB("DEV-TELB"),
    PAGO1("PAGO1"),
    PAGO2("PAGO2"),

    // Columnas de referencia — solo lectura para el frontend
    DEUDA_ANT("DEUDA_ANT");

    private final String valor;
}
