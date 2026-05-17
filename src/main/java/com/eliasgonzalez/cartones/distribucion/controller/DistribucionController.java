package com.eliasgonzalez.cartones.distribucion.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ArchivosGeneradosDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.SimulacionRequestDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
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
     * Genera los archivos PDF en filesystem y transiciona el proceso a COMPLETADO.
     * Se invoca una vez tras la simulación exitosa.
     */
    @PostMapping("/{procesoId}/archivos")
    public ResponseEntity<ArchivosGeneradosDTO> generarArchivos(@PathVariable String procesoId) {
        log.debug("POST /api/distribuciones/{}/archivos", LogSanitizer.safe(procesoId));
        ProcesoDistribucion proceso = gestionArchivoPdf.generarArchivos(procesoId);
        return ResponseEntity.ok(new ArchivosGeneradosDTO(proceso.getProcesoId(), proceso.getArchivosGeneradosEn()));
    }

    /**
     * Marca el proceso como ABANDONADO. Idempotente — el frontend lo llama
     * fire-and-forget en el flujo de "Reiniciar / descartar proceso".
     * Devuelve 204 sin body; el front no necesita info del lado del server.
     */
    @PostMapping("/{procesoId}/abandonar")
    public ResponseEntity<Void> abandonarProceso(@PathVariable String procesoId) {
        log.debug("POST /api/distribuciones/{}/abandonar", LogSanitizer.safe(procesoId));
        gestionArchivoPdf.abandonarProceso(procesoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{procesoId}/etiquetas.pdf")
    public ResponseEntity<Resource> descargarEtiquetas(@PathVariable String procesoId) {
        log.debug("GET /api/distribuciones/{}/etiquetas.pdf", LogSanitizer.safe(procesoId));
        Resource resource = gestionArchivoPdf.obtenerEtiquetas(procesoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Imprimir_etiquetas-" + sanitizarFilename(procesoId) + ".pdf\"")
                .body(resource);
    }

    @GetMapping("/{procesoId}/resumen.pdf")
    public ResponseEntity<Resource> descargarResumen(@PathVariable String procesoId) {
        log.debug("GET /api/distribuciones/{}/resumen.pdf", LogSanitizer.safe(procesoId));
        Resource resource = gestionArchivoPdf.obtenerResumen(procesoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Resumen_entrega-" + sanitizarFilename(procesoId) + ".pdf\"")
                .body(resource);
    }

    static String sanitizarFilename(String raw) {
        if (raw == null) return "x";
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
