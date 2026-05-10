package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.controller.dto.ProcesoDistribucionResumenDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionResumenView;

@ExtendWith(MockitoExtension.class)
class DistribucionListadoServiceTest {

    @Mock
    private ProcesoDistribucionRepository procesoRepo;

    @InjectMocks
    private DistribucionListadoService service;

    @AfterEach
    void limpiarSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- listarPropios -----------------------------------------------------

    @Test
    void listarPropios_filtraPorSubDelJwtYNoCargaBlobs() {
        autenticarConJwt("user-123");
        ProcesoDistribucionResumenView v = vista("p-1", "PENDIENTE", "user-123", 1024L, 0L);
        when(procesoRepo.findResumenByCreatedBy("user-123")).thenReturn(List.of(v));

        List<ProcesoDistribucionResumenDTO> resultado = service.listarPropios();

        assertThat(resultado).hasSize(1).first().satisfies(dto -> {
            assertThat(dto.getProcesoId()).isEqualTo("p-1");
            assertThat(dto.getCreatedBy()).isEqualTo("user-123");
            assertThat(dto.isTieneEtiquetas()).isTrue();
            assertThat(dto.isTieneResumen()).isFalse();
            assertThat(dto.getTamanoEtiquetasBytes()).isEqualTo(1024L);
            assertThat(dto.getTamanoResumenBytes()).isZero();
        });
        verify(procesoRepo).findResumenByCreatedBy("user-123");
        verify(procesoRepo, never()).findAllResumenOrderByCreatedAtDesc();
    }

    @Test
    void listarPropios_sinAutenticacion_lanza401() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.listarPropios()).isInstanceOf(InsufficientAuthenticationException.class);
        verify(procesoRepo, never()).findResumenByCreatedBy(any());
    }

    @Test
    void listarPropios_perfilLocalConAnonymousAuth_filtraPorAnonymousUser() {
        // En perfil local con LocalSecurityConfig (permitAll), Spring inyecta
        // AnonymousAuthenticationToken con name="anonymousUser". El service debe
        // filtrar por ese mismo string para que coincida con createdBy seteado
        // por AuditorAware.
        autenticarAnonymous();
        when(procesoRepo.findResumenByCreatedBy("anonymousUser")).thenReturn(List.of());

        List<ProcesoDistribucionResumenDTO> resultado = service.listarPropios();

        assertThat(resultado).isEmpty();
        verify(procesoRepo).findResumenByCreatedBy("anonymousUser");
    }

    // ---- listarTodos -------------------------------------------------------

    @Test
    void listarPropios_tamanosNullSeNormalizanA0YFlagsFalse() {
        // La projection retorna Long que puede ser null si la columna es NULL en BD.
        // El service tiene defensa explícita; este test cierra el contrato.
        autenticarConJwt("user-Z");
        ProcesoDistribucionResumenView v = vista("p-z", "PENDIENTE", "user-Z", null, null);
        when(procesoRepo.findResumenByCreatedBy("user-Z")).thenReturn(List.of(v));

        ProcesoDistribucionResumenDTO dto = service.listarPropios().get(0);

        assertThat(dto.getTamanoEtiquetasBytes()).isZero();
        assertThat(dto.getTamanoResumenBytes()).isZero();
        assertThat(dto.isTieneEtiquetas()).isFalse();
        assertThat(dto.isTieneResumen()).isFalse();
    }

    @Test
    void listarTodos_listaVaciaDelRepoSeMapeaAListaVaciaDelDTO() {
        autenticarConJwt("admin-1");
        when(procesoRepo.findAllResumenOrderByCreatedAtDesc()).thenReturn(List.of());

        assertThat(service.listarTodos()).isEmpty();
    }

    @Test
    void listarTodos_noAplicaFiltroPorUsuario() {
        autenticarConJwt("admin-1");
        when(procesoRepo.findAllResumenOrderByCreatedAtDesc())
                .thenReturn(List.of(
                        vista("p-1", "PENDIENTE", "user-A", 0L, 0L), vista("p-2", "COMPLETADO", "user-B", 200L, 100L)));

        List<ProcesoDistribucionResumenDTO> resultado = service.listarTodos();

        assertThat(resultado)
                .extracting(ProcesoDistribucionResumenDTO::getCreatedBy)
                .containsExactly("user-A", "user-B");
        verify(procesoRepo).findAllResumenOrderByCreatedAtDesc();
        verify(procesoRepo, never()).findResumenByCreatedBy(any());
    }

    // ---- verificarOwnership ------------------------------------------------

    @Test
    void verificarOwnership_devuelveProcesoSiPerteneceAlUsuario() {
        autenticarConJwt("user-A");
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-1")
                .estado("PENDIENTE")
                .build();
        when(procesoRepo.findByProcesoIdAndCreatedBy("p-1", "user-A")).thenReturn(Optional.of(proceso));

        ProcesoDistribucion resultado = service.verificarOwnership("p-1");

        assertThat(resultado).isSameAs(proceso);
    }

    @Test
    void verificarOwnership_lanza404SiProcesoEsDeOtroUsuario() {
        // Defensa intencional: 404 (no 403) para no filtrar la existencia del
        // proceso a un usuario que no es dueño. Verificamos solo el tipo;
        // el mensaje puede cambiar (i18n, refactor) sin romper el contrato.
        autenticarConJwt("user-B");
        when(procesoRepo.findByProcesoIdAndCreatedBy("p-1", "user-B")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificarOwnership("p-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verificarOwnership_lanza404SiProcesoNoExiste() {
        autenticarConJwt("user-A");
        when(procesoRepo.findByProcesoIdAndCreatedBy(eq("inexistente"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificarOwnership("inexistente"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verificarOwnership_sinAutenticacion_lanza401() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.verificarOwnership("p-1"))
                .isInstanceOf(InsufficientAuthenticationException.class);
        verify(procesoRepo, never()).findByProcesoIdAndCreatedBy(any(), any());
    }

    // ---- Helpers -----------------------------------------------------------

    private static void autenticarConJwt(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject(sub)
                .claim("realm_access", Map.of("roles", List.of("DISTRIBUIDOR")))
                .build();
        Authentication auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_DISTRIBUIDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void autenticarAnonymous() {
        Authentication auth = new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static ProcesoDistribucionResumenView vista(
            String procesoId, String estado, String createdBy, Long tamEtiq, Long tamRes) {
        return new ProcesoDistribucionResumenView() {
            @Override
            public String getProcesoId() {
                return procesoId;
            }

            @Override
            public String getEstado() {
                return estado;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return LocalDateTime.now();
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return LocalDateTime.now();
            }

            @Override
            public String getCreatedBy() {
                return createdBy;
            }

            @Override
            public Long getTamanoEtiquetasBytes() {
                return tamEtiq;
            }

            @Override
            public Long getTamanoResumenBytes() {
                return tamRes;
            }
        };
    }
}
