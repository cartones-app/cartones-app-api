package com.eliasgonzalez.cartones.ruta.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EstadoSesionEnum {
    ACTIVA("ACTIVA"),
    COMPLETADA("COMPLETADA"),
    ABANDONADA("ABANDONADA"),
    /**
     * Estado terminal post-retención: el BLOB del Excel ya fue purgado.
     * Solo queda metadata (id, sesionId, fechas, totales). Las queries
     * normales no ven sesiones ARCHIVADA gracias al @SQLRestriction
     * en SesionRuta.
     */
    ARCHIVADA("ARCHIVADA");

    private final String valor;
}
