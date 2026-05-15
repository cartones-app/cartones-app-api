package com.eliasgonzalez.cartones.pdftemplate.controller.dto;

import java.time.LocalDateTime;

import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

public record PdfTemplateDetalleDTO(
                String id,
                PdfTemplateTipo tipo,
                String nombre,
                String schemaJson,
                boolean activo,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {
}
