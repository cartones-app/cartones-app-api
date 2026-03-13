package com.eliasgonzalez.cartones.security.filter;

import com.eliasgonzalez.cartones.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación JWT que intercepta cada petición HTTP
 * y valida el token JWT presente en el header Authorization.
 *
 * Este filtro se ejecuta una vez por petición (OncePerRequestFilter)
 * y establece el contexto de seguridad si el token es válido.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extraer el header Authorization
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // 2. Verificar si el header existe y tiene el formato correcto
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token JWT (remover "Bearer " del header)
        final String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            // 4. Validar el token
            if (jwtService.isTokenValid(jwt)) {
                // 5. Extraer el subject (identificador del usuario)
                String subject = jwtService.extractSubject(jwt);

                // 6. Verificar que no haya autenticación previa en el contexto
                if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 7. Crear el token de autenticación con authorities
                    // Por ahora, asignar rol USER por defecto
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_USER")
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            subject,
                            null,
                            authorities
                    );

                    // 8. Establecer detalles adicionales del request
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 9. Establecer la autenticación en el contexto de seguridad
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Usuario autenticado: {}", subject);
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar token JWT: {}", e.getMessage());
            // No establecer autenticación - la petición continuará pero sin autorización
        }

        // 10. Continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }
}
