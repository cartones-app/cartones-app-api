package com.eliasgonzalez.cartones.distribucion.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RangoLogico {
    private int inicio;
    private int fin;

    /**
     * Cantidad de números en el rango (inclusive). Si {@code fin} e {@code inicio}
     * vienen de input externo (request), una resta directa podría overflow/underflow
     * en {@code int}. Usamos {@link Math#subtractExact} y {@link Math#addExact} y
     * propagamos como {@link IllegalArgumentException} para que el caller decida.
     */
    public int getCantidad() {
        try {
            return Math.addExact(Math.subtractExact(fin, inicio), 1);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Rango inválido: fin=" + fin + ", inicio=" + inicio + " produce overflow", ex);
        }
    }

    /**
     * Verifica si un sub-rango [reqInicio, reqFin] cabe completamente aquí.
     */
    public boolean contiene(int reqInicio, int reqFin) {
        return reqInicio >= this.inicio && reqFin <= this.fin;
    }
}
