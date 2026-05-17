package com.eliasgonzalez.cartones.distribucion.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionDescargaService;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionListadoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vista administrativa de los procesos de distribución.
 * Solo accesible por rol ADMIN.
 *
 * Defensa en capas: además del filtro path-based en SecurityConfig,
 *
 * @PreAuthorize a nivel clase asegura que un eventual refactor de ruta
 *               no exponga los endpoints accidentalmente.
 */
@RestController
@RequestMapping("/api/admin/distribuciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminDistribucionController {

    private final DistribucionListadoService listadoService;
    private final DistribucionDescargaService gestionArchivoPdf;

    @GetMapping
    public ResponseEntity<List<ProcesoDistribucionResumenDTO>> listarTodos() {
        log.debug("GET /api/admin/distribuciones");
        return ResponseEntity.ok(listadoService.listarTodos());
    }

    @GetMapping("/{procesoId}/etiquetas.pdf")
    public ResponseEntity<Resource> descargarEtiquetas(@PathVariable String procesoId) {
        log.debug("GET /api/admin/distribuciones/{}/etiquetas.pdf", LogSanitizer.safe(procesoId));
        Resource resource = gestionArchivoPdf.obtenerEtiquetasAdmin(procesoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Imprimir_etiquetas-" + DistribucionController.sanitizarFilename(procesoId) + ".pdf\"")
                .body(resource);
    }

    @GetMapping("/{procesoId}/resumen.pdf")
    public ResponseEntity<Resource> descargarResumen(@PathVariable String procesoId) {
        log.debug("GET /api/admin/distribuciones/{}/resumen.pdf", LogSanitizer.safe(procesoId));
        Resource resource = gestionArchivoPdf.obtenerResumenAdmin(procesoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Resumen_entrega-" + DistribucionController.sanitizarFilename(procesoId) + ".pdf\"")
                .body(resource);
    }
}
