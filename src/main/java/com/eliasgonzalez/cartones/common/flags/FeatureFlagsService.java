package com.eliasgonzalez.cartones.common.flags;

/**
 * API de lectura de feature flags que consume la aplicación.
 *
 * <p>
 * Esta es la única superficie que el resto del código debería tocar para
 * evaluar flags. La implementación actual
 * ({@code OverridableFeatureFlagsService})
 * combina los defaults de {@code classpath:flags.yml} (vía openflags) con una
 * tabla {@code flag_override} mutable desde la UI admin.
 *
 * <p>
 * Cuando openflags exponga una API mutable oficial, esta interface puede
 * mantenerse intacta y solo cambia la implementación — sin tocar callers.
 */
public interface FeatureFlagsService {

    boolean getBoolean(String key, boolean defaultValue);

    String getString(String key, String defaultValue);

    long getLong(String key, long defaultValue);
}
