package com.eliasgonzalez.cartones.security.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuración de Spring Security — Resource Server con Keycloak.
 * Activa en todos los perfiles EXCEPTO 'local'.
 *
 * - Stateless: sin sesiones HTTP
 * - Autenticación: tokens JWT emitidos por Keycloak
 * - Autorización:
 *     /api/admin/** → rol ADMIN
 *     /api/**       → rol ADMIN o DISTRIBUIDOR
 *     /actuator/**  → rol ADMIN (excepto /actuator/health que es público)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!local")
@Slf4j
public class SecurityConfig {

    @Value("${app.cors.origins}")
    private String corsOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configurando Spring Security como Resource Server (Keycloak)");

        // CSRF está deshabilitado intencionalmente: este backend es un Resource Server
        // stateless (JWT en Authorization header, sin cookies de sesión). CSRF aplica
        // a apps que autentican por cookie — el navegador NO envía Authorization
        // automáticamente cross-origin, así que un atacante no puede forjar la request.
        // CodeQL flag (java/spring-disabled-csrf-protection) es un falso positivo
        // para este patrón. Si en el futuro agregamos cookie-based auth, reactivar.
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Headers de seguridad por default. Spring inyecta varios; reforzamos:
                //   - HSTS: fuerza HTTPS en el navegador por 1 año. Solo aplica si el
                //     request original venía por TLS (la mayoría en prod via CF Tunnel).
                //   - X-Frame-Options DENY: no permitimos embed en iframes (clickjacking).
                //   - Referrer-Policy: no leakeamos URLs del backend hacia terceros.
                //   - Content-Type-Options nosniff: el navegador respeta el MIME.
                // No setamos CSP — el backend solo sirve JSON/PDF/Excel, no HTML.
                .headers(headers -> headers.httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000L))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(opts -> {})
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                .permitAll()
                                .requestMatchers("/api/admin/**")
                                .hasRole("ADMIN")
                                .requestMatchers("/actuator/**")
                                .hasRole("ADMIN")
                                .requestMatchers("/api/**")
                                .hasAnyRole("ADMIN", "DISTRIBUIDOR")
                                .anyRequest()
                                .denyAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configurando CORS con orígenes: {}", corsOrigins);

        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowedOrigins =
                Arrays.stream(corsOrigins.split(",")).map(String::trim).toList();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
