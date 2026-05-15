package com.eliasgonzalez.cartones.common.flags.registry;

import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;

/**
 * Metadata estática de un flag conocido: clave, tipo, valor por default y
 * descripción para la UI admin. El default acá debe coincidir con el de
 * {@code classpath:flags.yml} — es lo que se usa si openflags no devuelve nada.
 */
public record FlagDefinition(String key, FlagValueType type, String defaultValue, String description) {
}
