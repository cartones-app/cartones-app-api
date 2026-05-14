package com.eliasgonzalez.cartones.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter in-memory por clave (típicamente sub del JWT, fallback IP)
 * para los endpoints de upload. Token bucket vía Bucket4j.
 *
 * Configurable con {@code APP_UPLOADS_RATE_LIMIT_RPM} (default 10
 * req/min/usuario).
 *
 * Single-instance: el estado vive en memoria del proceso. Si la app se escala
 * horizontalmente habrá que migrar a un Bucket4j distribuido (Hazelcast/Redis).
 */
@Component
@Slf4j
public class UploadRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long requestsPerMinute;

    public UploadRateLimiter(@Value("${app.uploads.rate-limit.rpm:10}") long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        log.info("UploadRateLimiter configurado: {} req/min por clave.", requestsPerMinute);
    }

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, this::nuevoBucket);
        return bucket.tryConsume(1);
    }

    private Bucket nuevoBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
