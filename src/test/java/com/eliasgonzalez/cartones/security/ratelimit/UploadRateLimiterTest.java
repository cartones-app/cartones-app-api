package com.eliasgonzalez.cartones.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UploadRateLimiterTest {

    @Test
    void tryConsume_permiteHastaCapacidad() {
        UploadRateLimiter limiter = new UploadRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("user-1"))
                    .as("intento %d debería pasar", i + 1)
                    .isTrue();
        }
    }

    @Test
    void tryConsume_bloqueaDespuesDeAgotarCapacidad() {
        UploadRateLimiter limiter = new UploadRateLimiter(2);

        assertThat(limiter.tryConsume("user-1")).isTrue();
        assertThat(limiter.tryConsume("user-1")).isTrue();
        assertThat(limiter.tryConsume("user-1")).isFalse();
    }

    @Test
    void tryConsume_bucketsPorClaveSonIndependientes() {
        UploadRateLimiter limiter = new UploadRateLimiter(2);

        // user-1 agota su bucket.
        limiter.tryConsume("user-1");
        limiter.tryConsume("user-1");
        assertThat(limiter.tryConsume("user-1")).isFalse();

        // user-2 todavía tiene su capacidad completa.
        assertThat(limiter.tryConsume("user-2")).isTrue();
        assertThat(limiter.tryConsume("user-2")).isTrue();
        assertThat(limiter.tryConsume("user-2")).isFalse();
    }

    @Test
    void tryConsume_capacidadAlta_permitirMuchasRequests() {
        UploadRateLimiter limiter = new UploadRateLimiter(100);

        int permitidos = 0;
        for (int i = 0; i < 100; i++) {
            if (limiter.tryConsume("user-x"))
                permitidos++;
        }

        assertThat(permitidos).isEqualTo(100);
        assertThat(limiter.tryConsume("user-x")).isFalse();
    }
}
