package com.eliasgonzalez.cartones.common.flags.domain.enums;

/**
 * Tipo del valor de un flag. Determina cómo se parsea {@code value_text} al
 * leer un override desde la base.
 *
 * <p>
 * Mantenido deliberadamente chico — coincide con los tipos que hoy expone
 * {@code OpenFlagsClient} (boolean / string / long). Si openflags suma tipos
 * (double, enum, json) este enum se extiende sin tocar callers, porque las
 * lecturas pasan por métodos tipados en {@code FeatureFlagsService}.
 */
public enum FlagValueType {
    BOOLEAN,
    STRING,
    LONG
}
