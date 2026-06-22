package com.eliasgonzalez.cartones.ruta.controller.dto;

import lombok.*;

import java.util.List;

/**
 * Respuesta del endpoint POST /api/ruta/carga.
 * El frontend usa el sesionId para todos los pasos siguientes.
 * Las fechas disponibles se muestran al usuario para que elija con cuál(es) trabajar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CargaRutaResponseDTO {

    private String sesionId;

    // Fechas únicas encontradas en la columna FECHA del Excel (formato "dd/MM/yyyy" o como vengan)
    private List<String> fechasDisponibles;
}
