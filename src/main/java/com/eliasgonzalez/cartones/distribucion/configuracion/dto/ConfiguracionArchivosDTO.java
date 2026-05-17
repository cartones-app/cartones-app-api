package com.eliasgonzalez.cartones.distribucion.configuracion.dto;

import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;

import java.time.LocalDateTime;

public record ConfiguracionArchivosDTO(
        int retencionMeses,
        boolean eliminacionActiva,
        LocalDateTime updatedAt,
        String modifiedBy) {

    public static ConfiguracionArchivosDTO fromEntity(ConfiguracionArchivos c) {
        return new ConfiguracionArchivosDTO(
                c.getRetencionMeses(),
                c.isEliminacionActiva(),
                c.getUpdatedAt(),
                c.getModifiedBy());
    }
}
