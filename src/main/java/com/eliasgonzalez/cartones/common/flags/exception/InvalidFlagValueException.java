package com.eliasgonzalez.cartones.common.flags.exception;

/**
 * Se lanza cuando el valor enviado por el admin no coincide con el tipo del
 * flag.
 */
public class InvalidFlagValueException extends RuntimeException {

    public InvalidFlagValueException(String flagKey, String reason) {
        super("Valor inválido para flag '" + flagKey + "': " + reason);
    }
}
