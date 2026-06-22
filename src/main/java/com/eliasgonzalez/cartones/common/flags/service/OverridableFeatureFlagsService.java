package com.eliasgonzalez.cartones.common.flags.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.flags.FeatureFlagsAdminService;
import com.eliasgonzalez.cartones.common.flags.FeatureFlagsService;
import com.eliasgonzalez.cartones.common.flags.domain.FlagOverride;
import com.eliasgonzalez.cartones.common.flags.domain.enums.FlagValueType;
import com.eliasgonzalez.cartones.common.flags.dto.FlagViewDTO;
import com.eliasgonzalez.cartones.common.flags.dto.SetFlagRequest;
import com.eliasgonzalez.cartones.common.flags.exception.FlagNotFoundException;
import com.eliasgonzalez.cartones.common.flags.exception.InvalidFlagValueException;
import com.eliasgonzalez.cartones.common.flags.registry.FlagDefinition;
import com.eliasgonzalez.cartones.common.flags.registry.FlagRegistry;
import com.eliasgonzalez.cartones.common.flags.repository.FlagOverrideRepository;
import com.eliasgonzalez.cartones.common.logging.LogSanitizer;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación que combina openflags (defaults read-only desde
 * {@code classpath:flags.yml}) con una tabla {@code flag_override} mutable
 * desde la UI admin.
 *
 * <p><b>Estrategia de lectura</b>: en hot path no se va a la DB. Mantenemos
 * una cache in-memory ({@link ConcurrentHashMap}) que se hidrata al startup
 * y se actualiza atómicamente en cada write del admin. Si el flag no tiene
 * override en la cache, delegamos a {@code OpenFlagsClient}.
 *
 * <p><b>Multi-instancia</b>: hoy el deployment es single instance. Si en el
 * futuro se escala, la cache de cada instancia diverge hasta que reinicien.
 * Mitigaciones posibles cuando aplique: pub/sub vía Postgres LISTEN/NOTIFY,
 * polling cada N segundos, o esperar la API mutable de openflags (su
 * provider ya hace broadcast a {@code FlagChangeListener}).
 *
 * <p><b>Swap a openflags</b>: cuando el paquete exponga un
 * {@code MutableFlagProvider}, esta clase puede borrarse — la app solo
 * consume {@link FeatureFlagsService}, no esta impl. Ver
 * {@code /data/proyectos/openflags/main/notes/idea-mutable-flag-provider.md}.
 */
@Service
@Slf4j
public class OverridableFeatureFlagsService implements FeatureFlagsService, FeatureFlagsAdminService {

    private final OpenFlagsClient openFlags;
    private final FlagOverrideRepository repository;
    private final FlagRegistry registry;

    /** Snapshot in-memory de los overrides activos. Inicializado en {@link #warmup()}. */
    private final ConcurrentHashMap<String, FlagOverride> cache = new ConcurrentHashMap<>();

    public OverridableFeatureFlagsService(
            OpenFlagsClient openFlags, FlagOverrideRepository repository, FlagRegistry registry) {
        this.openFlags = openFlags;
        this.repository = repository;
        this.registry = registry;
    }

    @PostConstruct
    void warmup() {
        repository.findAll().forEach(o -> cache.put(o.getFlagKey(), o));
        log.info("FeatureFlagsService warmup: {} override(s) cargado(s) en cache", cache.size());
    }

