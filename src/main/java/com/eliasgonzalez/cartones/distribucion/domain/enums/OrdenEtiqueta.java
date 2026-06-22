package com.eliasgonzalez.cartones.distribucion.domain.enums;

/**
 * Orden en que se imprimen las etiquetas dentro del PDF.
 *
 * <p><strong>SECUENCIAL</strong>: 1,2,3 en la primera hoja; 4,5,6 en la segunda; etc.
 * Lectura natural de arriba a abajo, hoja por hoja.
 *
 * <p><strong>INTERCALADO</strong>: pensado para el flujo de "apilar varias hojas y
 * cortar todas de una vez". Si se imprimen K hojas con L slots cada una, el
 * orden queda tal que tras apilar y cortar quedan L pilas en orden secuencial:
 * pila 1 = [1..K], pila 2 = [K+1..2K], etc. Para lograrlo, en hoja {@code p}
 * slot {@code s} (1-indexed) se imprime la etiqueta número {@code (s-1)·K + p}.
 */
public enum OrdenEtiqueta {

    SECUENCIAL,
    INTERCALADO;

    public static OrdenEtiqueta defaultValue() {
        return SECUENCIAL;
    }
}
