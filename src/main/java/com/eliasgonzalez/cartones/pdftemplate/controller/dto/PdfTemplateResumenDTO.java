package com.eliasgonzalez.cartones.pdftemplate.controller.dto;

import java.time.LocalDateTime;

import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

/**
 * Vista resumida de un template para listados. NO incluye {@code schemaJson}
 * porque puede ser grande (KB / decenas de KB) y la lista solo necesita
 * metadata para mostrar la tabla.
 */
public record PdfTemplateResumenDTO(
                String id,
                PdfTemplateTipo tipo,
                String nombre,
                boolean activo,
                LocalDateTime updatedAt) {
}
