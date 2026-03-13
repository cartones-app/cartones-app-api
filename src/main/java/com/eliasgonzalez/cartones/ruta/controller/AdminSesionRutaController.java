package com.eliasgonzalez.cartones.ruta.controller;

import com.eliasgonzalez.cartones.ruta.controller.dto.EliminarSesionesRequestDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.SesionRutaRegistroResponseDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.SesionRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.service.AdminSesionRutaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de administración para el historial de sesiones de recorrido de ruta.
 * Solo accesible por el rol ADMIN.
 * Permite ver, filtrar y eliminar sesiones y registros individuales.
 */
@RestController
@RequestMapping("/api/admin/ruta")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.origins}")
public class AdminSesionRutaController {

    private final AdminSesionRutaService sesionRutaService;

    // Listar sesiones con filtros opcionales
    @GetMapping("/sesiones")
    public ResponseEntity<List<SesionRutaResponseDTO>> listarSesiones(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String createdBy
    ) {
        log.debug("GET /api/admin/ruta/sesiones - estado: {}, createdBy: {}", estado, createdBy);
        return ResponseEntity.ok(sesionRutaService.listarSesiones(estado, createdBy));
    }

    // Detalle de una sesión
    @GetMapping("/sesiones/{sesionId}")
    public ResponseEntity<SesionRutaResponseDTO> obtenerSesion(@PathVariable String sesionId) {
        log.debug("GET /api/admin/ruta/sesiones/{}", sesionId);
        return ResponseEntity.ok(sesionRutaService.obtenerSesion(sesionId));
    }

    // Registros de una sesión con filtros opcionales
    @GetMapping("/sesiones/{sesionId}/registros")
    public ResponseEntity<List<SesionRutaRegistroResponseDTO>> listarRegistros(
            @PathVariable String sesionId,
            @RequestParam(required = false) Boolean completado,
            @RequestParam(required = false) String vendedorNombre,
            @RequestParam(required = false) Boolean camposIncompletos
    ) {
        log.debug("GET /api/admin/ruta/sesiones/{}/registros - completado: {}, vendedorNombre: {}, camposIncompletos: {}",
            sesionId, completado, vendedorNombre, camposIncompletos);
        return ResponseEntity.ok(
            sesionRutaService.listarRegistros(sesionId, completado, vendedorNombre, camposIncompletos)
        );
    }

    // Eliminar una sesión y todos sus registros (bloqueado si está ACTIVA)
    @DeleteMapping("/sesiones/{sesionId}")
    public ResponseEntity<Void> eliminarSesion(@PathVariable String sesionId) {
        log.debug("DELETE /api/admin/ruta/sesiones/{}", sesionId);
        sesionRutaService.eliminarSesion(sesionId);
        return ResponseEntity.noContent().build();
    }

    // Eliminar múltiples sesiones en un solo request
    @DeleteMapping("/sesiones")
    public ResponseEntity<Void> eliminarSesiones(@Valid @RequestBody EliminarSesionesRequestDTO request) {
        log.debug("DELETE /api/admin/ruta/sesiones - ids: {}", request.getSesionIds());
        sesionRutaService.eliminarSesiones(request.getSesionIds());
        return ResponseEntity.noContent().build();
    }

    // Eliminar un registro individual
    @DeleteMapping("/registros/{id}")
    public ResponseEntity<Void> eliminarRegistro(@PathVariable Long id) {
        log.debug("DELETE /api/admin/ruta/registros/{}", id);
        sesionRutaService.eliminarRegistro(id);
        return ResponseEntity.noContent().build();
    }
}
