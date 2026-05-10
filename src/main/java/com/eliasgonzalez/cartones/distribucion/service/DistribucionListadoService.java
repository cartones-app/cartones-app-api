package com.eliasgonzalez.cartones.distribucion.service;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listado de ProcesoDistribucion con verificación de ownership.
 *
 * Reglas:
 *  - Cada usuario ve y descarga solo los procesos que él creó (createdBy = sub del JWT).
 *  - Los endpoints admin (/api/admin/distribuciones) no pasan por este filtro;
 *    la autorización por rol ya se hace en SecurityConfig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistribucionListadoService {

    private final ProcesoDistribucionRepository procesoRepo;

    @Transactional(readOnly = true)
    public List<ProcesoDistribucionResumenDTO> listarPropios() {
        String sub = obtenerSubActual();
        log.debug("Listando procesos del usuario sub={}", sub);
        return procesoRepo.findAllByCreatedByOrderByCreatedAtDesc(sub).stream()
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcesoDistribucionResumenDTO> listarTodos() {
        log.debug("Listando todos los procesos (vista admin)");
        return procesoRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::aResumen)
                .toList();
    }

    /**
     * Verifica que el proceso pertenece al usuario actual antes de retornarlo.
     * Lanza ResourceNotFoundException si no existe O si pertenece a otro usuario
     * (no exponemos la diferencia para no filtrar existencia).
     */
    @Transactional(readOnly = true)
    public ProcesoDistribucion verificarOwnership(String procesoId) {
        String sub = obtenerSubActual();
        return procesoRepo
                .findByProcesoIdAndCreatedBy(procesoId, sub)
                .orElseThrow(() -> new ResourceNotFoundException("Proceso de distribución no encontrado.", List.of()));
    }

    private ProcesoDistribucionResumenDTO aResumen(ProcesoDistribucion p) {
        long tamEtiq = p.getPdfEtiquetas() == null ? 0 : p.getPdfEtiquetas().length;
        long tamRes = p.getPdfResumen() == null ? 0 : p.getPdfResumen().length;
        return ProcesoDistribucionResumenDTO.builder()
                .procesoId(p.getProcesoId())
                .estado(p.getEstado())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .createdBy(p.getCreatedBy())
                .tieneEtiquetas(tamEtiq > 0)
                .tieneResumen(tamRes > 0)
                .tamanoEtiquetasBytes(tamEtiq)
                .tamanoResumenBytes(tamRes)
                .build();
    }

    /**
     * Extrae el sub del JWT autenticado. Lanza si no hay JWT (no debería
     * pasar dentro de SecurityFilterChain, pero defendemos igual).
     */
    private String obtenerSubActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new IllegalStateException("No hay JWT en el SecurityContext");
    }
}
