package com.eliasgonzalez.cartones.ruta.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EliminarSesionesRequestDTO {

    @NotEmpty(message = "La lista de sesionIds no puede estar vacía")
    private List<String> sesionIds;
}
