package com.eliasgonzalez.cartones.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter in-memory por {@link RateCategory} y clave (sub del JWT,
 * fallback IP). Token bucket vía Bucket4j.
 *
 * <p>Configurable con properties:
 * <ul>
 *   <li>{@code app.rate-limit.upload.rpm} (default 10): uploads de Excel.</li>
 *   <li>{@code app.rate-limit.compute.rpm} (default 30): simular,
 *       generar archivos, exportar.</li>
 * </ul>
 *
 * <p>Single-instance: el estado vive en memoria del proceso. Si la app se
 * escala horizontalmente habrá que migrar a un Bucket4j distribuido
 * (Hazelcast/Redis).
 */
@Component
@Slf4j
public class RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<RateCategory, Long> rpmPorCategoria;

    public RateLimiter(
            @Value("${app.rate-limit.upload.rpm:10}") long uploadRpm,
            @Value("${app.rate-limit.compute.rpm:30}") long computeRpm) {
        this.rpmPorCategoria = new EnumMap<>(RateCategory.class);
        rpmPorCategoria.put(RateCategory.UPLOAD, uploadRpm);
        rpmPorCategoria.put(RateCategory.COMPUTE, computeRpm);
        log.info("RateLimiter configurado: UPLOAD={} req/min, COMPUTE={} req/min", uploadRpm, computeRpm);
    }

    /**
     * Intenta consumir un token del bucket {@code (categoría, key)}.
     * El presupuesto por categoría es independiente: agotar UPLOAD no
     * bloquea COMPUTE del mismo usuario.
     */
    public boolean tryConsume(RateCategory categoria, String key) {
        String bucketKey = categoria.name() + ":" + key;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> nuevoBucket(categoria));
        return bucket.tryConsume(1);
    }

    private Bucket nuevoBucket(RateCategory categoria) {
        long rpm = rpmPorCategoria.get(categoria);
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rpm)
                        .refillIntervally(rpm, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
