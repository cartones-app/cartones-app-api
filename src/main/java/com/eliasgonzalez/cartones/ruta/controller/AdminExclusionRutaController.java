package com.eliasgonzalez.cartones.ruta.controller;

import com.eliasgonzalez.cartones.ruta.controller.dto.ExclusionRutaRequestDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.ExclusionRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.service.AdminExclusionRutaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de administración para la lista de exclusiones del flujo de ruta.
 * Solo accesible por el rol ADMIN.
 * Ejemplo: RECIBO DE CARTONES, VENTA LOCAL son pre-cargadas pero editables.
 */
@RestController
@RequestMapping("/api/admin/ruta/exclusiones")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.origins}")
public class AdminExclusionRutaController {

    private final AdminExclusionRutaService exclusionService;

    // Listar todas (activas e inactivas)
    @GetMapping
    public ResponseEntity<List<ExclusionRutaResponseDTO>> listar() {
        return ResponseEntity.ok(exclusionService.listarTodas());
    }

    // Crear una nueva exclusión
    @PostMapping
    public ResponseEntity<ExclusionRutaResponseDTO> crear(@Valid @RequestBody ExclusionRutaRequestDTO request) {
        return ResponseEntity.ok(exclusionService.crear(request));
    }

    // Actualizar (renombrar, cambiar descripción o activar/desactivar)
    @PutMapping("/{id}")
    public ResponseEntity<ExclusionRutaResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ExclusionRutaRequestDTO request
    ) {
        return ResponseEntity.ok(exclusionService.actualizar(id, request));
    }

    // Eliminar permanentemente
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        exclusionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
