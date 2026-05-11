package com.eliasgonzalez.cartones.ruta.config;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Kill switch del módulo ruta — controlado por el flag openflags
 * {@code ruta.enabled} (default {@code true}).
 *
 * <p>Cuando el flag está en {@code false}, los endpoints {@code /api/ruta/**}
 * y {@code /api/admin/ruta/**} devuelven {@code 503 Service Unavailable} con
 * un cuerpo JSON mínimo. Útil para desactivar temporalmente el módulo sin
 * redeploy si se detecta un bug crítico en runtime.
 *
 * <p>El interceptor se registra en {@link RutaWebConfig}.
 */
@Component
@Slf4j
public class RutaKillSwitchInterceptor implements HandlerInterceptor {

    /** Flag key. Default {@code true}: el módulo está habilitado salvo opt-out explícito. */
    static final String FLAG_RUTA_ENABLED = "ruta.enabled";

    private final OpenFlagsClient flags;

    public RutaKillSwitchInterceptor(OpenFlagsClient flags) {
        this.flags = flags;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean enabled = flags.getBooleanValue(FLAG_RUTA_ENABLED, true);
        if (enabled) {
            return true;
        }
        log.warn("Módulo ruta deshabilitado por flag '{}', rechazando {} {}",
                FLAG_RUTA_ENABLED, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = """
                {"status":503,"error":"Service Unavailable",\
                "message":"El módulo ruta está temporalmente deshabilitado.",\
                "instance":"%s","timestamp":"%s"}"""
                .formatted(request.getRequestURI(), Instant.now().toString());
        response.getWriter().write(body);
        return false;
    }
}
