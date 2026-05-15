package com.eliasgonzalez.cartones.common.flags.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;

/**
 * Registro central de los feature flags que la UI admin puede gestionar.
 *
 * <p>
 * Mantenerlo explícito (en vez de inferirlo de openflags) tiene dos
 * ventajas: (1) la UI sabe qué flags existen aunque no haya override en DB,
 * y (2) evita que un typo en {@code flag_override.flag_key} produzca filas
 * huérfanas que la app no consulta.
 *
 * <p>
 * Para sumar un flag: agregarlo acá, también en {@code flags.yml} (con el
 * mismo default), y usar las constantes desde el código que lo evalúa.
 */
@Component
public class FlagRegistry {

    // Constantes públicas para que los call sites no escriban strings sueltas.
    public static final String FLAG_RUTA_ENABLED = "ruta.enabled";
    public static final String FLAG_EXCEL_EXPOSE_ERROR_DETAILS = "excel.expose-error-details";
    public static final String FLAG_SIMULACION_ALGORITMO = "simulacion.algoritmo";

    private final Map<String, FlagDefinition> defs;

    public FlagRegistry() {
        Map<String, FlagDefinition> m = new LinkedHashMap<>();
        register(m, FLAG_RUTA_ENABLED, FlagValueType.BOOLEAN, "true",
                "Habilita el módulo ruta (/api/ruta/** y /api/admin/ruta/**). "
                        + "En false los endpoints devuelven 503.");
        register(m, FLAG_EXCEL_EXPOSE_ERROR_DETAILS, FlagValueType.BOOLEAN, "true",
                "Si true, las respuestas 422 de procesamiento de Excel incluyen el detalle "
                        + "de errores de validación. Si false, solo mensaje genérico.");
        register(m, FLAG_SIMULACION_ALGORITMO, FlagValueType.STRING, "legacy",
                "Algoritmo de distribución de cartones. Hoy solo existe 'legacy'.");
        this.defs = Map.copyOf(m);
    }

    private static void register(Map<String, FlagDefinition> m, String key, FlagValueType type,
            String defaultValue, String description) {
        m.put(key, new FlagDefinition(key, type, defaultValue, description));
    }

    public List<FlagDefinition> all() {
        return List.copyOf(defs.values());
    }

    public Optional<FlagDefinition> find(String key) {
        return Optional.ofNullable(defs.get(key));
    }

    public boolean isRegistered(String key) {
        return defs.containsKey(key);
    }
}
