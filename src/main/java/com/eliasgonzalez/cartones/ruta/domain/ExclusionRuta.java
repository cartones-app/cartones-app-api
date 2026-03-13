package com.eliasgonzalez.cartones.ruta.domain;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;

/**
 * Exclusión dinámica para el flujo de ruta.
 * Los nombres en esta lista se omiten al parsear el Excel de ruta,
 * independientemente de su posición (siempre que no superen el límite TOTAL).
 * Gestionada desde /api/admin/ruta/exclusiones por el rol ADMIN.
 * Pre-cargada con: RECIBO DE CARTONES, VENTA LOCAL.
 */
@Entity
@Table(name = "exclusion_ruta")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ExclusionRuta extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    private String descripcion;

    @Builder.Default
    private Boolean activo = true;
}
