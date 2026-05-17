package com.eliasgonzalez.cartones.distribucion.configuracion.domain;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "configuracion_archivos")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ConfiguracionArchivos extends EntidadAuditable {

    @Id
    private Long id;

    @Column(name = "retencion_meses", nullable = false)
    private int retencionMeses;

    @Column(name = "eliminacion_activa", nullable = false)
    private boolean eliminacionActiva;
}
