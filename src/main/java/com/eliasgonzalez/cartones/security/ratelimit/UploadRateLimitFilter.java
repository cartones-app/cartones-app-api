package com.eliasgonzalez.cartones.security.ratelimit;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica rate limiting a los endpoints de upload (carga de Excel).
 *
 * Clave del bucket:
 * - sub del JWT si la request está autenticada (perfil con Keycloak),
 * - remote address en otro caso (perfil local sin auth).
 *
 * Si el bucket está vacío responde 429 Too Many Requests con un body
 * mínimo en JSON. No se ataja antes del filtro de Spring Security: corre
 * después, sobre requests ya autenticadas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of("/api/vendedores/carga", "/api/ruta/carga");

    private final UploadRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !RATE_LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = resolverClave(request);
        if (rateLimiter.tryConsume(key)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn(
                "Rate limit excedido en {} para clave='{}'",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(key));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Demasiados uploads. Esperá un minuto y reintentá.\"}");
    }

    private String resolverClave(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            return "sub:" + jwt.getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
