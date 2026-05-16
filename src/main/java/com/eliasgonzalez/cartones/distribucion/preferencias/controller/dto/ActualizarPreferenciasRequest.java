package com.eliasgonzalez.cartones.distribucion.preferencias.controller.dto;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;

import jakarta.validation.constraints.NotNull;

/**
 * Payload de PUT para setear las preferencias.
 *
 * <p>Ambos campos requeridos: no permitimos updates parciales para evitar el
 * caso "el admin solo quería cambiar el orden, no el layout" — si quiere
 * eso, manda igual los dos. Cliente debe leer GET primero si necesita
 * preservar el otro valor.
 */
public record ActualizarPreferenciasRequest(
        @NotNull LayoutEtiqueta layoutEtiqueta,
        @NotNull OrdenEtiqueta ordenEtiqueta) {
}
