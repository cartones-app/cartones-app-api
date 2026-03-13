package com.eliasgonzalez.cartones.vendedor.entity;

import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entidad maestra de vendedor. Solo contiene la identidad (quién es).
 * Los datos de distribución por proceso viven en ProcesoDistribucionVendedor.
 * Los datos de recorrido de ruta viven en SesionRutaRegistro.
 */
@Entity
@Table(name = "vendedor")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Vendedor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;
}
