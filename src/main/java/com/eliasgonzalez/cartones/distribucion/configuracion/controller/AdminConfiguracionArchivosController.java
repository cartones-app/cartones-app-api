package com.eliasgonzalez.cartones.distribucion.configuracion.controller;

import com.eliasgonzalez.cartones.distribucion.configuracion.dto.ActualizarConfiguracionArchivosRequest;
import com.eliasgonzalez.cartones.distribucion.configuracion.dto.ConfiguracionArchivosDTO;
import com.eliasgonzalez.cartones.distribucion.configuracion.service.ConfiguracionArchivosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints administrativos para la configuración global de retención de archivos.
 * Singleton (id=1) — no se puede crear ni borrar, solo leer y actualizar.
 *
 * <p>Doble defensa: path bajo {@code /api/admin/...} cubierto por
 * {@code SecurityConfig} + {@code @PreAuthorize} a nivel clase como
 * redundancia ante refactors de ruta.
 */
@RestController
@RequestMapping("/api/admin/configuracion-archivos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminConfiguracionArchivosController {

    private final ConfiguracionArchivosService service;

    @GetMapping
    public ResponseEntity<ConfiguracionArchivosDTO> obtener() {
        log.debug("GET /api/admin/configuracion-archivos");
        return ResponseEntity.ok(ConfiguracionArchivosDTO.fromEntity(service.obtener()));
    }

    @PutMapping
    public ResponseEntity<ConfiguracionArchivosDTO> actualizar(
            @Valid @RequestBody ActualizarConfiguracionArchivosRequest body) {
        log.debug("PUT /api/admin/configuracion-archivos retencionMeses={} eliminacionActiva={}",
                body.retencionMeses(), body.eliminacionActiva());
        return ResponseEntity.ok(
                ConfiguracionArchivosDTO.fromEntity(
                        service.actualizar(body.retencionMeses(), body.eliminacionActiva())));
    }
}
