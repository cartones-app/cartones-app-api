package com.eliasgonzalez.cartones.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad para el perfil 'local'.
 * Permite todas las peticiones sin autenticación — para desarrollo
 * sin necesidad de levantar Keycloak.
 */
@Configuration
@EnableWebSecurity
@Profile("local")
@Slf4j
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.warn("Perfil 'local' activo: seguridad deshabilitada. No usar en producción.");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
