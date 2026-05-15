package com.eliasgonzalez.cartones.pdftemplate.controller.dto;

import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

/**
 * Vista del template activo que consume el cliente para generar PDFs.
 * Sin metadata de auditoría — el cliente no la necesita.
 */
public record PdfTemplateActiveDTO(
                String id,
                PdfTemplateTipo tipo,
                String nombre,
                String schemaJson) {
}
