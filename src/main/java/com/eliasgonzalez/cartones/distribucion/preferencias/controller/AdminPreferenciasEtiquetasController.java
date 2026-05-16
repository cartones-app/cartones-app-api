package com.eliasgonzalez.cartones.distribucion.preferencias.controller;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.distribucion.preferencias.controller.dto.ActualizarPreferenciasRequest;
import com.eliasgonzalez.cartones.distribucion.preferencias.controller.dto.PreferenciasEtiquetasDTO;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import com.eliasgonzalez.cartones.distribucion.preferencias.service.PreferenciasDistribuidorService;
import com.eliasgonzalez.cartones.distribucion.preferencias.service.PreferenciasDistribuidorService.PreferenciasResueltas;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Endpoints administrativos para preferencias de etiquetas. Permiten al admin
 * editar las preferencias de cualquier distribuidor — útil cuando el user
 * se olvida cómo cambiar sus parámetros y pide ayuda. El cambio persiste
 * exactamente igual que si el distribuidor lo hiciera por sí mismo; el user
 * puede volver a modificarlo cuando quiera.
 *
 * <p>Doble defensa: path bajo {@code /api/admin/...} ya está cubierto por
 * {@code SecurityConfig} + {@code @PreAuthorize} a nivel clase como
 * redundancia ante refactors de ruta.
 */
@RestController
@RequestMapping("/api/admin/preferencias-etiquetas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminPreferenciasEtiquetasController {

    private final PreferenciasDistribuidorService service;

    /** Lista todas las filas presentes en la tabla. */
    @GetMapping
    public ResponseEntity<List<PreferenciasEtiquetasDTO>> listar() {
        log.debug("GET /api/admin/preferencias-etiquetas");
        List<PreferenciasEtiquetasDTO> dtos = service.listarTodas().stream()
                .map(PreferenciasEtiquetasDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Lee las preferencias de un user puntual. Si nunca configuró nada
     * devuelve los defaults (sin crear row), para que la UI muestre algo
     * coherente.
     */
    @GetMapping("/{username}")
    public ResponseEntity<PreferenciasEtiquetasDTO> obtener(@PathVariable String username) {
        log.debug("GET /api/admin/preferencias-etiquetas/{}", LogSanitizer.safe(username));
        return ResponseEntity.ok(service.buscarPorUsername(username)
                .map(PreferenciasEtiquetasDTO::fromEntity)
                .orElseGet(() -> defaultDto(username)));
    }

    /** Upsert para un user puntual. Idempotente con el mismo body. */
    @PutMapping("/{username}")
    public ResponseEntity<PreferenciasEtiquetasDTO> guardar(
            @PathVariable String username,
            @Valid @RequestBody ActualizarPreferenciasRequest body) {
        log.debug("PUT /api/admin/preferencias-etiquetas/{} layout={} orden={}",
                LogSanitizer.safe(username), body.layoutEtiqueta(), body.ordenEtiqueta());
        PreferenciasDistribuidor saved = service.guardar(username, body.layoutEtiqueta(), body.ordenEtiqueta());
        return ResponseEntity.ok(PreferenciasEtiquetasDTO.fromEntity(saved));
    }

    private PreferenciasEtiquetasDTO defaultDto(String username) {
        PreferenciasResueltas d = PreferenciasResueltas.defaults();
        return new PreferenciasEtiquetasDTO(username, d.layout(), d.orden(), LocalDateTime.now(), null);
    }
}
