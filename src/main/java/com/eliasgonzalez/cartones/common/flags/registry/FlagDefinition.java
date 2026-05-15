package com.eliasgonzalez.cartones.common.flags.registry;

import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;

/**
 * Metadata estática de un flag conocido: clave, tipo, valor por default y
 * descripción para la UI admin. El default acá debe coincidir con el de
 * {@code classpath:flags.yml} — es lo que se usa si openflags no devuelve nada.
 *
 * <p>
 * {@code publicRead} marca los flags expuestos al endpoint
 * {@code GET /api/feature-flags} que cualquier usuario autenticado consume
 * para gating de páginas. Los flags que no son {@code publicRead} solo se
 * ven desde el endpoint admin.
 */
public record FlagDefinition(
        String key, FlagValueType type, String defaultValue, String description, boolean publicRead) {

    /**
     * Constructor de conveniencia para flags no públicos (legacy / backend only).
     */
    public FlagDefinition(String key, FlagValueType type, String defaultValue, String description) {
        this(key, type, defaultValue, description, false);
    }
}
