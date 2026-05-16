package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.common.exception.PdfCreationException;
import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.distribucion.service.etiquetas.EtiquetaLayoutRenderer;
import com.eliasgonzalez.cartones.distribucion.service.etiquetas.EtiquetaOrderer;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orquestador de la generación del PDF de etiquetas.
 *
 * <p>Esta clase no sabe dibujar etiquetas — delega cada slot a un
 * {@link EtiquetaLayoutRenderer} (uno por valor de {@link LayoutEtiqueta}).
 * Lo único que hace acá es:
 * <ol>
 *   <li>Elegir el renderer según el {@code layout} pedido.</li>
 *   <li>Reordenar la lista con {@link EtiquetaOrderer} si {@code orden} es
 *       INTERCALADO.</li>
 *   <li>Recorrer hojas × slots con la geometría que el renderer indique.</li>
 * </ol>
 *
 * <p>Agregar un layout nuevo (ej. 6-por-hoja) es solo escribir un renderer más
 * y mapearlo a un valor de {@code LayoutEtiqueta}; ni esta clase ni el orderer
 * cambian.
 */
@Service
public class EtiquetasPdfService {

    private static final float MARGEN = 20f;             // se aplica en los 4 bordes del A4
    private static final float ESPACIO_VERTICAL = 15f;   // entre etiquetas en una hoja

    private final Map<LayoutEtiqueta, EtiquetaLayoutRenderer> rendererPorLayout;
    private final EtiquetaOrderer orderer;

    /**
     * Spring inyecta TODOS los beans que implementan {@link EtiquetaLayoutRenderer}.
     * Los indexamos por {@link LayoutEtiqueta} en startup — si dos renderers
     * reportan el mismo layout, {@link Collectors#toUnmodifiableMap} lanza al
     * arrancar el contexto (mejor que descubrirlo al primer PDF).
     */
    public EtiquetasPdfService(List<EtiquetaLayoutRenderer> renderers, EtiquetaOrderer orderer) {
        this.orderer = orderer;
        this.rendererPorLayout = renderers.stream()
                .collect(Collectors.toUnmodifiableMap(EtiquetaLayoutRenderer::getLayout, r -> r));
    }

    /** Backwards-compat: defaults a TRES_POR_HOJA + SECUENCIAL. */
    public byte[] generarEtiquetas(
            List<EtiquetaDTO> etiquetas,
            LocalDate fechaSenete,
            LocalDate fechaTelebingo) {
        return generarEtiquetas(etiquetas, fechaSenete, fechaTelebingo,
                LayoutEtiqueta.defaultValue(), OrdenEtiqueta.defaultValue());
    }

    /**
     * Genera el PDF aplicando el layout y orden dados. Si {@code etiquetas} es
     * vacío/null devuelve un PDF mínimo con un placeholder.
     */
    public byte[] generarEtiquetas(
            List<EtiquetaDTO> etiquetas,
            LocalDate fechaSenete,
            LocalDate fechaTelebingo,
            LayoutEtiqueta layout,
            OrdenEtiqueta orden) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (etiquetas == null || etiquetas.isEmpty()) {
                return pdfVacio(baos);
            }

            EtiquetaLayoutRenderer renderer = resolverRenderer(layout);
            int slotsPorHoja = renderer.getSlotsPorHoja();
            List<EtiquetaDTO> ordenadas = orderer.ordenar(etiquetas, orden, slotsPorHoja);

            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            float width = PageSize.A4.getWidth();
            float height = PageSize.A4.getHeight();
            // Alto de cada etiqueta: (alto total - 2 márgenes - (slots-1) espacios) / slots
            float altoEt = (height - 2 * MARGEN - (slotsPorHoja - 1) * ESPACIO_VERTICAL) / slotsPorHoja;
            float anchoEt = width - 2 * MARGEN;

            BaseFont helv = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String txtFechaSenete    = fechaSenete.format(fmt);
            String txtFechaTelebingo = fechaTelebingo.format(fmt);
            boolean fechasIguales    = fechaSenete.isEqual(fechaTelebingo);

            for (int i = 0; i < ordenadas.size(); i++) {
                int slot = i % slotsPorHoja;
                // y del borde inferior de la etiqueta en este slot (iText: origen abajo-izq)
                float y = height - MARGEN - (slot + 1) * altoEt - slot * ESPACIO_VERTICAL;
                float x = MARGEN;

                EtiquetaLayoutRenderer.ContextoEtiqueta ctx = new EtiquetaLayoutRenderer.ContextoEtiqueta(
                        ordenadas.get(i), txtFechaSenete, txtFechaTelebingo, fechasIguales, helv, bold);

                renderer.dibujarEtiqueta(cb, ctx, x, y, anchoEt, altoEt);

                boolean ultimoSlotDeHoja = (i + 1) % slotsPorHoja == 0;
                boolean quedanEtiquetas  = (i + 1) < ordenadas.size();
                if (ultimoSlotDeHoja && quedanEtiquetas) {
                    document.newPage();
                }
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new PdfCreationException(
                    "Error generando el PDF de etiquetas. Detalle: " + e.getMessage(),
                    List.of(e.getMessage()));
        }
    }

    private EtiquetaLayoutRenderer resolverRenderer(LayoutEtiqueta layout) {
        LayoutEtiqueta efectivo = layout != null ? layout : LayoutEtiqueta.defaultValue();
        EtiquetaLayoutRenderer r = rendererPorLayout.get(efectivo);
        if (r == null) {
            throw new IllegalStateException("No hay renderer registrado para layout " + efectivo);
        }
        return r;
    }

    private byte[] pdfVacio(ByteArrayOutputStream baos) throws Exception {
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        document.add(new Paragraph("No hay etiquetas para generar."));
        document.close();
        return baos.toByteArray();
    }
}
