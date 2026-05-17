package com.eliasgonzalez.cartones.distribucion.service;

import java.util.List;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionResumenView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listado de ProcesoDistribucion con verificación de ownership.
 *
 * Reglas:
 * - Cada usuario ve y descarga solo los procesos que él creó (createdBy = sub
 * del JWT).
 * - Los endpoints admin (/api/admin/distribuciones) no pasan por este filtro;
 * la autorización por rol ya se hace en SecurityConfig.
 * - Las queries de listado usan projection nativa para no traer los bytes
 * de archivos a la JVM.
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
        return procesoRepo.findResumenByCreatedBy(sub).stream()
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcesoDistribucionResumenDTO> listarTodos() {
        log.debug("Listando todos los procesos (vista admin)");
        return procesoRepo.findAllResumenOrderByCreatedAtDesc().stream()
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

    private ProcesoDistribucionResumenDTO aResumen(ProcesoDistribucionResumenView v) {
        return ProcesoDistribucionResumenDTO.builder()
                .procesoId(v.getProcesoId())
                .estado(v.getEstado())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .createdBy(v.getCreatedBy())
                .archivosGeneradosEn(v.getArchivosGeneradosEn())
                .archivosBorradosEn(v.getArchivosBorradosEn())
                .build();
    }

    /**
     * Identidad del usuario actual para filtrar / verificar ownership.
     *
     * Usa auth.getName() en todos los casos para mantener coherencia con
     * AuditorAware (que persiste createdBy con el mismo valor). En el perfil
     * con Keycloak, SecurityConfig configura el JwtAuthenticationConverter con
     * principalClaimName="preferred_username", asi que getName() devuelve el
     * username humano (no el UUID del claim sub). En perfil local con
     * AnonymousAuthenticationToken, getName() devuelve "anonymousUser".
     *
     * Sin Authentication: lanza InsufficientAuthenticationException → 401.
     */
    private String obtenerSubActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new InsufficientAuthenticationException("No hay autenticación en el SecurityContext");
        }
        return auth.getName();
    }
}
