package com.eliasgonzalez.cartones.distribucion.service.etiquetas;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layout compacto: 4 etiquetas por A4. Mismo contenido que
 * {@link Layout3PorHojaRenderer} pero con tipografía y espaciados reducidos
 * para entrar en ~3/4 del alto.
 *
 * <p>Ajustes vs 3-por-hoja:
 * <ul>
 *   <li>Cabecera comprimida: padding superior 14 (vs 20), interlineado 12 (vs 15).</li>
 *   <li>Número de vendedor: tamaño 20 (vs 24).</li>
 *   <li>Nombre del vendedor: tamaño 12 (vs 14), bajado a -65 (vs -80).</li>
 *   <li>Columnas SENETÉ/TELEBINGO arrancan en -95 (vs -120) y usan interlineado 10 (vs 12).</li>
 *   <li>Saldo: sube a y+50/y+38 (vs y+60/y+45) para no chocar con las columnas.</li>
 * </ul>
 */
@Component
public class Layout4PorHojaRenderer implements EtiquetaLayoutRenderer {

    @Override
    public LayoutEtiqueta getLayout() {
        return LayoutEtiqueta.CUATRO_POR_HOJA;
    }

    @Override
    public void dibujarEtiqueta(
            PdfContentByte cb,
            ContextoEtiqueta ctx,
            float x, float y, float ancho, float alto) {

        if (ctx.etiqueta() == null) {
            return;
        }
        EtiquetaDTO item = ctx.etiqueta();
        BaseFont helv = ctx.fontNormal();
        BaseFont bold = ctx.fontBold();
        float yTop = y + alto;

        cb.setLineWidth(1f);
        cb.rectangle(x, y, ancho, alto);
        cb.stroke();

        // Número vendedor (más chico, esquina sup derecha)
        cb.beginText();
        cb.setFontAndSize(bold, 20);
        cb.showTextAligned(Element.ALIGN_RIGHT, "#" + item.getNumeroVendedor(), x + ancho - 5, yTop - 20, 0);
        cb.endText();

        // Cabecera izquierda comprimida
        cb.beginText();
        cb.setFontAndSize(bold, 9);
        cb.setTextMatrix(x + 10, yTop - 14);
        cb.showText("ROBERTO GONZÁLEZ");
        cb.setTextMatrix(x + 10, yTop - 26);
        cb.showText("DIST - ITAUGUÁ - PY");
        cb.setTextMatrix(x + 10, yTop - 38);
        cb.showText("0983 433572");
        cb.endText();

        // Cabecera derecha (fechas)
        cb.beginText();
        cb.setFontAndSize(bold, 9);
        float xFechas = x + ancho - 65;
        if (ctx.fechasIguales()) {
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO: " + ctx.textoFechaSenete(), xFechas, yTop - 26, 0);
        } else {
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO SENETÉ: " + ctx.textoFechaSenete(),    xFechas, yTop - 20, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO TELEBINGO: " + ctx.textoFechaTelebingo(), xFechas, yTop - 32, 0);
        }
        cb.endText();

        // Divisoria un poco más arriba
        cb.moveTo(x, yTop - 48);
        cb.lineTo(x + ancho, yTop - 48);
        cb.stroke();

        // Nombre vendedor
        cb.beginText();
        cb.setFontAndSize(bold, 12);
        cb.showTextAligned(Element.ALIGN_CENTER,
                item.getNombre() != null ? item.getNombre().toUpperCase() : "",
                x + ancho / 2f, yTop - 65, 0);
        cb.endText();

        // Columnas (más pegadas verticalmente)
        float xCentroSenete    = x + 80;
        float xCentroTelebingo = x + ancho - 80;
        float yInicioColumnas  = yTop - 95;

        dibujarColumna(cb, "SENETÉ",    item.getSeneteRangos(),    item.getSeneteCartones(),    item.getResultadoSenete(),
                xCentroSenete, yInicioColumnas, helv, bold);
        dibujarColumna(cb, "TELEBINGO", item.getTelebingoRangos(), item.getTelebingoCartones(), item.getResultadoTelebingo(),
                xCentroTelebingo, yInicioColumnas, helv, bold);

        // Saldo
        cb.beginText();
        cb.setFontAndSize(bold, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, "SALDO", x + ancho / 2f, y + 50, 0);
        cb.setFontAndSize(helv, 10);
        cb.showTextAligned(Element.ALIGN_CENTER,
                "Gs. " + (item.getSaldo() != null ? item.getSaldo() : "0"),
                x + ancho / 2f, y + 38, 0);
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

        cb.setFontAndSize(fBold, 10);
        cb.showTextAligned(Element.ALIGN_CENTER, titulo, xCentro, curY, 0);
        curY -= 12;

        cb.setFontAndSize(fNorm, 9);
        if (rangos != null && rangos.size() > 4) {
            cb.setFontAndSize(fNorm, 8);
        }
        if (rangos != null) {
            for (String r : rangos) {
                cb.showTextAligned(Element.ALIGN_CENTER, r, xCentro, curY, 0);
                curY -= 10;
            }
        }

        cb.setFontAndSize(fBold, 9);
        cb.showTextAligned(Element.ALIGN_CENTER,
                "TOTAL ------------> (" + (total != null ? total : "0") + ")",
                xCentro, curY, 0);

        curY -= 20;

        cb.setFontAndSize(fBold, 10);
        cb.showTextAligned(Element.ALIGN_CENTER, "RESULTADOS", xCentro, curY, 0);
        cb.setFontAndSize(fNorm, 10);
        cb.showTextAligned(Element.ALIGN_CENTER, resultado != null ? resultado : "0", xCentro, curY - 13, 0);

        cb.endText();
    }
}
