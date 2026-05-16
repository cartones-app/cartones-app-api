package com.eliasgonzalez.cartones.distribucion.preferencias.domain;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;

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
 * Preferencias de impresión de etiquetas por distribuidor.
 *
 * <p>{@link #username} = {@code preferred_username} del JWT de Keycloak.
 * Coincide con {@code created_by} de las entidades auditables (ver
 * {@code SecurityConfig.jwtAuthenticationConverter}, que setea
 * {@code principalClaimName=preferred_username}). Eso permite asociar un
 * proceso con la preferencia de su creador sin tabla {@code users} ni FK
 * contra Keycloak.
 */
@Entity
@Table(name = "preferencias_distribuidor")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PreferenciasDistribuidor extends EntidadAuditable {

    @Id
    @Column(name = "username", length = 255, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_etiqueta", length = 32, nullable = false)
    private LayoutEtiqueta layoutEtiqueta;

    @Enumerated(EnumType.STRING)
    @Column(name = "orden_etiqueta", length = 32, nullable = false)
    private OrdenEtiqueta ordenEtiqueta;
}
