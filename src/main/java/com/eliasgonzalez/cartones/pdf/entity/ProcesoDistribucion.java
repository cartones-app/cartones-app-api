package com.eliasgonzalez.cartones.pdf.entity;

import com.eliasgonzalez.cartones.pdf.enums.EstadoEnum;
import com.eliasgonzalez.cartones.shared.entity.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Proceso de distribución de cartones. Guarda el estado del proceso
 * y los PDFs generados (etiquetas + resumen) como BLOBs.
 * Reemplaza la tabla PROCESOS_PDF con un esquema normalizado y auditado.
 */
@Entity
@Table(name = "proceso_distribucion")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ProcesoDistribucion extends EntidadAuditable {

    @Id
    private String procesoId;

    @Builder.Default
    private String estado = EstadoEnum.PENDIENTE.getValue();

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "pdf_etiquetas")
    private byte[] pdfEtiquetas;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "pdf_resumen")
    private byte[] pdfResumen;

    @Version
    private Long version;

    public void setEstado(String estado) {
        this.estado = estado == null ? EstadoEnum.PENDIENTE.getValue() : estado;
    }
}
