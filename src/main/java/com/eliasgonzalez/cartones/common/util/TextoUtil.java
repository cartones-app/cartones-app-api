package com.eliasgonzalez.cartones.common.util;

/**
 * Utilidades de manipulación de texto.
 */
public class TextoUtil {

    private TextoUtil() {}

    /**
     * Normaliza un string para comparaciones case-insensitive: trim + lowercase + quita espacios.
     */
    public static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
