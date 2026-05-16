package com.eliasgonzalez.cartones.security.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro post-autenticación que mantiene la tabla {@code user_identity}
 * sincronizada con los claims del JWT entrante. Si detecta un cambio de
 * {@code preferred_username} para un {@code sub} ya conocido, delega al
 * {@link UserIdentityService} que propaga el rename a las columnas
 * locales que referencian al username viejo.
 *
 * <p>Posicionamiento: debe correr DESPUÉS del filter de OAuth2 Resource
 * Server (que pone el {@link JwtAuthenticationToken} en el contexto).
 * Spring lo inserta automáticamente como bean {@code @Component}, y el
 * orden default lo deja al final — suficiente porque solo consulta el
 * principal ya autenticado.
 *
 * <p>Cost de runtime: SELECT por PK + (raro) UPDATEs de propagación. Se
 * ejecuta en transacción {@code REQUIRES_NEW} del service, así que un
 * fallo no tumba la request. Tampoco bloquea: si el filter falla,
 * loggeamos y seguimos.
 *
 * <p>Profile {@code !local}: el perfil {@code local} desactiva Spring
 * Security, así que el filter no aplica.
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class UserIdentityTrackingFilter extends OncePerRequestFilter {

    private final UserIdentityService service;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String sub = jwt.getSubject();
                String preferredUsername = jwt.getClaimAsString("preferred_username");
                if (sub != null && preferredUsername != null) {
                    service.registrarUsoYPropagarSiCambio(sub, preferredUsername);
                }
            }
        } catch (Exception e) {
            // Defensivo: nunca tumbar la request por un fallo en el tracking
            // de identidad. Se loggea para investigar.
            log.error("UserIdentityTrackingFilter falló (no bloqueante): {}", e.getMessage(), e);
        }
        chain.doFilter(request, response);
    }
}
