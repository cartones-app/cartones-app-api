package com.eliasgonzalez.cartones.distribucion.preferencias.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * IT del repositorio con Postgres real (Testcontainers) — valida que la
 * migración V6 esté alineada con la entidad (columnas, CHECK constraints,
 * tipos VARCHAR de los enums).
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.test.database.replace=NONE")
class PreferenciasDistribuidorRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private PreferenciasDistribuidorRepository repo;

    @Autowired
    private TestEntityManager em;

    @Test
    void persistYRecuperarConservaLosEnums() {
        var p = PreferenciasDistribuidor.builder()
                .username("juan")
                .layoutEtiqueta(LayoutEtiqueta.CUATRO_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.INTERCALADO)
                .build();
        repo.saveAndFlush(p);
        em.clear();

        var encontrado = repo.findById("juan").orElseThrow();
        assertThat(encontrado.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        assertThat(encontrado.getOrdenEtiqueta()).isEqualTo(OrdenEtiqueta.INTERCALADO);
        assertThat(encontrado.getCreatedAt()).isNotNull();
        assertThat(encontrado.getUpdatedAt()).isNotNull();
    }

    @Test
    void checkConstraintRechazaLayoutInvalido() {
        // El @Enumerated(STRING) solo guarda nombres del enum Java; para
        // forzar un valor inválido hay que pegarle al SQL directamente.
        assertThatThrownBy(() -> em.getEntityManager()
                        .createNativeQuery(
                                "INSERT INTO preferencias_distribuidor (username, layout_etiqueta, orden_etiqueta) "
                                        + "VALUES ('bad', 'CINCO_POR_HOJA', 'SECUENCIAL')")
                        .executeUpdate())
                .isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void checkConstraintRechazaOrdenInvalido() {
        assertThatThrownBy(() -> em.getEntityManager()
                        .createNativeQuery(
                                "INSERT INTO preferencias_distribuidor (username, layout_etiqueta, orden_etiqueta) "
                                        + "VALUES ('bad', 'TRES_POR_HOJA', 'ALEATORIO')")
                        .executeUpdate())
                .isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }
}
