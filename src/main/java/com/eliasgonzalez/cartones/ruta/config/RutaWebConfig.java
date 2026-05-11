package com.eliasgonzalez.cartones.ruta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración MVC del módulo ruta. Registra el {@link RutaKillSwitchInterceptor}
 * sobre los paths del módulo.
 */
@Configuration
public class RutaWebConfig implements WebMvcConfigurer {

    private final RutaKillSwitchInterceptor killSwitch;

    public RutaWebConfig(RutaKillSwitchInterceptor killSwitch) {
        this.killSwitch = killSwitch;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(killSwitch)
                .addPathPatterns("/api/ruta/**", "/api/admin/ruta/**");
    }
}
