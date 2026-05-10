package com.eliasgonzalez.cartones.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración que necesitan un Postgres real.
 *
 * El campo `static` con `@Container` da un único contenedor compartido entre
 * todos los métodos de test de una misma clase. Spring Test reutiliza el
 * contexto entre clases que comparten configuration (ActiveProfiles + MockBeans),
 * así que en la práctica el contenedor también se reutiliza entre clases bajo
 * el mismo cache key. Cada nuevo contexto dispara Flyway de nuevo.
 *
 * No usamos `withReuse(true)`: requiere `~/.testcontainers.properties` con
 * `testcontainers.reuse.enable=true` y silenciosamente cae a comportamiento
 * default si falta esa configuración — fragilidad innecesaria para A3.1.
 *
 * Razón de no usar H2: V1__crear_esquema_normalizado.sql tiene tipos y
 * sintaxis Postgres-specific (BYTEA, OCTET_LENGTH, TIMESTAMP WITHOUT TIME ZONE).
 */
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cartones_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerPostgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
