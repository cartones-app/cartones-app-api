package com.eliasgonzalez.cartones.ruta.entity;

import com.eliasgonzalez.cartones.shared.entity.EntidadAuditable;
import com.eliasgonzalez.cartones.vendedor.entity.Vendedor;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos completados de un vendedor durante un recorrido de ruta.
 * Se popula al exportar (no durante el recorrido — eso vive en IndexedDB).
 * Las notas se guardan aquí pero no se vuelcan al Excel exportado.
 */
@Entity
@Table(
    name = "sesion_ruta_registro",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_srr_sesion_vendedor",
        columnNames = {"sesion_ruta_id", "vendedor_id"}
    )
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SesionRutaRegistro extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_ruta_id", nullable = false)
    private SesionRuta sesionRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    // Fecha del registro en el Excel (columna FECHA)
    private LocalDate fecha;

    // Columnas de entrada del Excel de ruta
    @Column(name = "senete_total_enviado")
    private Integer seneteTotalEnviado;

    @Column(name = "telebingo_total_enviado")
    private Integer telebingoTotalEnviado;

    @Column(name = "ref_senete")
    private Integer refSenete;

    @Column(name = "ref_telb")
    private Integer refTelb;

    @Column(name = "dev_sen")
    private Integer devSen;

    @Column(name = "dev_telb")
    private Integer devTelb;

    private BigDecimal pago1;

    private BigDecimal pago2;

    // Nota opcional del distribuidor. No se vuelca al Excel exportado.
    // Se muestra en la pantalla de resumen al finalizar el recorrido.
    @Column(columnDefinition = "TEXT")
    private String nota;

    @Builder.Default
    private Boolean completado = false;
}
