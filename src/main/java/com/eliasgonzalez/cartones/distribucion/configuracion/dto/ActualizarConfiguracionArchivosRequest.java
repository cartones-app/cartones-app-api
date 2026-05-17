package com.eliasgonzalez.cartones.distribucion.configuracion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request del PUT de configuración de archivos.
 *
 * Cota superior de retención: 120 meses (10 años). Evita que un valor absurdo
 * tipo {@code Integer.MAX_VALUE} desborde {@code LocalDateTime.minusMonths(...)}
 * en el job y tire excepción no controlada. El CHECK constraint en DB también
 * lo enforza como segunda defensa.
 */
public record ActualizarConfiguracionArchivosRequest(
        @Min(1) @Max(120) int retencionMeses,
        boolean eliminacionActiva) {
}
