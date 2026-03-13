package com.eliasgonzalez.cartones.pdf.service;

import com.eliasgonzalez.cartones.pdf.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.common.exception.PdfCreationException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio encargado de la generación física del documento PDF conteniendo las etiquetas
 * de distribución para los vendedores.
 * <p>
 * Utiliza la librería OpenPDF (com.lowagie) para el dibujo de bajo nivel (canvas).
 * Diseño optimizado para hoja A4 con 3 etiquetas por página.
 */
@Service
@Transactional
public class PdfEtiquetasService {

    /**
     * Genera un arreglo de bytes (PDF) con las etiquetas formateadas.
     *
     * @param etiquetas      Lista de DTOs con la información de cada vendedor.
     * @param fechaSenete    Fecha del sorteo de Seneté.
     * @param fechaTelebingo Fecha del sorteo de Telebingo.
     * @return byte[] conteniendo el archivo PDF.
     */
    public byte[] generarEtiquetas(List<EtiquetaDTO> etiquetas, LocalDate fechaSenete, LocalDate fechaTelebingo) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Si la lista de etiquetas está vacía, genera un PDF vacío válido.
            if (etiquetas == null || etiquetas.isEmpty()) {
                Document document = new Document(PageSize.A4);
                PdfWriter.getInstance(document, baos);
                document.open();
                document.add(new com.lowagie.text.Paragraph("No hay etiquetas para generar.")); // Añadir al menos algo
                document.close();
                return baos.toByteArray();
            }

            // Configuración del documento A4 sin márgenes automáticos (manejados manualmente)
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            document.open();
            PdfContentByte cb = writer.getDirectContent();

            // --- 1. CONFIGURACIÓN DE DIMENSIONES Y CONSTANTES ---
            float width = PageSize.A4.getWidth();
            float height = PageSize.A4.getHeight();
            float margen = 20;    // Margen lateral de la hoja
            float espV = 15;      // Espacio vertical entre etiquetas

            // Cálculo: (AltoTotal - MárgenesSupInf - EspaciosEntreEtiquetas) / 3 etiquetas
            float altoEt = (height - 2 * margen - 2 * espV) / 3;

            // --- 2. DEFINICIÓN DE FUENTES ---
            BaseFont helv = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            // --- 3. FORMATEO DE FECHAS ---
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String txtFechaSenete = fechaSenete.format(fmt);
            String txtFechaTelebingo = fechaTelebingo.format(fmt);
            boolean fechasIguales = fechaSenete.isEqual(fechaTelebingo);

