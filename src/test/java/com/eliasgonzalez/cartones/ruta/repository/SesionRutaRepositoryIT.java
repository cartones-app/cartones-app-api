package com.eliasgonzalez.cartones.ruta.repository;

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

import com.eliasgonzalez.cartones.ruta.domain.SesionRuta;
import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

/**
 * Tests del SesionRutaRepository con Postgres real (Testcontainers).
 *
 * Foco principal: el comportamiento del soft delete híbrido del Sprint A2.4.
 *  - @SQLRestriction("deleted_at IS NULL") oculta sesiones archivadas en queries normales.
 *  - archivarPorEstadoYUpdatedAtBefore (UPDATE batch) archiva COMPLETADA/ABANDONADA viejas.
 *  - Los registros hijos en sesion_ruta_registro tienen ON DELETE CASCADE en V1.
 *
 * Verifica también la ausencia de regresión en findBySesionId.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.test.database.replace=NONE")
class SesionRutaRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private SesionRutaRepository repo;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void limpiar() {
        em.getEntityManager()
                .createNativeQuery("DELETE FROM sesion_ruta_registro")
                .executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM sesion_ruta").executeUpdate();
    }

    @Test
    void findBySesionId_devuelveSesionExistente() {
        SesionRuta s = persistirSesion("s-1", EstadoSesionEnum.ACTIVA, LocalDateTime.now());

        Optional<SesionRuta> found = repo.findBySesionId("s-1");

        assertThat(found).isPresent();
        assertThat(found.get().getSesionId()).isEqualTo("s-1");
    }

    @Test
    void findBySesionId_noVeSesionesArchivadas() {
        // @SQLRestriction("deleted_at IS NULL") oculta archivadas.
        SesionRuta s = persistirSesion("archivada-1", EstadoSesionEnum.ARCHIVADA, LocalDateTime.now());
        s.setDeletedAt(LocalDateTime.now());
        em.persistAndFlush(s);
        em.clear();

        Optional<SesionRuta> found = repo.findBySesionId("archivada-1");

        assertThat(found).isEmpty();
    }

    @Test
    void archivar_setteaArchivoExcelANullYEstadoARCHIVADA() {
        LocalDateTime hace40dias = LocalDateTime.now().minusDays(40);
        SesionRuta vieja = persistirSesion("vieja-1", EstadoSesionEnum.COMPLETADA, hace40dias);
        em.clear();

        int archivadas = repo.archivarPorEstadoYUpdatedAtBefore(
                List.of(EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor()),
                LocalDateTime.now().minusDays(30),
                EstadoSesionEnum.ARCHIVADA.getValor(),
                LocalDateTime.now());

        assertThat(archivadas).isEqualTo(1);
        // Verificamos directamente con query nativa porque @SQLRestriction
        // ocultaría la fila a la entidad.
        Object[] row = (Object[]) em.getEntityManager()
                .createNativeQuery(
                        "SELECT estado, archivo_excel, deleted_at FROM sesion_ruta WHERE sesion_id = 'vieja-1'")
                .getSingleResult();
        assertThat(row[0]).isEqualTo("ARCHIVADA");
        assertThat(row[1]).isNull();
        assertThat(row[2]).isNotNull();
    }

    @Test
    void archivar_noTocaSesionesActivas() {
        LocalDateTime hace40dias = LocalDateTime.now().minusDays(40);
        persistirSesion("activa-vieja", EstadoSesionEnum.ACTIVA, hace40dias);

        int archivadas = repo.archivarPorEstadoYUpdatedAtBefore(
                List.of(EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor()),
                LocalDateTime.now().minusDays(30),
                EstadoSesionEnum.ARCHIVADA.getValor(),
                LocalDateTime.now());

        assertThat(archivadas).isZero();
        Optional<SesionRuta> sigueViva = repo.findBySesionId("activa-vieja");
        assertThat(sigueViva).isPresent();
        assertThat(sigueViva.get().getEstado()).isEqualTo(EstadoSesionEnum.ACTIVA.getValor());
    }

    @Test
    void archivar_noTocaSesionesRecientes() {
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        persistirSesion("reciente", EstadoSesionEnum.COMPLETADA, ayer);

        int archivadas = repo.archivarPorEstadoYUpdatedAtBefore(
                List.of(EstadoSesionEnum.COMPLETADA.getValor()),
                LocalDateTime.now().minusDays(30),
                EstadoSesionEnum.ARCHIVADA.getValor(),
                LocalDateTime.now());

        assertThat(archivadas).isZero();
        assertThat(repo.findBySesionId("reciente")).isPresent();
    }

    @Test
    void archivar_solamenteEstadosIncluidos() {
        LocalDateTime hace40dias = LocalDateTime.now().minusDays(40);
        persistirSesion("completa", EstadoSesionEnum.COMPLETADA, hace40dias);
        persistirSesion("abandonada", EstadoSesionEnum.ABANDONADA, hace40dias);

        int archivadas = repo.archivarPorEstadoYUpdatedAtBefore(
                List.of(EstadoSesionEnum.COMPLETADA.getValor()), // solo COMPLETADA
                LocalDateTime.now().minusDays(30),
                EstadoSesionEnum.ARCHIVADA.getValor(),
                LocalDateTime.now());

        assertThat(archivadas).isEqualTo(1);
        // ABANDONADA no se archivó: sigue visible.
        assertThat(repo.findBySesionId("abandonada")).isPresent();
    }

    private SesionRuta persistirSesion(String sesionId, EstadoSesionEnum estado, LocalDateTime updatedAt) {
        SesionRuta s = SesionRuta.builder()
                .sesionId(sesionId)
                .fechaFiltro("2026-05-10")
                .estado(estado.getValor())
                .archivoExcel(new byte[] {1, 2, 3})
                .build();
        em.persist(s);
        em.flush();
        // updatedAt es @LastModifiedDate (auto). Para tests donde necesitamos un
        // updatedAt específico, lo seteamos via query nativa que sí permite
        // sobrescribirlo (Hibernate no lo toca en native UPDATE).
        em.getEntityManager()
                .createNativeQuery("UPDATE sesion_ruta SET updated_at = :ts WHERE sesion_id = :sid")
                .setParameter("ts", updatedAt)
                .setParameter("sid", sesionId)
                .executeUpdate();
        em.clear();
        return s;
    }
}
