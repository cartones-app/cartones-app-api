package com.eliasgonzalez.cartones.common.flags.domain;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Override runtime para un feature flag, pisando el valor que viene del
 * provider de openflags (classpath:flags.yml).
 *
 * <p>
 * La clave del flag actúa como PK natural — un flag tiene a lo sumo un
 * override activo. {@code DELETE} sobre la fila vuelve el valor efectivo al
 * default del YAML.
 *
 * <p>
 * {@code created_by}/{@code modified_by} dan auditoría gratis vía
 * {@code AuditingEntityListener} (clase base {@link EntidadAuditable}).
 */
@Entity
@Table(name = "flag_override")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FlagOverride extends EntidadAuditable {

    @Id
    @Column(name = "flag_key", length = 128, nullable = false)
    private String flagKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 16, nullable = false)
    private FlagValueType valueType;

    @Column(name = "value_text", nullable = false, columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
}
