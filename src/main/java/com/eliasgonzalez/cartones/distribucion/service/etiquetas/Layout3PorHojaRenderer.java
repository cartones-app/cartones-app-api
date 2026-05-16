package com.eliasgonzalez.cartones.distribucion.service.etiquetas;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layout original: 3 etiquetas por A4. Replica el diseño que existía en
 * {@code EtiquetasPdfService} antes del refactor — mismo tamaño de fuentes,
 * mismas posiciones relativas (-20, -35, -50, etc. desde el tope de la
 * etiqueta), columnas SENETÉ + TELEBINGO centradas en sus mitades.
 */
@Component
public class Layout3PorHojaRenderer implements EtiquetaLayoutRenderer {

    @Override
    public LayoutEtiqueta getLayout() {
        return LayoutEtiqueta.TRES_POR_HOJA;
    }

    @Override
    public void dibujarEtiqueta(
            PdfContentByte cb,
            ContextoEtiqueta ctx,
            float x, float y, float ancho, float alto) {

        if (ctx.etiqueta() == null) {
            // Slot vacío (último grupo en orden intercalado). Sin recuadro.
            return;
        }
        EtiquetaDTO item = ctx.etiqueta();
        BaseFont helv = ctx.fontNormal();
        BaseFont bold = ctx.fontBold();
        float yTop = y + alto;

        // A. Recuadro exterior
        cb.setLineWidth(1f);
        cb.rectangle(x, y, ancho, alto);
        cb.stroke();

        // B. Número de vendedor (esquina superior derecha)
        cb.beginText();
        cb.setFontAndSize(bold, 24);
        cb.showTextAligned(Element.ALIGN_RIGHT, "#" + item.getNumeroVendedor(), x + ancho - 5, yTop - 25, 0);
        cb.endText();

        // C. Cabecera izquierda (datos fijos del distribuidor)
        cb.beginText();
        cb.setFontAndSize(bold, 10);
        cb.setTextMatrix(x + 10, yTop - 20);
        cb.showText("ROBERTO GONZÁLEZ");
        cb.setTextMatrix(x + 10, yTop - 35);
        cb.showText("DIST - ITAUGUÁ - PY");
        cb.setTextMatrix(x + 10, yTop - 50);
        cb.showText("0983 433572");
        cb.endText();

        // D. Cabecera derecha (fechas)
        cb.beginText();
        cb.setFontAndSize(bold, 10);
        float xFechas = x + ancho - 65;
        if (ctx.fechasIguales()) {
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO: " + ctx.textoFechaSenete(), xFechas, yTop - 35, 0);
        } else {
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO SENETÉ: " + ctx.textoFechaSenete(),    xFechas, yTop - 25, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO TELEBINGO: " + ctx.textoFechaTelebingo(), xFechas, yTop - 40, 0);
        }
        cb.endText();

        // E. Línea divisoria
        cb.moveTo(x, yTop - 60);
        cb.lineTo(x + ancho, yTop - 60);
        cb.stroke();

        // F. Nombre del vendedor (grande y centrado)
        cb.beginText();
        cb.setFontAndSize(bold, 14);
        cb.showTextAligned(Element.ALIGN_CENTER,
                item.getNombre() != null ? item.getNombre().toUpperCase() : "",
                x + ancho / 2f, yTop - 80, 0);
        cb.endText();

        // G. Columnas SENETÉ + TELEBINGO
        float xCentroSenete    = x + 80;
        float xCentroTelebingo = x + ancho - 80;
        float yInicioColumnas  = yTop - 120;

        dibujarColumna(cb, "SENETÉ",    item.getSeneteRangos(),    item.getSeneteCartones(),    item.getResultadoSenete(),
                xCentroSenete, yInicioColumnas, helv, bold);
        dibujarColumna(cb, "TELEBINGO", item.getTelebingoRangos(), item.getTelebingoCartones(), item.getResultadoTelebingo(),
                xCentroTelebingo, yInicioColumnas, helv, bold);

        // H. Saldo (centrado abajo)
        cb.beginText();
        cb.setFontAndSize(bold, 12);
        cb.showTextAligned(Element.ALIGN_CENTER, "SALDO", x + ancho / 2f, y + 60, 0);
        cb.setFontAndSize(helv, 11);
        cb.showTextAligned(Element.ALIGN_CENTER,
                "Gs. " + (item.getSaldo() != null ? item.getSaldo() : "0"),
                x + ancho / 2f, y + 45, 0);
        cb.endText();
    }

    private void dibujarColumna(
            PdfContentByte cb,
            String titulo,
            List<String> rangos,
            String total,
            String resultado,
            float xCentro,
            float yInicio,
            BaseFont fNorm,
            BaseFont fBold) {
        float curY = yInicio;

        cb.beginText();

        // 1. Título
        cb.setFontAndSize(fBold, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, titulo, xCentro, curY, 0);
        curY -= 15;

        // 2. Lista de rangos (con compresión si son muchos)
        cb.setFontAndSize(fNorm, 10);
        if (rangos != null && rangos.size() > 4) {
            cb.setFontAndSize(fNorm, 9);
        }
        if (rangos != null) {
            for (String r : rangos) {
                cb.showTextAligned(Element.ALIGN_CENTER, r, xCentro, curY, 0);
                curY -= 12;
            }
        }

        // 3. Total
        cb.setFontAndSize(fBold, 10);
        cb.showTextAligned(Element.ALIGN_CENTER,
                "TOTAL ------------> (" + (total != null ? total : "0") + ")",
                xCentro, curY, 0);

        curY -= 25;

        // 4. Resultados
        cb.setFontAndSize(fBold, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, "RESULTADOS", xCentro, curY, 0);
        cb.setFontAndSize(fNorm, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, resultado != null ? resultado : "0", xCentro, curY - 15, 0);

        cb.endText();
    }
}
