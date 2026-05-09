package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Un registro del Excel de ruta para mostrar en el frontend.
 * El frontend muestra los campos de referencia como solo lectura
 * y permite editar los campos de entrada.
 * El numeroFila se usa al exportar para escribir en la fila correcta del Excel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroRutaDTO {

    // Identidad del vendedor en la tabla maestra (para FK en SesionRutaRegistro)
    private Long vendedorId;

    // Campos de referencia (solo lectura en el frontend)
    private String nombre;
    private String fecha;
    private BigDecimal deudaAnterior;

    // Número de fila en el Excel (base 0, fila real = numeroFila + 1)
    // Necesario para escribir de vuelta al exportar
    private int numeroFila;

    // Campos de entrada — el usuario los completa durante el recorrido
    // Vienen con el valor actual del Excel si ya tienen alguno
    private Integer seneteTotalEnviado;
    private Integer telebingoTotalEnviado;
    private Integer refSenete;
    private Integer refTelb;
    private Integer devSen;
    private Integer devTelb;
    private BigDecimal pago1;
    private BigDecimal pago2;

    // Nota del distribuidor — solo se envía en el request de exportación, nunca en la respuesta de E1
    @Size(max = 1000, message = "La nota no puede superar 1000 caracteres")
    private String nota;
}
