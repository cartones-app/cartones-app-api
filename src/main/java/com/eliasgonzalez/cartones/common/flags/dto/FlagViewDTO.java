package com.eliasgonzalez.cartones.common.flags.dto;

import java.time.LocalDateTime;

import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;

import lombok.Builder;

/**
 * Vista de un flag para la UI admin. Combina la definición estática con el
 * valor efectivo y el override DB si lo hay.
 */
@Builder
public record FlagViewDTO(
        String key,
        FlagValueType type,
        String description,
        String defaultValue,
        String effectiveValue,
        boolean hasOverride,
        String overrideValue,
        String overrideReason,
        String modifiedBy,
        LocalDateTime updatedAt) {
}
