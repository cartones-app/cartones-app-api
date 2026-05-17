package com.eliasgonzalez.cartones.distribucion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
        em.getEntityManager().createQuery("DELETE FROM ProcesoDistribucion").executeUpdate();
    }

    @Test
    void findResumenByCreatedBy_devuelveTimestampsDeArchivos() {
        LocalDateTime generadoEn = LocalDateTime.now().minusDays(1).withNano(0);

        ProcesoDistribucion p = ProcesoDistribucion.builder()
                .procesoId("p-1")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(generadoEn)
                .build();
        em.persistAndFlush(p);
        em.clear();

        List<ProcesoDistribucionResumenView> resultado = repo.findResumenByCreatedBy("sistema");

        assertThat(resultado).hasSize(1).first().satisfies(v -> {
            assertThat(v.getProcesoId()).isEqualTo("p-1");
            assertThat(v.getArchivosGeneradosEn()).isNotNull();
            assertThat(v.getArchivosBorradosEn()).isNull();
            assertThat(v.getCreatedBy()).isEqualTo("sistema");
            assertThat(v.getEstado()).isEqualTo("completado");
        });
    }

    @Test
    void findResumenByCreatedBy_timestampsNullCuandoSinArchivos() {
        ProcesoDistribucion p = ProcesoDistribucion.builder()
                .procesoId("p-2")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();
        em.persistAndFlush(p);
        em.clear();

        List<ProcesoDistribucionResumenView> resultado = repo.findResumenByCreatedBy("sistema");

        assertThat(resultado).hasSize(1).first().satisfies(v -> {
            assertThat(v.getArchivosGeneradosEn()).isNull();
            assertThat(v.getArchivosBorradosEn()).isNull();
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
        Thread.sleep(15);
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

    @Test
    void findCandidatosLimpieza_devuelveSoloLosQueCorresponden() {
        LocalDateTime hace4Meses = LocalDateTime.now().minusMonths(4);
        LocalDateTime hace1Mes = LocalDateTime.now().minusMonths(1);
        LocalDateTime umbral = LocalDateTime.now().minusMonths(3);

        // Candidato: generado hace 4 meses, sin borrar
        ProcesoDistribucion candidato = ProcesoDistribucion.builder()
                .procesoId("p-candidato")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(hace4Meses)
                .build();
        em.persistAndFlush(candidato);

        // Reciente: generado hace 1 mes, sin borrar — NO debe aparecer
        ProcesoDistribucion reciente = ProcesoDistribucion.builder()
                .procesoId("p-reciente")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(hace1Mes)
                .build();
        em.persistAndFlush(reciente);

        // Ya borrado: generado hace 4 meses pero archivosBorradosEn != null — NO
        ProcesoDistribucion yaBorrado = ProcesoDistribucion.builder()
                .procesoId("p-ya-borrado")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(hace4Meses)
                .archivosBorradosEn(LocalDateTime.now().minusDays(1))
                .build();
        em.persistAndFlush(yaBorrado);

        // Sin archivos: nunca generó — NO
        ProcesoDistribucion sinArchivos = ProcesoDistribucion.builder()
                .procesoId("p-sin-archivos")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();
        em.persistAndFlush(sinArchivos);

        em.clear();

        List<ProcesoDistribucion> resultado = repo
                .findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(umbral);

        assertThat(resultado)
                .extracting(ProcesoDistribucion::getProcesoId)
                .containsExactly("p-candidato");
    }
}
