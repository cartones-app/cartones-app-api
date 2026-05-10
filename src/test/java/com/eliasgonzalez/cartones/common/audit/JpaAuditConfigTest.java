package com.eliasgonzalez.cartones.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tests del bean AuditorAware. Define qué string queda en createdBy / modifiedBy
 * de las entidades que extienden EntidadAuditable.
 *
 * Contrato verificado:
 *  - Sin Authentication: "sistema".
 *  - JWT autenticado: jwt.getSubject() (sub del token).
 *  - AnonymousAuthenticationToken (perfil local): "anonymousUser" (auth.getName()).
 *
 * Nota: el auditor usa auth.getName(), que para JwtAuthenticationToken por
 * default retorna jwt.getSubject(). Verificamos ese contrato porque el filter
 * de DistribucionListadoService.obtenerSubActual() lo asume.
 */
class JpaAuditConfigTest {

    private final AuditorAware<String> auditor = new JpaAuditConfig().auditorAware();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacion_devuelveSistema() {
        SecurityContextHolder.clearContext();

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).contains("sistema");
    }

    @Test
    void conJwtAutenticado_devuelveSubDelJwt() {
        autenticarConJwt("user-abc-123");

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).contains("user-abc-123");
    }

    @Test
    void conAnonymousAuth_devuelveAnonymousUser() {
        // Es el caso del perfil local con LocalSecurityConfig (permitAll).
        // DistribucionListadoService.obtenerSubActual asume que es exactamente
        // este string, y en ese perfil createdBy se setea con el mismo valor —
        // así el filtrado de "ver mis procesos" devuelve resultados.
        autenticarAnonymous();

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).contains("anonymousUser");
    }

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
}
