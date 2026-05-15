package com.eliasgonzalez.cartones.pdftemplate.mapper;

import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateActiveDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateDetalleDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateResumenDTO;
import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;

public final class PdfTemplateMapper {

    private PdfTemplateMapper() {
    }

    public static PdfTemplateResumenDTO toResumen(PdfTemplate t) {
        return new PdfTemplateResumenDTO(t.getId(), t.getTipo(), t.getNombre(), t.isActivo(), t.getUpdatedAt());
    }

    public static PdfTemplateDetalleDTO toDetalle(PdfTemplate t) {
        return new PdfTemplateDetalleDTO(
                t.getId(),
                t.getTipo(),
                t.getNombre(),
                t.getSchemaJson(),
                t.isActivo(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    public static PdfTemplateActiveDTO toActive(PdfTemplate t) {
        return new PdfTemplateActiveDTO(t.getId(), t.getTipo(), t.getNombre(), t.getSchemaJson());
    }
}
