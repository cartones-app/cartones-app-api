package com.eliasgonzalez.cartones.common.exception;

import java.util.List;

/**
 * 410 Gone — el recurso existió pero ya no está disponible y no va a volver
 * por este mismo path. Distinto de 404 (que sugiere "puede que aparezca
 * después").
 *
 * <p>
 * Caso de uso actual: pedir los datos para regenerar un PDF de un proceso
 * cuyo {@code SimulacionCache} se perdió (reinicio del container entre
 * simulación y descarga). La opción del cliente es re-simular.
 */
public class GoneException extends RuntimeException {

    private final List<String> errorDetails;

    public GoneException(String message, List<String> errorDetails) {
        super(message);
        this.errorDetails = errorDetails;
    }

    public List<String> getErrorDetails() {
        return errorDetails;
    }
}
