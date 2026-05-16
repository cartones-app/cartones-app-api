package com.eliasgonzalez.cartones.common.flags.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.common.flags.FeatureFlagsService;
import com.eliasgonzalez.cartones.common.flags.registry.FlagDefinition;
import com.eliasgonzalez.cartones.common.flags.registry.FlagRegistry;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint público (en el sentido de "no admin") para que el frontend lea los
 * flags marcados como {@code publicRead} en el registry. El cliente lo usa
 * para gating de páginas: si {@code page.upload.enabled=false}, el sidebar
 * oculta el item y la URL directa muestra un cartel de página deshabilitada.
 *
 * <p>
 * Requiere usuario autenticado (cae bajo {@code /api/**} que exige
 * ADMIN o DISTRIBUIDOR en {@code SecurityConfig}). No exponemos los flags
 * a usuarios anónimos para no leakear información de configuración interna.
 *
 * <p>
 * Solo devolvemos las claves marcadas {@code publicRead=true}. Los flags
 * sensibles (excel.expose-error-details, etc) nunca aparecen acá.
 */
@RestController
@RequestMapping("/api/feature-flags")
@RequiredArgsConstructor
public class PublicFeatureFlagController {

    private final FlagRegistry registry;
    private final FeatureFlagsService flags;

    /**
     * Devuelve un map {@code clave -> valor efectivo serializado como string}
     * de los flags públicos. El cliente parsea según el tipo que ya conoce.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> listarPublicos() {
        Map<String, String> out = new LinkedHashMap<>();
        for (FlagDefinition def : registry.publicFlags()) {
            out.put(def.key(), evaluate(def));
        }
        return ResponseEntity.ok(out);
    }

    private String evaluate(FlagDefinition def) {
        return switch (def.type()) {
            case BOOLEAN -> Boolean.toString(flags.getBoolean(def.key(), parseBoolSafe(def.defaultValue())));
            case STRING -> flags.getString(def.key(), def.defaultValue());
            case LONG -> Long.toString(flags.getLong(def.key(), parseLongSafe(def.defaultValue())));
        };
    }

    private static boolean parseBoolSafe(String s) {
        return Boolean.parseBoolean(s);
    }

    private static long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
