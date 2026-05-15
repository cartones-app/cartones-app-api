package com.eliasgonzalez.cartones.pdftemplate.domain.enums;

/**
 * Tipo del template de PDF.
 *
 * <p><b>Sincronizar con CHECK constraint en V6__pdf_template.sql</b> al
 * agregar valores nuevos. Hoy: {@code CHECK (tipo IN ('ETIQUETAS','RESUMEN'))}.
 */
public enum PdfTemplateTipo {
    ETIQUETAS,
    RESUMEN
}
