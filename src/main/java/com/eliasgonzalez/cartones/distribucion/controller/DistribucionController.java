package com.eliasgonzalez.cartones.distribucion.controller;

import com.eliasgonzalez.cartones.distribucion.controller.dto.SimulacionRequestDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionDescargaService;
import com.eliasgonzalez.cartones.distribucion.service.DistribucionOrquestadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/distribuciones")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.origins}")
public class DistribucionController {

    private final DistribucionOrquestadorService gestionDistribucion;
    private final DistribucionDescargaService gestionArchivoPdf;

    @PostMapping("/{procesoId}/simular")
    public ResponseEntity<List<VendedorSimuladoDTO>> simular(
            @Valid @RequestBody SimulacionRequestDTO solicitud,
            @PathVariable String procesoId) {

        log.debug("POST /api/distribuciones/{}/simular", procesoId);
        log.info("Iniciando simulación para el proceso ID: {}", procesoId);
        return ResponseEntity.ok(gestionDistribucion.procesarSimulacion(procesoId, solicitud));
    }

    @GetMapping("/{procesoId}/pdfs")
    public ResponseEntity<Resource> descargar(@PathVariable String procesoId) throws IOException {
        log.debug("GET /api/distribuciones/{}/pdfs", procesoId);

        Resource zip = gestionArchivoPdf.generarPaqueteZip(procesoId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"zip-" + procesoId + ".zip\"")
                .contentLength(zip.contentLength())
                .body(zip);
    }
}