package com.eliasgonzalez.cartones.distribucion.preferencias.controller.dto;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;

import java.time.LocalDateTime;

/**
 * Vista de las preferencias de etiquetas de un distribuidor. Misma forma en
 * GET y respuesta a PUT, tanto en /api/me/... como en /api/admin/....
 */
public record PreferenciasEtiquetasDTO(
        String username,
        LayoutEtiqueta layoutEtiqueta,
        OrdenEtiqueta ordenEtiqueta,
        LocalDateTime updatedAt,
        String modifiedBy) {

    public static PreferenciasEtiquetasDTO fromEntity(PreferenciasDistribuidor p) {
        return new PreferenciasEtiquetasDTO(
                p.getUsername(),
                p.getLayoutEtiqueta(),
                p.getOrdenEtiqueta(),
                p.getUpdatedAt(),
                p.getModifiedBy());
    }
}
