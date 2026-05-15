package com.eliasgonzalez.cartones.pdftemplate.controller.dto;

import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PdfTemplateCreateDTO(
        @NotNull PdfTemplateTipo tipo,
        @NotBlank @Size(max = 128) String nombre,
        @NotBlank String schemaJson) {
}
