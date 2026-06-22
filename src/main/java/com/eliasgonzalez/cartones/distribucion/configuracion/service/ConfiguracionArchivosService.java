package com.eliasgonzalez.cartones.distribucion.configuracion.service;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;
import com.eliasgonzalez.cartones.distribucion.configuracion.repository.ConfiguracionArchivosRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfiguracionArchivosService {

    private static final long SINGLETON_ID = 1L;

    private final ConfiguracionArchivosRepository repo;

    @Transactional(readOnly = true)
    public ConfiguracionArchivos obtener() {
        return cargarSingleton();
    }

    @Transactional
    public ConfiguracionArchivos actualizar(int retencionMeses, boolean eliminacionActiva) {
        // Llamamos al helper privado en lugar de `this.obtener()` para no
        // depender del proxy de Spring en el self-invoke (Sonar java:S6809).
        // El @Transactional del método público ya cubre toda la operación.
        ConfiguracionArchivos config = cargarSingleton();
        config.setRetencionMeses(retencionMeses);
        config.setEliminacionActiva(eliminacionActiva);
        log.info("Configuración de archivos actualizada: retencionMeses={}, eliminacionActiva={}",
                retencionMeses, eliminacionActiva);
        return repo.save(config);
    }

    private ConfiguracionArchivos cargarSingleton() {
        return repo.findById(SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuración de archivos no encontrada (id=1 debe existir por seed).",
                        List.of()));
    }
}
