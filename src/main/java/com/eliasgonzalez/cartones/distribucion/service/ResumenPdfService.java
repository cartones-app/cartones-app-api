package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.service.dto.ResumenDTO;
import com.eliasgonzalez.cartones.common.exception.PdfCreationException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ResumenPdfService {

    private static final float MARGEN = 15;
    private static final float PADDING_Y = 6;
    private static final int LIMITE_CARACTERES = 30;

    // POSICIONES X ACTUALIZADAS (Más espacio para el nombre y nombres de columnas cortos)
    private static final Map<String, Float> COLUMNAS_X = new HashMap<>();

    static {
        COLUMNAS_X.put("NUMERO", MARGEN + 5);
        COLUMNAS_X.put("VENDEDOR", MARGEN + 35);
        COLUMNAS_X.put("SENETE", MARGEN + 210);      // Desplazado a la derecha (antes 180)
        COLUMNAS_X.put("TELEBINGO", MARGEN + 350);   // Desplazado a la derecha (antes 330)
        COLUMNAS_X.put("CANT_S", MARGEN + 490);
        COLUMNAS_X.put("CANT_T", MARGEN + 540);
    }

    public byte[] generarResumen(List<ResumenDTO> vendedores, LocalDate fechaSenete, LocalDate fechaTelebingo) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // Si la lista de vendedores está vacía, genera un PDF vacío válido.
            if (vendedores == null || vendedores.isEmpty()) {
                document.add(new com.lowagie.text.Paragraph("No hay resumen de vendedores para generar.")); // Añadir al menos algo
                document.close();
                return baos.toByteArray();
            }
            
            // Asegurarse de que haya al menos una página para el contenido
            document.newPage(); 

            // Continuar con la lógica normal si hay vendedores
            PdfContentByte cb = writer.getDirectContent();

            float width = PageSize.A4.getWidth();
            float height = PageSize.A4.getHeight();
            BaseFont helv = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            // TÍTULO
            float y = height - MARGEN - 20;
            cb.beginText();
            cb.setFontAndSize(bold, 14);
            if (fechaSenete.isEqual(fechaTelebingo)) {
                cb.showTextAligned(Element.ALIGN_CENTER, "RESUMEN DE ENTREGA - " + fechaSenete.format(fmt), width / 2, y, 0);
                y -= 25;
            } else {
                cb.showTextAligned(Element.ALIGN_CENTER, "RESUMEN DE ENTREGA", width / 2, y, 0);
                cb.setFontAndSize(bold, 10);
                cb.showTextAligned(Element.ALIGN_CENTER, "SENETÉ: " + fechaSenete.format(fmt) + " | TELEBINGO: " + fechaTelebingo.format(fmt), width / 2, y - 15, 0);
                y -= 35;
            }
            cb.endText();

            dibujarEncabezados(cb, y, width, bold);
            y -= 25;

            for (int i = 0; i < vendedores.size(); i++) {
                ResumenDTO row = vendedores.get(i);
                int maxLineas = Math.max(row.getSeneteDelAl().size(), row.getTelebingoDelAl().size());
                if (maxLineas == 0) maxLineas = 1;
                float alturaFila = (maxLineas * 12) + PADDING_Y * 2;

                if (y - alturaFila < MARGEN + 20) {
                    document.newPage();
                    y = height - MARGEN - 40;
                    dibujarEncabezados(cb, y, width, bold);
                    y -= 25;
                }

                if ((i + 1) % 2 == 0) {
                    cb.saveState();
                    cb.setRGBColorFill(240, 240, 240);
                    cb.rectangle(MARGEN, y - alturaFila, width - 2 * MARGEN, alturaFila);
                    cb.fill();
                    cb.restoreState();
                }

                cb.beginText();
                cb.setFontAndSize(helv, 9);
                float yTextoBase = y - 12 - PADDING_Y + 4;

                cb.showTextAligned(Element.ALIGN_LEFT, "#" + row.getNumeroVendedor(), COLUMNAS_X.get("NUMERO"), yTextoBase, 0);

                // LÓGICA DE TRUNCAMIENTO DE NOMBRE
                String nombreProcesado = procesarNombre(row.getNombre());
                cb.showTextAligned(Element.ALIGN_LEFT, nombreProcesado, COLUMNAS_X.get("VENDEDOR"), yTextoBase, 0);

                cb.showTextAligned(Element.ALIGN_CENTER, String.valueOf(row.getCantidadSenete()), COLUMNAS_X.get("CANT_S") + 15, yTextoBase, 0);
                cb.showTextAligned(Element.ALIGN_CENTER, String.valueOf(row.getCantidadTelebingo()), COLUMNAS_X.get("CANT_T") + 15, yTextoBase, 0);
                cb.endText();

                dibujarMapaRangos(cb, row.getSeneteDelAl(), COLUMNAS_X.get("SENETE"), y - PADDING_Y - 8, helv);
                dibujarMapaRangos(cb, row.getTelebingoDelAl(), COLUMNAS_X.get("TELEBINGO"), y - PADDING_Y - 8, helv);

                y -= alturaFila;
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new PdfCreationException("Error generando Resumen", List.of(e.getMessage()));
        }
    }

    private String procesarNombre(String nombre) {
        if (nombre == null) return "";
        if (nombre.length() <= LIMITE_CARACTERES) return nombre.toUpperCase();
        return nombre.substring(0, LIMITE_CARACTERES - 3).toUpperCase() + "...";
    }

    private void dibujarEncabezados(PdfContentByte cb, float y, float width, BaseFont font) {
        cb.beginText();
        cb.setFontAndSize(font, 9);
        cb.showTextAligned(Element.ALIGN_LEFT, "#", COLUMNAS_X.get("NUMERO"), y, 0);
        cb.showTextAligned(Element.ALIGN_LEFT, "VENDEDOR", COLUMNAS_X.get("VENDEDOR"), y, 0);

        // TÍTULOS SIMPLIFICADOS (Sin "RANGOS")
        cb.showTextAligned(Element.ALIGN_LEFT, "SENETÉ (Del - Al)", COLUMNAS_X.get("SENETE"), y, 0);
        cb.showTextAligned(Element.ALIGN_LEFT, "TELEBINGO (Del - Al)", COLUMNAS_X.get("TELEBINGO"), y, 0);

        cb.showTextAligned(Element.ALIGN_CENTER, "CANT. S", COLUMNAS_X.get("CANT_S") + 15, y, 0);
        cb.showTextAligned(Element.ALIGN_CENTER, "CANT. T", COLUMNAS_X.get("CANT_T") + 15, y, 0);
        cb.endText();

        cb.setLineWidth(1f);
        cb.moveTo(MARGEN, y - 5);
        cb.lineTo(width - MARGEN, y - 5);
        cb.stroke();
    }

    private void dibujarMapaRangos(PdfContentByte cb, Map<String, String> rangos, float xBase, float yInicio, BaseFont font) {
        if (rangos == null || rangos.isEmpty()) return;
        cb.beginText();
        cb.setFontAndSize(font, 9);
        float currentY = yInicio;
        List<String> keys = new ArrayList<>(rangos.keySet());
        Collections.sort(keys);
        for (String inicio : keys) {
            cb.showTextAligned(Element.ALIGN_LEFT, inicio + " - " + rangos.get(inicio), xBase, currentY, 0);
            currentY -= 12;
        }
        cb.endText();
    }
}