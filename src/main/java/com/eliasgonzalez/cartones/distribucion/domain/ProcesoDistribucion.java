package com.eliasgonzalez.cartones.distribucion.domain;

import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @Column(name = "archivos_generados_en")
    private LocalDateTime archivosGeneradosEn;

    @Column(name = "archivos_borrados_en")
    private LocalDateTime archivosBorradosEn;

    @Version
    private Long version;

    public void setEstado(String estado) {
        this.estado = estado == null ? EstadoEnum.PENDIENTE.getValue() : estado;
    }
}
