package com.eliasgonzalez.cartones.distribucion.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EstadoEnum {

    PENDIENTE("pendiente"),
    VERIFICANDO("verificando"),
    COMPLETADO("completado");

    @JsonValue
    private final String value;
}
