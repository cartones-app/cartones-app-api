package com.eliasgonzalez.cartones.distribucion.controller;

import java.io.IOException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.distribucion.controller.dto.DistribucionDatosPdfDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.SimulacionRequestDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionDatosPdfService;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionDescargaService;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionListadoService;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionOrquestadorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/distribuciones")
@RequiredArgsConstructor
@Slf4j
public class DistribucionController {

    private final DistribucionOrquestadorService gestionDistribucion;
    private final DistribucionDescargaService gestionArchivoPdf;
    private final DistribucionListadoService listadoService;
    private final DistribucionDatosPdfService datosPdfService;

    /**
     * Lista los procesos de distribución del usuario autenticado, más recientes
     * primero.
     * No incluye los BLOBs de PDFs — solo metadata para mostrar en una grilla.
     */
    @GetMapping
    public ResponseEntity<List<ProcesoDistribucionResumenDTO>> listarPropios() {
        log.debug("GET /api/distribuciones");
        return ResponseEntity.ok(listadoService.listarPropios());
    }

    @PostMapping("/{procesoId}/simular")
    public ResponseEntity<List<VendedorSimuladoDTO>> simular(
            @Valid @RequestBody SimulacionRequestDTO solicitud, @PathVariable String procesoId) {

        log.debug("POST /api/distribuciones/{}/simular", LogSanitizer.safe(procesoId));
        log.info("Iniciando simulación para el proceso ID: {}", LogSanitizer.safe(procesoId));
        return ResponseEntity.ok(gestionDistribucion.procesarSimulacion(procesoId, solicitud));
    }

    /**
     * Devuelve los datos crudos del proceso (etiquetas, resumen, fechas) para
     * que el cliente arme los PDFs con pdfme. Solo el usuario que creó el
     * proceso puede leerlos (ownership).
     *
     * <p>
     * Si la simulación se perdió (container reiniciado, nunca se simuló),
     * devuelve 410 Gone — el cliente debe re-simular.
     */
    @GetMapping("/{procesoId}/datos")
    public ResponseEntity<DistribucionDatosPdfDTO> obtenerDatos(@PathVariable String procesoId) {
        log.debug("GET /api/distribuciones/{}/datos", LogSanitizer.safe(procesoId));
        listadoService.verificarOwnership(procesoId);
        return ResponseEntity.ok(datosPdfService.obtenerDatos(procesoId));
    }

    /**
     * Descarga el ZIP con los PDFs (etiquetas + resumen) del proceso indicado.
     * Solo el usuario que creó el proceso puede bajarlo (ownership).
     * Para bypass admin, ver AdminDistribucionController.
     *
     * @deprecated reemplazado por {@code GET /{procesoId}/datos} + generación
     *             client-side con pdfme. Se mantiene como fallback cuando el
     *             flag {@code pdf.client.enabled} está en {@code false}.
     */
    @Deprecated
    @GetMapping("/{procesoId}/pdfs")
    public ResponseEntity<Resource> descargar(@PathVariable String procesoId) throws IOException {
        log.debug("GET /api/distribuciones/{}/pdfs", LogSanitizer.safe(procesoId));
        listadoService.verificarOwnership(procesoId);

        Resource zip = gestionArchivoPdf.generarPaqueteZip(procesoId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"zip-" + sanitizarFilename(procesoId) + ".zip\"")
                .contentLength(zip.contentLength())
                .body(zip);
    }

    /**
     * Sanitiza el procesoId para usarlo en Content-Disposition. Solo permite
     * caracteres alfanuméricos, guiones y guión bajo. Defensa en profundidad:
     * el procesoId server-generated es un UUID, pero esto cierra cualquier
     * vector de inyección de header si el contrato cambia.
     */
    static String sanitizarFilename(String raw) {
        if (raw == null)
            return "x";
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
