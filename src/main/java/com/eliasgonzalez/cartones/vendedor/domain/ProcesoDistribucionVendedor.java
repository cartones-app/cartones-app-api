package com.eliasgonzalez.cartones.vendedor.domain;

import com.eliasgonzalez.cartones.vendedor.domain.converter.RangosJsonConverter;
import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de distribución de un vendedor dentro de un proceso específico.
 * Normaliza lo que antes estaba todo junto en la tabla VENDEDORES.
 * La identidad del vendedor vive en la tabla vendedor (entidad Vendedor).
 */
@Entity
@Table(
    name = "proceso_distribucion_vendedor",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pdv_vendedor_proceso",
        columnNames = {"vendedor_id", "proceso_id"}
    )
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ProcesoDistribucionVendedor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    @Column(name = "proceso_id", nullable = false)
    private String procesoId;

    // --- SENETÉ ---
    @Builder.Default
    private Integer cantidadSenete = 0;

    private Integer terminacionSenete;

    @Builder.Default
    private Integer resultadoSenete = 0;

    @Convert(converter = RangosJsonConverter.class)
    @Column(name = "rangos_senete", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> rangosSenete = new ArrayList<>();

    // --- TELEBINGO ---
    @Builder.Default
    private Integer cantidadTelebingo = 0;

    private Integer terminacionTelebingo;

    @Builder.Default
    private Integer resultadoTelebingo = 0;

    @Convert(converter = RangosJsonConverter.class)
    @Column(name = "rangos_telebingo", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> rangosTelebingo = new ArrayList<>();

    // --- DEUDA ---
    @Builder.Default
    private BigDecimal deuda = BigDecimal.ZERO;

    // --- Métodos defensivos para nulos ---

    public void setCantidadSenete(Integer cantidadSenete) {
        this.cantidadSenete = cantidadSenete == null ? 0 : cantidadSenete;
    }

    public void setCantidadTelebingo(Integer cantidadTelebingo) {
        this.cantidadTelebingo = cantidadTelebingo == null ? 0 : cantidadTelebingo;
    }

    public void setResultadoSenete(Integer resultadoSenete) {
        this.resultadoSenete = resultadoSenete == null ? 0 : resultadoSenete;
    }

    public void setResultadoTelebingo(Integer resultadoTelebingo) {
        this.resultadoTelebingo = resultadoTelebingo == null ? 0 : resultadoTelebingo;
    }
}
