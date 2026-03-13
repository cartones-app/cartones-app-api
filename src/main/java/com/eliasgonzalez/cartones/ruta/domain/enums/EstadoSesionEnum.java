package com.eliasgonzalez.cartones.ruta.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EstadoSesionEnum {

    ACTIVA("ACTIVA"),
    COMPLETADA("COMPLETADA"),
    ABANDONADA("ABANDONADA");

    private final String valor;
}
