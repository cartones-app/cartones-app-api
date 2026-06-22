package com.eliasgonzalez.cartones.common.logging;

/**
 * Sanitiza valores de origen externo (usuario, request, archivos) antes de
 * incluirlos en mensajes de log. Mitiga CWE-117 (log forging) reemplazando
 * caracteres de control (CR/LF) que podrían inyectar líneas falsas de log y
 * confundir herramientas de auditoría o SIEM.
 *
 * <p>
 * Truncamiento adicional a 500 caracteres para acotar logs absurdamente
 * grandes (file names patológicos, URIs con muchos query params, etc).
 *
 * <p>
 * Uso:
 *
 * <pre>{@code
 * import static com.eliasgonzalez.cartones.common.logging.LogSanitizer.safe;
 * ...
 * log.info("user-input: {}", safe(value));
 * }</pre>
 */
public final class LogSanitizer {

    private static final int MAX_LENGTH = 500;
    private static final String TRUNCATION_SUFFIX = "...";

    private LogSanitizer() {
        // utility class
    }

    /**
     * Devuelve una representación segura del valor para logging. {@code null}
     * se renderiza como {@code "null"} (consistente con {@link String#valueOf}).
     */
    public static String safe(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH) + TRUNCATION_SUFFIX;
        }
        // CR/LF/TAB se hacen visibles como literal escape (preservan pista forense).
        // Otros caracteres de control ASCII (<0x20 excepto los tres anteriores, 0x7F)
        // se neutralizan a '?' — no aportan info útil en logs y rompen parsers de
        // SIEM / log shippers (Logstash, Fluentd, etc).
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "?")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
