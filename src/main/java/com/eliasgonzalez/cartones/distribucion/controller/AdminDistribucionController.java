package com.eliasgonzalez.cartones.distribucion.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Lista todos los procesos del sistema, más recientes primero.
     */
    @GetMapping
    public ResponseEntity<List<ProcesoDistribucionResumenDTO>> listarTodos() {
        log.debug("GET /api/admin/distribuciones");
        return ResponseEntity.ok(listadoService.listarTodos());
    }

    /**
     * Descarga el ZIP de cualquier proceso (sin ownership check).
     */
    @GetMapping("/{procesoId}/pdfs")
    public ResponseEntity<Resource> descargar(@PathVariable String procesoId) throws IOException {
        log.debug("GET /api/admin/distribuciones/{}/pdfs", procesoId);
        Resource zip = gestionArchivoPdf.generarPaqueteZip(procesoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"zip-" + DistribucionController.sanitizarFilename(procesoId) + ".zip\"")
                .contentLength(zip.contentLength())
                .body(zip);
    }
}
