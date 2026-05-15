package com.eliasgonzalez.cartones.pdftemplate.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PdfTemplateUpdateDTO(
                @NotBlank @Size(max = 128) String nombre,
                @NotBlank String schemaJson) {
}
