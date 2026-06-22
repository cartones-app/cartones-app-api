package com.eliasgonzalez.cartones.common.flags.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.common.flags.FeatureFlagsAdminService;
import com.eliasgonzalez.cartones.common.flags.dto.FlagViewDTO;
import com.eliasgonzalez.cartones.common.flags.dto.SetFlagRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Endpoints de administración de feature flags. Permiten al admin pisar el
 * valor de un flag en runtime sin redeploy.
 *
 * <p>
 * <b>Autorización en capas</b>:
 * <ol>
 * <li>{@code /api/admin/**} ya requiere {@code hasRole('ADMIN')} en
 * {@code SecurityConfig}.</li>
 * <li>{@code @PreAuthorize} a nivel clase como defensa redundante — si un
 * refactor mueve el mapping fuera de {@code /api/admin/}, la restricción
 * sigue activa.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/feature-flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminFeatureFlagController {

    private final FeatureFlagsAdminService service;

    @GetMapping
    public ResponseEntity<List<FlagViewDTO>> listar() {
        return ResponseEntity.ok(service.listarFlags());
    }

    @GetMapping("/{flagKey}")
    public ResponseEntity<FlagViewDTO> obtener(@PathVariable String flagKey) {
        return ResponseEntity.ok(service.obtenerFlag(flagKey));
    }

    /**
     * Crea o actualiza el override de un flag. Idempotente — un PUT repetido
     * con el mismo valor no genera cambios visibles (más allá del modifiedBy
     * y updatedAt de auditoría).
     */
    @PutMapping("/{flagKey}")
    public ResponseEntity<FlagViewDTO> setOverride(
            @PathVariable String flagKey, @Valid @RequestBody SetFlagRequest request) {
        return ResponseEntity.ok(service.setOverride(flagKey, request));
    }

    /**
     * Elimina el override — el valor efectivo vuelve al default de
     * {@code classpath:flags.yml}.
     */
    @DeleteMapping("/{flagKey}")
    public ResponseEntity<Void> clearOverride(@PathVariable String flagKey) {
        service.clearOverride(flagKey);
        return ResponseEntity.noContent().build();
    }
}
