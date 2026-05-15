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
 *
 * <p>
 * Flags {@code page.*}: gating de páginas del frontend. Tienen
 * {@code publicRead=true} para que el endpoint público
 * {@code /api/feature-flags} los exponga al cliente sin requerir rol admin.
 * El frontend los lee al boot y oculta/redirige las páginas deshabilitadas.
 */
@Component
public class FlagRegistry {

        // Constantes públicas para que los call sites no escriban strings sueltas.
        public static final String FLAG_EXCEL_EXPOSE_ERROR_DETAILS = "excel.expose-error-details";

        // Gating de páginas del frontend.
        public static final String FLAG_PAGE_UPLOAD = "page.upload.enabled";
        public static final String FLAG_PAGE_MIS_DISTRIBUCIONES = "page.mis-distribuciones.enabled";
        public static final String FLAG_PAGE_CONFIGURACION = "page.configuracion.enabled";
        public static final String FLAG_PAGE_RUTA = "page.ruta.enabled";

        // Rollout del flujo de generación de PDF en cliente. Fallback al endpoint
        // server-side viejo cuando esté en false.
        public static final String FLAG_PDF_CLIENT_ENABLED = "pdf.client.enabled";

        private final Map<String, FlagDefinition> defs;

        public FlagRegistry() {
                Map<String, FlagDefinition> m = new LinkedHashMap<>();
                register(m, new FlagDefinition(FLAG_EXCEL_EXPOSE_ERROR_DETAILS, FlagValueType.BOOLEAN, "true",
                                "Si true, las respuestas 422 de procesamiento de Excel incluyen el detalle "
                                                + "de errores de validación. Si false, solo mensaje genérico.",
                                false));

                register(m, new FlagDefinition(FLAG_PAGE_UPLOAD, FlagValueType.BOOLEAN, "true",
                                "Página /upload (Nueva distribución). En false el sidebar la oculta y "
                                                + "el acceso directo muestra cartel de página deshabilitada.",
                                true));
                register(m, new FlagDefinition(FLAG_PAGE_MIS_DISTRIBUCIONES, FlagValueType.BOOLEAN, "true",
                                "Página /mis-distribuciones. En false el sidebar la oculta y "
                                                + "el acceso directo muestra cartel de página deshabilitada.",
                                true));
                register(m, new FlagDefinition(FLAG_PAGE_CONFIGURACION, FlagValueType.BOOLEAN, "true",
                                "Página /configuracion (settings del simulador). En false el sidebar la oculta y "
                                                + "el acceso directo muestra cartel de página deshabilitada.",
                                true));
                register(m, new FlagDefinition(FLAG_PAGE_RUTA, FlagValueType.BOOLEAN, "true",
                                "Página /ruta (Recorrido de ruta). En false el sidebar la oculta y "
                                                + "el acceso directo muestra cartel de página deshabilitada.",
                                true));

                register(m, new FlagDefinition(FLAG_PDF_CLIENT_ENABLED, FlagValueType.BOOLEAN, "true",
                                "Habilita la generación de PDFs en el cliente con pdfme. "
                                                + "Si false, el cliente cae al endpoint server-side viejo /pdfs.",
                                true));

                this.defs = Map.copyOf(m);
        }

        private static void register(Map<String, FlagDefinition> m, FlagDefinition def) {
                if (def.defaultValue() == null || def.defaultValue().isBlank()) {
                        throw new IllegalStateException("FlagDefinition '" + def.key()
                                        + "' tiene defaultValue null/blank — debe ser explícito");
                }
                m.put(def.key(), def);
        }

        public List<FlagDefinition> all() {
                return List.copyOf(defs.values());
        }

        public List<FlagDefinition> publicFlags() {
                return defs.values().stream().filter(FlagDefinition::publicRead).toList();
        }

        public Optional<FlagDefinition> find(String key) {
                return Optional.ofNullable(defs.get(key));
        }

        public boolean isRegistered(String key) {
                return defs.containsKey(key);
        }
}
