package com.eliasgonzalez.cartones.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void tryConsume_permiteHastaCapacidad() {
        RateLimiter limiter = new RateLimiter(5, 30);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1"))
                    .as("intento %d debería pasar", i + 1)
                    .isTrue();
        }
    }

    @Test
    void tryConsume_bloqueaDespuesDeAgotarCapacidad() {
        RateLimiter limiter = new RateLimiter(2, 30);
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isFalse();
    }

    @Test
    void tryConsume_bucketsPorClaveSonIndependientes() {
        RateLimiter limiter = new RateLimiter(2, 30);
        limiter.tryConsume(RateCategory.UPLOAD, "user-1");
        limiter.tryConsume(RateCategory.UPLOAD, "user-1");
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isFalse();
        // Otro user mantiene su capacidad completa.
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-2")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-2")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-2")).isFalse();
    }

    @Test
    void tryConsume_categoriasSonIndependientesParaElMismoUsuario() {
        // Razón de la categorización: agotar UPLOAD no debe bloquear COMPUTE
        // y viceversa. Cada uno con su presupuesto.
        RateLimiter limiter = new RateLimiter(1, 1);
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isFalse();
        // COMPUTE del mismo user todavía intacto.
        assertThat(limiter.tryConsume(RateCategory.COMPUTE, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.COMPUTE, "user-1")).isFalse();
    }

    @Test
    void tryConsume_rpmDistintoPorCategoria() {
        // UPLOAD=2, COMPUTE=5: el del COMPUTE permite más antes de cortar.
        RateLimiter limiter = new RateLimiter(2, 5);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(RateCategory.COMPUTE, "user-1")).isTrue();
        }
        assertThat(limiter.tryConsume(RateCategory.COMPUTE, "user-1")).isFalse();
        // UPLOAD por separado: hasta 2.
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isTrue();
        assertThat(limiter.tryConsume(RateCategory.UPLOAD, "user-1")).isFalse();
    }
}
