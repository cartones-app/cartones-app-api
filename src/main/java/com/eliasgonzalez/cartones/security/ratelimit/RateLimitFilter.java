package com.eliasgonzalez.cartones.security.ratelimit;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica rate limiting a los endpoints costosos del backend.
 *
 * <p>Cubre dos familias (ver {@link RateCategory}):
 * <ul>
 *   <li>{@code UPLOAD} — carga de Excel ({@code /api/vendedores/carga},
 *       {@code /api/ruta/carga}).</li>
 *   <li>{@code COMPUTE} — simulación de distribución, generación de PDFs,
 *       exportación de Excel.</li>
 * </ul>
 *
 * <p>Path matching con {@link AntPathMatcher} (los endpoints COMPUTE tienen
 * id variable). Solo POST — los demás métodos no llegan acá.
 *
 * <p>Clave del bucket: sub del JWT si la request está autenticada (perfil
 * con Keycloak); remote address en otro caso (perfil local sin auth).
 *
 * <p>Si el bucket está vacío responde 429 con un body mínimo JSON. Para
 * abuso volumétrico de endpoints livianos (GET listados, etc.) el rate
 * limit vive en el proxy reverso, no acá.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Patrones path → categoría. Orden: específicos primero (los uploads son
     * literales, los compute usan wildcard `*` para el id del proceso/sesión).
     * `LinkedHashMap` preserva el orden de inserción — si en el futuro hay
     * solapamientos, lo que va primero gana.
     */
    private static final Map<String, RateCategory> PATH_PATTERNS = new LinkedHashMap<>();

    static {
        PATH_PATTERNS.put("/api/vendedores/carga", RateCategory.UPLOAD);
        PATH_PATTERNS.put("/api/ruta/carga", RateCategory.UPLOAD);
        PATH_PATTERNS.put("/api/distribuciones/*/simular", RateCategory.COMPUTE);
        PATH_PATTERNS.put("/api/distribuciones/*/archivos", RateCategory.COMPUTE);
        PATH_PATTERNS.put("/api/ruta/*/exportar", RateCategory.COMPUTE);
    }

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        return resolverCategoria(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateCategory categoria = resolverCategoria(request.getRequestURI());
        // shouldNotFilter ya garantiza categoria != null, pero defendemos contra
        // refactors futuros que cambien el orden de invocación.
        if (categoria == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = resolverClave(request);
        if (rateLimiter.tryConsume(categoria, key)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn(
                "Rate limit excedido en {} (categoria={}, clave={})",
                LogSanitizer.safe(request.getRequestURI()),
                categoria,
                LogSanitizer.safe(key));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Demasiadas requests. Esperá un minuto y reintentá.\"}");
    }

    private RateCategory resolverCategoria(String path) {
        for (Map.Entry<String, RateCategory> entry : PATH_PATTERNS.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolverClave(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            return "sub:" + jwt.getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
