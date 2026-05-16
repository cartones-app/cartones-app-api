package com.eliasgonzalez.cartones.common.flags.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request del admin para crear o actualizar un override. El tipo del valor
 * se valida contra la definición estática del flag, no se acepta en la
 * request — el admin no puede cambiar el tipo de un flag existente.
 */
public record SetFlagRequest(
        @NotBlank @Size(max = 4096) String value,
        @Size(max = 1024) String reason) {
}
