package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Request del endpoint POST /api/ruta/{sesionId}/registros.
 * El frontend envía la(s) fecha(s) seleccionada(s) por el usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FiltroFechaRequestDTO {

    @NotEmpty(message = "Debe seleccionar al menos una fecha")
    private List<String> fechas;
}
