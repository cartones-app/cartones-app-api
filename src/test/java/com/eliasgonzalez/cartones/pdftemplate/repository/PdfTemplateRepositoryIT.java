package com.eliasgonzalez.cartones.pdftemplate.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

/**
 * Tests de integración del PdfTemplateRepository sobre Postgres real
 * (Testcontainers). Foco:
 * - `findByTipoAndActivoTrue` discrimina por tipo.
 * - `desactivarOtrosDelTipo` solo toca filas del mismo tipo.
 * - El índice parcial único `pdf_template_activo_unico` (V6) impide
 *   tener dos activos del mismo tipo simultáneamente.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.test.database.replace=NONE")
class PdfTemplateRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private PdfTemplateRepository repo;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void limpiar() {
        em.getEntityManager().createQuery("DELETE FROM PdfTemplate").executeUpdate();
    }

    @Test
    void findByTipoAndActivoTrue_devuelveSoloElActivoDelTipoPedido() {
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "etq activo", true));
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "etq viejo", false));
        repo.save(template(PdfTemplateTipo.RESUMEN, "res activo", true));
        em.flush();

        assertThat(repo.findByTipoAndActivoTrue(PdfTemplateTipo.ETIQUETAS))
                .isPresent()
                .get()
                .extracting(PdfTemplate::getNombre)
                .isEqualTo("etq activo");
        assertThat(repo.findByTipoAndActivoTrue(PdfTemplateTipo.RESUMEN))
                .isPresent()
                .get()
                .extracting(PdfTemplate::getNombre)
                .isEqualTo("res activo");
    }

    @Test
    void findByTipoAndActivoTrue_vacioSiNoHayActivo() {
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "etq", false));
        em.flush();

        assertThat(repo.findByTipoAndActivoTrue(PdfTemplateTipo.ETIQUETAS)).isEmpty();
    }

    @Test
    void indiceParcialBloqueaDosActivosDelMismoTipo() {
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "primero", true));
        em.flush();

        PdfTemplate segundo = template(PdfTemplateTipo.ETIQUETAS, "segundo", true);
        // El índice parcial único hace que el insert falle. La excepción de JPA
        // se materializa al flush.
        assertThatThrownBy(() -> {
                    repo.save(segundo);
                    em.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indiceParcialPermiteVariosInactivosDelMismoTipo() {
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "v1", false));
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "v2", false));
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "v3", false));
        em.flush();

        assertThat(repo.findAllByOrderByCreatedAtDesc()).hasSize(3);
    }

    @Test
    void desactivarOtrosDelTipo_soloTocaFilasDelMismoTipoExcluyendoElTarget() {
        PdfTemplate etqActivo = repo.save(template(PdfTemplateTipo.ETIQUETAS, "etq activo", true));
        PdfTemplate etqOtro = repo.save(template(PdfTemplateTipo.ETIQUETAS, "etq otro", false));
        PdfTemplate resActivo = repo.save(template(PdfTemplateTipo.RESUMEN, "res activo", true));
        em.flush();

        // Simulamos "activar etqOtro" — antes hay que desactivar a etqActivo.
        int filas = repo.desactivarOtrosDelTipo(PdfTemplateTipo.ETIQUETAS, etqOtro.getId());
        em.flush();
        em.clear();

        assertThat(filas).isEqualTo(1);
        // etqActivo ya no debe estar activo.
        assertThat(repo.findById(etqActivo.getId()).orElseThrow().isActivo()).isFalse();
        // resActivo del otro tipo intacto.
        assertThat(repo.findById(resActivo.getId()).orElseThrow().isActivo()).isTrue();
        // etqOtro sigue sin estar activo (no se le tocó — se va a activar después).
        assertThat(repo.findById(etqOtro.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void findAllByOrderByCreatedAtDesc_devuelveDelMasNuevoAlMasViejo() throws InterruptedException {
        repo.save(template(PdfTemplateTipo.ETIQUETAS, "viejo", false));
        em.flush();
        Thread.sleep(10); // garantizar diferencia en created_at
        repo.save(template(PdfTemplateTipo.RESUMEN, "nuevo", false));
        em.flush();

        var lista = repo.findAllByOrderByCreatedAtDesc();
        assertThat(lista).extracting(PdfTemplate::getNombre).containsExactly("nuevo", "viejo");
    }

    private static PdfTemplate template(PdfTemplateTipo tipo, String nombre, boolean activo) {
        return PdfTemplate.builder()
                .id(UUID.randomUUID().toString())
                .tipo(tipo)
                .nombre(nombre)
                .schemaJson("{\"basePdf\":\"BLANK_PDF\",\"schemas\":[[]]}")
                .activo(activo)
                .build();
    }
}
