package com.eliasgonzalez.cartones.ruta.config;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.eliasgonzalez.cartones.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Kill switch del módulo ruta — controlado por el flag openflags
 * {@code ruta.enabled} (default {@code true}).
 *
 * <p>Cuando el flag está en {@code false}, los endpoints {@code /api/ruta/**}
 * y {@code /api/admin/ruta/**} devuelven {@code 503 Service Unavailable} con
 * un cuerpo {@link ErrorResponse} consistente con el resto de las respuestas
 * de error del backend. Útil para desactivar temporalmente el módulo sin
 * redeploy si se detecta un bug crítico en runtime.
 *
 * <p><b>Orden respecto a Spring Security</b>: los {@code HandlerInterceptor}
 * MVC corren <i>después</i> de la cadena de filtros de Spring Security. Un
 * request sin JWT válido nunca llega acá — lo rechaza el resource server con
 * 401/403 antes. Por eso este interceptor no necesita reimplementar checks de
 * autenticación; solo decide habilitar/deshabilitar el módulo para usuarios
 * que ya pasaron auth.
 *
 * <p>El interceptor se registra en {@link RutaWebConfig}.
 */
@Component
@Slf4j
public class RutaKillSwitchInterceptor implements HandlerInterceptor {

    /** Flag key. Default {@code true}: módulo habilitado salvo opt-out explícito. */
    static final String FLAG_RUTA_ENABLED = "ruta.enabled";

    private final OpenFlagsClient flags;
    private final ObjectMapper objectMapper;

    public RutaKillSwitchInterceptor(OpenFlagsClient flags, ObjectMapper objectMapper) {
        this.flags = flags;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean enabled = flags.getBooleanValue(FLAG_RUTA_ENABLED, true);
        if (enabled) {
            return true;
        }
        log.warn(
                "Módulo ruta deshabilitado por flag '{}', rechazando {} {}",
                FLAG_RUTA_ENABLED,
                request.getMethod(),
                request.getRequestURI());

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message("El módulo ruta está temporalmente deshabilitado.")
                .details(List.of())
                .instance(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }
}
