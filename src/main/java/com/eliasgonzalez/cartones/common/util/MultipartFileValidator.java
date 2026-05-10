package com.eliasgonzalez.cartones.common.util;

import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validaciones tempranas para uploads de Excel.
 * Rechaza extensiones y content-types no esperados antes de pasar el flujo
 * a Apache POI (que con un blob arbitrario puede tardar o caer en zip-bomb).
 */
public final class MultipartFileValidator {

    private static final String XLSX_EXTENSION = ".xlsx";
    private static final Set<String> XLSX_CONTENT_TYPES = Set.of(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel", // algunos clientes lo mandan así para xlsx
        "application/octet-stream"  // ciertos browsers no detectan el MIME
    );

    private MultipartFileValidator() {}

    public static void validarXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileProcessingException(
                "El archivo recibido es nulo o está vacío.", List.of());
        }
        String nombre = file.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(XLSX_EXTENSION)) {
            throw new FileProcessingException(
                "Solo se aceptan archivos .xlsx",
                List.of("Archivo recibido: " + (nombre == null ? "(sin nombre)" : nombre)));
        }
        String contentType = file.getContentType();
        if (contentType != null && !XLSX_CONTENT_TYPES.contains(contentType)) {
            throw new FileProcessingException(
                "Content-Type del archivo no es válido para un .xlsx",
                List.of("Content-Type recibido: " + contentType));
        }
    }
}
