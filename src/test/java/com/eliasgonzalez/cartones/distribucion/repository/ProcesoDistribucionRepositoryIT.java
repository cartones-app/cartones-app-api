package com.eliasgonzalez.cartones.distribucion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

/**
 * Tests de integración del ProcesoDistribucionRepository sobre Postgres real
 * (Testcontainers). Foco: la query nativa con OCTET_LENGTH (introducida en
 * el Sprint A2 / código del listado de distribuciones) que H2 no entendería
 * y que es crítica porque su propósito es no traer los BLOBs a la JVM.
 *
 * Auditoría JPA está activa: el AuditorAware retorna "sistema" cuando no hay
 * SecurityContext (caso de estos tests). createdBy se popula automáticamente.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.test.database.replace=NONE")
class ProcesoDistribucionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private ProcesoDistribucionRepository repo;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void limpiar() {
        // DataJpaTest envuelve cada test en un rollback transaccional, pero
        // las queries nativas con SELECT directo a la tabla pueden ver datos
        // committeados de tests anteriores si los hubiera. Limpieza defensiva.
        em.getEntityManager().createQuery("DELETE FROM ProcesoDistribucion").executeUpdate();
    }

    @Test
    void findResumenByCreatedBy_devuelveTamanosCorrectosSinTraerBlobs() {
        byte[] etiquetas = new byte[1024]; // 1 KB
        byte[] resumen = new byte[2048]; // 2 KB

        ProcesoDistribucion p = ProcesoDistribucion.builder()
                .procesoId("p-1")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .pdfEtiquetas(etiquetas)
                .pdfResumen(resumen)
                .build();
        em.persistAndFlush(p);
        em.clear();

        // Buscamos por createdBy="sistema" porque AuditorAware lo setea cuando
        // no hay SecurityContext.
        List<ProcesoDistribucionResumenView> resultado = repo.findResumenByCreatedBy("sistema");

        assertThat(resultado).hasSize(1).first().satisfies(v -> {
            assertThat(v.getProcesoId()).isEqualTo("p-1");
            assertThat(v.getTamanoEtiquetasBytes()).isEqualTo(1024L);
            assertThat(v.getTamanoResumenBytes()).isEqualTo(2048L);
            assertThat(v.getCreatedBy()).isEqualTo("sistema");
            assertThat(v.getEstado()).isEqualTo("completado");
        });
    }

    @Test
    void findResumenByCreatedBy_pdfsNullSeReportanComoCero() {
        // El COALESCE en la query nativa transforma NULL → 0 antes de mandarlo
        // al cliente. Así el frontend nunca recibe null en los tamaños.
        ProcesoDistribucion p = ProcesoDistribucion.builder()
                .procesoId("p-2")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();
        em.persistAndFlush(p);
        em.clear();

        List<ProcesoDistribucionResumenView> resultado = repo.findResumenByCreatedBy("sistema");

        assertThat(resultado).hasSize(1).first().satisfies(v -> {
            assertThat(v.getTamanoEtiquetasBytes()).isZero();
            assertThat(v.getTamanoResumenBytes()).isZero();
        });
    }

    @Test
    void findResumenByCreatedBy_filtraPorCreatedBy() {
        em.persistAndFlush(ProcesoDistribucion.builder()
                .procesoId("p-3")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build());
        em.clear();

        List<ProcesoDistribucionResumenView> propios = repo.findResumenByCreatedBy("sistema");
        List<ProcesoDistribucionResumenView> deOtro = repo.findResumenByCreatedBy("user-x");

        assertThat(propios).hasSize(1);
        assertThat(deOtro).isEmpty();
    }

    @Test
    void findAllResumenOrderByCreatedAtDesc_listaTodosOrdenadosDescendente() throws Exception {
        em.persistAndFlush(ProcesoDistribucion.builder()
                .procesoId("p-1-vieja")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build());
        Thread.sleep(15); // garantizar createdAt distinto
        em.persistAndFlush(ProcesoDistribucion.builder()
                .procesoId("p-2-nueva")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build());
        em.clear();

        List<ProcesoDistribucionResumenView> resultado = repo.findAllResumenOrderByCreatedAtDesc();

        assertThat(resultado)
                .extracting(ProcesoDistribucionResumenView::getProcesoId)
                .containsExactly("p-2-nueva", "p-1-vieja");
    }

    @Test
    void findByProcesoIdAndCreatedBy_filtraPorAmbos() {
        em.persistAndFlush(ProcesoDistribucion.builder()
                .procesoId("p-4")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build());
        em.clear();

        Optional<ProcesoDistribucion> match = repo.findByProcesoIdAndCreatedBy("p-4", "sistema");
        Optional<ProcesoDistribucion> noOwner = repo.findByProcesoIdAndCreatedBy("p-4", "user-x");
        Optional<ProcesoDistribucion> noExiste = repo.findByProcesoIdAndCreatedBy("inexistente", "sistema");

        assertThat(match).isPresent();
        assertThat(noOwner).isEmpty();
        assertThat(noExiste).isEmpty();
    }

    @Test
    void auditorAware_seteaCreatedByYCreatedAtAutomaticamente() {
        ProcesoDistribucion p = ProcesoDistribucion.builder()
                .procesoId("p-audit")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();

        em.persistAndFlush(p);
        em.clear();

        ProcesoDistribucion recargado = em.find(ProcesoDistribucion.class, "p-audit");
        assertThat(recargado.getCreatedBy()).isEqualTo("sistema");
        assertThat(recargado.getCreatedAt()).isNotNull();
        assertThat(recargado.getUpdatedAt()).isNotNull();
    }
}