    // ============================================================================
    // Lectura (FeatureFlagsService)
    // ============================================================================

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        FlagOverride override = cache.get(key);
        if (override != null && override.getValueType() == FlagValueType.BOOLEAN) {
            return Boolean.parseBoolean(override.getValueText());
        }
        return openFlags.getBooleanValue(key, defaultValue);
    }

    @Override
    public String getString(String key, String defaultValue) {
        FlagOverride override = cache.get(key);
        if (override != null && override.getValueType() == FlagValueType.STRING) {
            return override.getValueText();
        }
        return openFlags.getStringValue(key, defaultValue);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        FlagOverride override = cache.get(key);
        if (override != null && override.getValueType() == FlagValueType.LONG) {
            try {
                return Long.parseLong(override.getValueText());
            } catch (NumberFormatException e) {
                log.warn("Override de flag '{}' no parsea como long, usando default", LogSanitizer.safe(key));
                return defaultValue;
            }
        }
        // openflags expone getNumberValue(double); para LONG casteamos.
        return (long) openFlags.getNumberValue(key, defaultValue);
    }

    // ============================================================================
    // Admin (FeatureFlagsAdminService)
    // ============================================================================

    @Override
    public List<FlagViewDTO> listarFlags() {
        List<FlagViewDTO> out = new ArrayList<>();
        for (FlagDefinition def : registry.all()) {
            out.add(toView(def, cache.get(def.key())));
        }
        return out;
    }

    @Override
    public FlagViewDTO obtenerFlag(String flagKey) {
        FlagDefinition def = registry
                .find(flagKey)
                .orElseThrow(() -> new FlagNotFoundException(flagKey));
        return toView(def, cache.get(flagKey));
    }

    @Override
    @Transactional
    public FlagViewDTO setOverride(String flagKey, SetFlagRequest request) {
        FlagDefinition def = registry
                .find(flagKey)
                .orElseThrow(() -> new FlagNotFoundException(flagKey));
        validateValue(def, request.value());

        FlagOverride override = repository.findById(flagKey).orElseGet(() -> FlagOverride.builder()
                .flagKey(flagKey)
                .valueType(def.type())
                .build());
        override.setValueType(def.type());
        override.setValueText(request.value());
        override.setReason(request.reason());

        FlagOverride saved = repository.save(override);
        cache.put(flagKey, saved);
        log.info(
                "Flag override seteado: key='{}' value='{}' reason='{}'",
                LogSanitizer.safe(flagKey),
                LogSanitizer.safe(request.value()),
                LogSanitizer.safe(request.reason()));
        return toView(def, saved);
    }

    @Override
    @Transactional
    public void clearOverride(String flagKey) {
        if (!registry.isRegistered(flagKey)) {
            throw new FlagNotFoundException(flagKey);
        }
        repository.deleteById(flagKey);
        cache.remove(flagKey);
        log.info("Flag override eliminado: key='{}'", LogSanitizer.safe(flagKey));
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private FlagViewDTO toView(FlagDefinition def, FlagOverride override) {
        String effective = override != null ? override.getValueText() : evaluateUpstream(def);
        return FlagViewDTO.builder()
                .key(def.key())
                .type(def.type())
                .description(def.description())
                .defaultValue(def.defaultValue())
                .effectiveValue(effective)
                .hasOverride(override != null)
                .overrideValue(override != null ? override.getValueText() : null)
                .overrideReason(override != null ? override.getReason() : null)
                .modifiedBy(override != null ? override.getModifiedBy() : null)
                .updatedAt(override != null ? override.getUpdatedAt() : null)
                .build();
    }

    /** Lee el valor que openflags devolvería si no hubiera override (para mostrar en UI). */
    private String evaluateUpstream(FlagDefinition def) {
        return switch (def.type()) {
            case BOOLEAN -> Boolean.toString(
                    openFlags.getBooleanValue(def.key(), Boolean.parseBoolean(def.defaultValue())));
            case STRING -> openFlags.getStringValue(def.key(), def.defaultValue());
            case LONG -> Long.toString((long) openFlags.getNumberValue(def.key(), parseLongSafe(def.defaultValue())));
        };
    }

    private static long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static void validateValue(FlagDefinition def, String value) {
        switch (def.type()) {
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new InvalidFlagValueException(def.key(), "se esperaba 'true' o 'false'");
                }
            }
            case LONG -> {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new InvalidFlagValueException(def.key(), "se esperaba un entero");
                }
            }
            case STRING -> {
                /* cualquier string no vacío vale; @NotBlank ya cubrió */
            }
        }
    }

    /** Solo para tests. */
    Optional<FlagOverride> peekCache(String key) {
        return Optional.ofNullable(cache.get(key));
    }
}
