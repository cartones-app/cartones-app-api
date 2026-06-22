package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Request del endpoint POST /api/ruta/{sesionId}/exportar.
 * El frontend envía los registros completados durante el recorrido.
 * Cada registro incluye los valores ingresados y, opcionalmente, una nota.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportarRutaRequestDTO {

    @Valid
    @NotEmpty(message = "Debe incluir al menos un registro para exportar")
    private List<RegistroRutaDTO> registros;
}
