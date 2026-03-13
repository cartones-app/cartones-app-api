package com.eliasgonzalez.cartones.ruta.entity;

import com.eliasgonzalez.cartones.ruta.entity.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.common.audit.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sesión de recorrido de ruta. Registra quién salió, cuándo,
 * cuántos registros procesó y el estado final del recorrido.
 * El Excel original se guarda como BLOB para la exportación posterior.
 * Los estados individuales por registro viven en el frontend (IndexedDB)
 * y se persisten en SesionRutaRegistro solo al exportar.
 */
@Entity
@Table(name = "sesion_ruta")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SesionRuta extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sesion_id", nullable = false, unique = true)
    private String sesionId;

    // Fecha o fechas seleccionadas al cargar el Excel (guardadas como String)
    @Column(name = "fecha_filtro", nullable = false)
    private String fechaFiltro;

    @Builder.Default
    private String estado = EstadoSesionEnum.ACTIVA.getValor();

    @Builder.Default
    @Column(name = "total_registros")
    private Integer totalRegistros = 0;

    @Builder.Default
    @Column(name = "registros_completados")
    private Integer registrosCompletados = 0;

    // Excel original para la exportación. Se guarda al crear la sesión.
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "archivo_excel")
    private byte[] archivoExcel;

    // Locking optimista para evitar doble exportación concurrente
    @Version
    private Long version;
}
