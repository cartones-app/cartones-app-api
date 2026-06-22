package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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
    @PositiveOrZero(message = "numeroFila no puede ser negativo")
    private int numeroFila;

    // Campos de entrada — el usuario los completa durante el recorrido
    // Vienen con el valor actual del Excel si ya tienen alguno
    @Min(value = 0, message = "seneteTotalEnviado no puede ser negativo")
    private Integer seneteTotalEnviado;
    @Min(value = 0, message = "telebingoTotalEnviado no puede ser negativo")
    private Integer telebingoTotalEnviado;
    @Min(value = 0, message = "refSenete no puede ser negativo")
    private Integer refSenete;
    @Min(value = 0, message = "refTelb no puede ser negativo")
    private Integer refTelb;
    @Min(value = 0, message = "devSen no puede ser negativo")
    private Integer devSen;
    @Min(value = 0, message = "devTelb no puede ser negativo")
    private Integer devTelb;
    @DecimalMin(value = "0.0", inclusive = true, message = "pago1 no puede ser negativo")
    @Digits(integer = 12, fraction = 2, message = "pago1 con formato inválido")
    private BigDecimal pago1;
    @DecimalMin(value = "0.0", inclusive = true, message = "pago2 no puede ser negativo")
    @Digits(integer = 12, fraction = 2, message = "pago2 con formato inválido")
    private BigDecimal pago2;

    // Nota del distribuidor — solo se envía en el request de exportación, nunca en la respuesta de E1
    @Size(max = 1000, message = "La nota no puede superar 1000 caracteres")
    private String nota;
}
