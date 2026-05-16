package com.eliasgonzalez.cartones.distribucion.preferencias.controller;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Preferencias del distribuidor logueado. El sujeto siempre es el dueño del
 * JWT — no se acepta {@code username} como path param para evitar el
 * "spoof" lateral entre distribuidores con el mismo rol.
 *
 * <p>Accesible por DISTRIBUIDOR o ADMIN. El admin que use estos endpoints
 * verá/editará SUS PROPIAS preferencias; para tocar las de otro user usar
 * {@code /api/admin/preferencias-etiquetas/{username}}.
 */
@RestController
@RequestMapping("/api/me/preferencias-etiquetas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DISTRIBUIDOR')")
@Slf4j
public class MePreferenciasEtiquetasController {

    private final PreferenciasDistribuidorService service;

    @GetMapping
    public ResponseEntity<PreferenciasEtiquetasDTO> obtenerMisPreferencias(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        log.debug("GET /api/me/preferencias-etiquetas user={}", username);
        return ResponseEntity.ok(service.buscarPorUsername(username)
                .map(PreferenciasEtiquetasDTO::fromEntity)
                .orElseGet(() -> defaultDto(username)));
    }

    @PutMapping
    public ResponseEntity<PreferenciasEtiquetasDTO> guardarMisPreferencias(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ActualizarPreferenciasRequest body) {
        String username = jwt.getClaimAsString("preferred_username");
        log.debug("PUT /api/me/preferencias-etiquetas user={} layout={} orden={}",
                username, body.layoutEtiqueta(), body.ordenEtiqueta());
        PreferenciasDistribuidor saved = service.guardar(username, body.layoutEtiqueta(), body.ordenEtiqueta());
        return ResponseEntity.ok(PreferenciasEtiquetasDTO.fromEntity(saved));
    }

    /**
     * Cuando el user nunca abrió esta pantalla, GET devuelve los defaults
     * efectivos (mismos que aplica el generador de PDF) sin crear row.
     */
    private PreferenciasEtiquetasDTO defaultDto(String username) {
        PreferenciasResueltas d = PreferenciasResueltas.defaults();
        return new PreferenciasEtiquetasDTO(username, d.layout(), d.orden(), LocalDateTime.now(), null);
    }
}
