package com.eliasgonzalez.cartones.distribucion.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter
@AllArgsConstructor
@ToString
public class RangoLogico {
    private int inicio;
    private int fin;

    public int getCantidad() {
        return (fin - inicio) + 1;
    }

    /**
     * Verifica si un sub-rango [reqInicio, reqFin] cabe completamente aquí.
     */
    public boolean contiene(int reqInicio, int reqFin) {
        return reqInicio >= this.inicio && reqFin <= this.fin;
    }
}