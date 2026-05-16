package com.eliasgonzalez.cartones.distribucion.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cantidad de etiquetas que entran en una hoja A4 al generar el PDF.
 * Acoplado al renderer correspondiente: cada valor tiene un {@link
 * com.eliasgonzalez.cartones.distribucion.service.etiquetas.EtiquetaLayoutRenderer}
 * que sabe dibujar exactamente esa cantidad.
 */
@AllArgsConstructor
@Getter
public enum LayoutEtiqueta {

    TRES_POR_HOJA(3),
    CUATRO_POR_HOJA(4);

    /** Cantidad de slots (etiquetas) por hoja A4. */
    private final int slotsPorHoja;

    public static LayoutEtiqueta defaultValue() {
        return TRES_POR_HOJA;
    }
}
