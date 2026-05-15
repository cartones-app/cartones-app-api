package com.eliasgonzalez.cartones.common.flags.exception;

/**
 * Se lanza cuando el admin opera sobre un flag que no está registrado en
 * {@code FlagRegistry}.
 */
public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException(String flagKey) {
        super("Flag no registrado: " + flagKey);
    }
}
