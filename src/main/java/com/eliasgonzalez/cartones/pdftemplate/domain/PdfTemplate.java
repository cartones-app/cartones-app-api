package com.eliasgonzalez.cartones.pdftemplate.domain;

import java.util.UUID;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Template de PDF editable desde {@code /admin/pdf-templates}.
 *
 * <p>
 * El {@code schemaJson} contiene el {@code Template} de pdfme serializado
 * ({@code { basePdf, schemas: [...] }}). El cliente hace {@code JSON.parse}
 * y se lo pasa a {@code @pdfme/generator.generate} junto con los datos.
 *
 * <p>
 * En Fase 1 hay un único activo por tipo (índice parcial enforced en V6).
 * En Fase 3 se relaja para multi-template.
 */
@Entity
@Table(name = "pdf_template")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PdfTemplate extends EntidadAuditable {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 16, nullable = false)
    private PdfTemplateTipo tipo;

    @Column(name = "nombre", length = 128, nullable = false)
    private String nombre;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Genera el UUID si no se asignó explícitamente. Mantiene control en el service
     * para tests.
     */
    @PrePersist
    void asignarIdSiHaceFalta() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
