package com.eliasgonzalez.cartones.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import com.eliasgonzalez.cartones.distribucion.preferencias.repository.PreferenciasDistribuidorRepository;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Integration test del flujo de detección y propagación de renames. Necesita
 * Postgres real (Testcontainers) porque la lógica está en SQL nativo contra
 * tablas reales con CHECK constraints.
 *
 * <p>Setup: cargamos PreferenciasDistribuidorRepository para insertar/leer
 * la row de prueba sin tocar SQL crudo.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.test.database.replace=NONE")
@Import({UserIdentityServiceIT.Config.class})
class UserIdentityServiceIT extends AbstractPostgresIT {

    @TestConfiguration
    static class Config {
        @Bean
        UserIdentityService userIdentityService(UserIdentityRepository repo) {
            return new UserIdentityService(repo);
        }
    }

    @Autowired private UserIdentityService service;
    @Autowired private UserIdentityRepository userIdentityRepo;
    @Autowired private PreferenciasDistribuidorRepository preferenciasRepo;
    @Autowired private TestEntityManager testEm;

    @PersistenceContext
    private EntityManager em;

    @Test
    void primerLoginCreaRowUserIdentity() {
        String sub = UUID.randomUUID().toString();

        service.registrarUsoYPropagarSiCambio(sub, "juan");

        var ui = userIdentityRepo.findById(sub).orElseThrow();
        assertThat(ui.getCurrentPreferredUsername()).isEqualTo("juan");
        assertThat(ui.getRenameCount()).isZero();
    }

    @Test
    void loginRepetidoMismoUsernameNoBumpeaRename() {
        String sub = UUID.randomUUID().toString();
        service.registrarUsoYPropagarSiCambio(sub, "juan");
        service.registrarUsoYPropagarSiCambio(sub, "juan");

        var ui = userIdentityRepo.findById(sub).orElseThrow();
        assertThat(ui.getRenameCount()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // Forzar visibilidad cross-tx
    void renameDePreferenciasDistribuidorPropaga() {
        String sub = UUID.randomUUID().toString();
        service.registrarUsoYPropagarSiCambio(sub, "juan");

        preferenciasRepo.saveAndFlush(PreferenciasDistribuidor.builder()
                .username("juan")
                .layoutEtiqueta(LayoutEtiqueta.CUATRO_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.INTERCALADO)
                .build());

        // Rename
        service.registrarUsoYPropagarSiCambio(sub, "juanperez");

        // La row vieja desaparece, aparece bajo el nombre nuevo con los mismos values
        assertThat(preferenciasRepo.findById("juan")).isEmpty();
        var renamed = preferenciasRepo.findById("juanperez").orElseThrow();
        assertThat(renamed.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        assertThat(renamed.getOrdenEtiqueta()).isEqualTo(OrdenEtiqueta.INTERCALADO);

        var ui = userIdentityRepo.findById(sub).orElseThrow();
        assertThat(ui.getCurrentPreferredUsername()).isEqualTo("juanperez");
        assertThat(ui.getRenameCount()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void renameConColisionEnPreferenciasBorraLaRowViejaYPreservaLaDestino() {
        String sub = UUID.randomUUID().toString();
        service.registrarUsoYPropagarSiCambio(sub, "juan");

        // Row vieja del usuario
        preferenciasRepo.saveAndFlush(PreferenciasDistribuidor.builder()
                .username("juan")
                .layoutEtiqueta(LayoutEtiqueta.TRES_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.SECUENCIAL)
                .build());
        // Row preexistente con el username destino del rename
        preferenciasRepo.saveAndFlush(PreferenciasDistribuidor.builder()
                .username("juanperez")
                .layoutEtiqueta(LayoutEtiqueta.CUATRO_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.INTERCALADO)
                .build());

        service.registrarUsoYPropagarSiCambio(sub, "juanperez");

        // La row destino NO debe haberse sobreescrito.
        var destino = preferenciasRepo.findById("juanperez").orElseThrow();
        assertThat(destino.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        // La row vieja se borra (no la dejamos huérfana porque su contenido es
        // inaccesible para el user, que ahora está identificado por 'juanperez').
        assertThat(preferenciasRepo.findById("juan")).isEmpty();
        // user_identity se actualiza igual.
        assertThat(userIdentityRepo.findById(sub).orElseThrow().getCurrentPreferredUsername())
                .isEqualTo("juanperez");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void renamePropagaCreatedByEnTablasAuditables() {
        String sub = UUID.randomUUID().toString();
        service.registrarUsoYPropagarSiCambio(sub, "juan");

        // Insertamos manualmente una row con created_by='juan' en proceso_distribucion
        // (la tabla más común). Usamos SQL nativo para no depender de toda la cadena
        // de servicios de distribución.
        em.createNativeQuery("INSERT INTO proceso_distribucion (proceso_id, estado, created_at, updated_at, created_by) "
                        + "VALUES (:id, 'pendiente', NOW(), NOW(), :cb)")
                .setParameter("id", "test-proceso-" + sub)
                .setParameter("cb", "juan")
                .executeUpdate();

        service.registrarUsoYPropagarSiCambio(sub, "juanperez");

        String createdBy = (String) em.createNativeQuery(
                        "SELECT created_by FROM proceso_distribucion WHERE proceso_id = :id")
                .setParameter("id", "test-proceso-" + sub)
                .getSingleResult();
        assertThat(createdBy).isEqualTo("juanperez");
    }
}
