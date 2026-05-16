package com.eliasgonzalez.cartones.distribucion.service.etiquetas;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;

/**
 * Renderer de una etiqueta individual sobre el canvas de OpenPDF. Cada
 * implementación maneja un valor de {@link LayoutEtiqueta} (3 o 4 por hoja)
 * y sabe dibujarse dentro de un rectángulo dado.
 *
 * <p>El orquestador ({@code EtiquetasPdfService}) decide cuántas hojas crear,
 * dónde está cada slot, y cuándo cambiar de página. Cada renderer solo se
 * preocupa por <em>cómo</em> se ve una etiqueta dentro de su rectángulo.
 *
 * <p>Para agregar un layout nuevo en el futuro: crear una implementación más,
 * sin tocar el orquestador ni los renderers existentes.
 */
public interface EtiquetaLayoutRenderer {

    /** El valor de layout que maneja este renderer. */
    LayoutEtiqueta getLayout();

    /** Cantidad de slots (etiquetas) que entran en una hoja A4. */
    default int getSlotsPorHoja() {
        return getLayout().getSlotsPorHoja();
    }

    /**
     * Dibuja una etiqueta dentro del rectángulo dado. Coordenadas iText:
     * origen abajo-izquierda; {@code y} es el borde inferior de la etiqueta.
     *
     * @param cb        canvas de OpenPDF
     * @param contexto  contexto compartido (etiqueta, fechas, fuentes)
     * @param x         borde izquierdo
     * @param y         borde inferior
     * @param ancho     ancho del rectángulo (puede no usar todo)
     * @param alto      alto del rectángulo
     */
    void dibujarEtiqueta(
            PdfContentByte cb,
            ContextoEtiqueta contexto,
            float x, float y, float ancho, float alto);

    /**
     * Contexto inmutable pasado a cada llamada {@link #dibujarEtiqueta}.
     * Mantiene las cosas que no cambian dentro del PDF (fechas, fuentes) y
     * la etiqueta puntual. {@code etiqueta} puede ser {@code null} para slots
     * vacíos del último grupo en orden intercalado — el renderer decide qué
     * dibujar (o nada).
     */
    record ContextoEtiqueta(
            EtiquetaDTO etiqueta,
            String textoFechaSenete,
            String textoFechaTelebingo,
            boolean fechasIguales,
            BaseFont fontNormal,
            BaseFont fontBold) {
    }
}