            // --- 4. BUCLE DE GENERACIÓN ---
            for (int i = 0; i < etiquetas.size(); i++) {
                EtiquetaDTO item = etiquetas.get(i);

                // Cálculo de la posición Y base de la etiqueta actual (Coordenadas iText: 0,0 es abajo-izq)
                // Se invierte la lógica para dibujar de arriba hacia abajo.
                float y = height - margen - ((i % 3) + 1) * altoEt - (i % 3) * espV;

                // A. RECUADRO EXTERIOR
                cb.setLineWidth(1f);
                cb.rectangle(margen, y, width - 2 * margen, altoEt);
                cb.stroke();

                // B. NÚMERO DE VENDEDOR (Esquina Superior Derecha)
                cb.beginText();
                cb.setFontAndSize(bold, 24);
                // Alineado a la derecha con un padding de 5pt
                cb.showTextAligned(Element.ALIGN_RIGHT, "#" + item.getNumeroVendedor(), width - margen - 5, y + altoEt - 25, 0);
                cb.endText();

                // C. CABECERA IZQUIERDA (Datos Fijos del Distribuidor)
                cb.beginText();
                cb.setFontAndSize(bold, 10);
                cb.setTextMatrix(margen + 10, y + altoEt - 20);
                cb.showText("ROBERTO GONZÁLEZ");

                cb.setTextMatrix(margen + 10, y + altoEt - 35);
                cb.showText("DIST - ITAUGUÁ - PY");

                cb.setTextMatrix(margen + 10, y + altoEt - 50);
                cb.showText("0983 433572");
                cb.endText();

                // D. CABECERA DERECHA (Fechas de Sorteo)
                cb.beginText();
                cb.setFontAndSize(bold, 10);
                float xFechas = width - margen - 65; // Posición X para las fechas

                if (fechasIguales) {
                    cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO: " + txtFechaSenete, xFechas, y + altoEt - 35, 0);
                } else {
                    cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO SENETÉ: " + txtFechaSenete, xFechas, y + altoEt - 25, 0);
                    cb.showTextAligned(Element.ALIGN_RIGHT, "SORTEO TELEBINGO: " + txtFechaTelebingo, xFechas, y + altoEt - 40, 0);
                }
                cb.endText();

                // E. LÍNEA DIVISORIA (Separa cabecera del cuerpo)
                cb.moveTo(margen, y + altoEt - 60);
                cb.lineTo(width - margen, y + altoEt - 60);
                cb.stroke();

                // F. NOMBRE DEL VENDEDOR (Grande y Centrado)
                cb.beginText();
                cb.setFontAndSize(bold, 14);
                // Posición Y ajustada a -80 del tope de la etiqueta
                cb.showTextAligned(Element.ALIGN_CENTER, item.getNombre() != null ? item.getNombre().toUpperCase() : "", width / 2, y + altoEt - 80, 0);
                cb.endText();

                // G. COLUMNAS DE DETALLE (Lógica de Simetría)
                // Definimos los centros de las columnas equidistantes de los márgenes
                float xCentroSenete = margen + 80;
                float xCentroTelebingo = width - margen - 80;

                // AJUSTE VISUAL: Bajamos el inicio de las columnas (-120) para dar aire respecto al nombre
                float yInicioColumnas = y + altoEt - 120;

                // Dibujar Columna Izquierda (Seneté)
                dibujarColumnaCentrada(cb, "SENETÉ", item.getSeneteRangos(), item.getSeneteCartones(), item.getResultadoSenete(),
                        xCentroSenete, yInicioColumnas, helv, bold);

                // Dibujar Columna Derecha (Telebingo)
                dibujarColumnaCentrada(cb, "TELEBINGO", item.getTelebingoRangos(), item.getTelebingoCartones(), item.getResultadoTelebingo(),
                        xCentroTelebingo, yInicioColumnas, helv, bold);

                // H. SALDO (Centrado en la parte inferior)
                // AJUSTE VISUAL: Subimos el saldo (+60 y +45) para alejarlo del borde inferior
                cb.beginText();
                cb.setFontAndSize(bold, 12);
                cb.showTextAligned(Element.ALIGN_CENTER, "SALDO", width / 2, y + 60, 0);

                cb.setFontAndSize(helv, 11);
                cb.showTextAligned(Element.ALIGN_CENTER, "Gs. " + (item.getSaldo() != null ? item.getSaldo() : "0"), width / 2, y + 45, 0);
                cb.endText();

                // I. PAGINACIÓN
                // Crea una nueva página cada 3 etiquetas, excepto en la última iteración
                if ((i + 1) % 3 == 0 && (i + 1) < etiquetas.size()) {
                    document.newPage();
                }
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new PdfCreationException("Error generando el PDF de etiquetas. Detalle: " + e.getMessage(), List.of(e.getMessage()));
        }
    }

    /**
     * Método auxiliar para dibujar una columna de datos (Rangos, Total, Resultados)
     * perfectamente centrada sobre un eje X.
     *
     * @param cb        Lienzo del PDF.
     * @param titulo    Título de la columna (ej: SENETÉ).
     * @param rangos    Lista de rangos a imprimir.
     * @param total     Texto del total de cartones.
     * @param resultado Texto del resultado (devolución).
     * @param xCentro   Coordenada X central de la columna.
     * @param yInicio   Coordenada Y superior donde inicia el título.
     * @param fNorm     Fuente normal.
     * @param fBold     Fuente negrita.
     */
    private void dibujarColumnaCentrada(PdfContentByte cb, String titulo, List<String> rangos, String total, String resultado,
                                        float xCentro, float yInicio, BaseFont fNorm, BaseFont fBold) {
        float curY = yInicio;

        cb.beginText();

        // 1. TÍTULO DE COLUMNA
        cb.setFontAndSize(fBold, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, titulo, xCentro, curY, 0);
        curY -= 15; // Salto de línea

        // 2. LISTA DE RANGOS
        cb.setFontAndSize(fNorm, 10);

        // Lógica de compresión: Si hay muchos rangos, reducimos la fuente para que quepan
        if (rangos != null && rangos.size() > 4) {
            cb.setFontAndSize(fNorm, 9);
        }

        if (rangos != null) {
            for (String r : rangos) {
                cb.showTextAligned(Element.ALIGN_CENTER, r, xCentro, curY, 0);
                curY -= 12; // Salto entre rangos
            }
        }

        // 3. LÍNEA DE TOTAL (Siempre visible)
        // Formato estandarizado: TOTAL ------------> (XX)
        cb.setFontAndSize(fBold, 10);
        String textoTotal = "TOTAL ------------> (" + (total != null ? total : "0") + ")";
        cb.showTextAligned(Element.ALIGN_CENTER, textoTotal, xCentro, curY, 0);

        // Espacio separador antes de la sección de Resultados
        curY -= 25;

        // 4. SECCIÓN RESULTADOS
        cb.setFontAndSize(fBold, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, "RESULTADOS", xCentro, curY, 0);

        cb.setFontAndSize(fNorm, 11);
        cb.showTextAligned(Element.ALIGN_CENTER, resultado != null ? resultado : "0", xCentro, curY - 15, 0);

        cb.endText();
    }
}