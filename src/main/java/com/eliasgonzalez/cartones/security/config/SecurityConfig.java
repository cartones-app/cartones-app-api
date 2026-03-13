package com.eliasgonzalez.cartones.security.config;

import com.eliasgonzalez.cartones.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de Spring Security 6
 *
 * - Arquitectura Stateless (sin sesiones)
 * - Autenticación basada en JWT
 * - Protección de endpoints /api/**
 * - Configuración explícita de CORS
 * - Protección de Actuator (solo acceso autenticado)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Value("${app.cors.origins}")
    private String corsOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configurando Spring Security con arquitectura Stateless");

        http
                // Deshabilitar CSRF (no necesario en API stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Configuración de autorización de peticiones
                .authorizeHttpRequests(auth -> auth
                        // Permitir acceso público a Swagger/OpenAPI
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Permitir acceso público a health check (para monitoreo externo)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Proteger otros endpoints de Actuator (requieren autenticación)
                        .requestMatchers("/actuator/**").authenticated()

                        // Proteger todos los endpoints de la API
                        .requestMatchers("/api/**").authenticated()

                        // Cualquier otra petición debe denegar acceso por defecto
                        .anyRequest().denyAll()
                )

                // Configuración de gestión de sesiones: STATELESS
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configuración de CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Añadir el filtro JWT antes del filtro de autenticación de Spring
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración de CORS (Cross-Origin Resource Sharing)
     * Lee los orígenes permitidos desde la variable de entorno APP_CORS_ORIGINS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configurando CORS con orígenes: {}", corsOrigins);

        CorsConfiguration configuration = new CorsConfiguration();

        // Parsear los orígenes permitidos (separados por coma)
        List<String> allowedOrigins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .toList();

        configuration.setAllowedOrigins(allowedOrigins);

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "OPTIONS"
        ));

        // Headers permitidos
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-API-KEY"
        ));

        // Exponer headers en la respuesta (útil para tokens en headers)
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count"
        ));

        // Permitir envío de credenciales (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Tiempo de cacheo de la configuración CORS (1 hora)
        configuration.setMaxAge(3600L);

        // Aplicar la configuración a todos los endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
