package com.eliasgonzalez.cartones.ruta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests del LimpiezaSesionRutaJob (cron de archivado).
 *
 * No corre Postgres — solo verifica que llama al repo con los argumentos
 * correctos basados en la propiedad app.ruta.retention-days.
 */
@ExtendWith(MockitoExtension.class)
class LimpiezaSesionRutaJobTest {

    @Mock
    private SesionRutaRepository repo;

    private LimpiezaSesionRutaJob job;
    private MeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        job = new LimpiezaSesionRutaJob(repo, registry);
        ReflectionTestUtils.setField(job, "retentionDays", 30);
    }

    @Test
    void limpiar_invocaArchivarConEstadosYCutoffCorrectos() {
        when(repo.archivarPorEstadoYUpdatedAtBefore(anyList(), any(), any(), any()))
                .thenReturn(0);

        job.limpiar();

        ArgumentCaptor<List<String>> estadosCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> archivadaCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo)
                .archivarPorEstadoYUpdatedAtBefore(
                        estadosCaptor.capture(),
                        cutoffCaptor.capture(),
                        archivadaCaptor.capture(),
                        nowCaptor.capture());

        // ACTIVA jamás se archiva — solo COMPLETADA y ABANDONADA.
        assertThat(estadosCaptor.getValue())
                .containsExactlyInAnyOrder(
                        EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor())
                .doesNotContain(EstadoSesionEnum.ACTIVA.getValor())
                .doesNotContain(EstadoSesionEnum.ARCHIVADA.getValor());

        // El cutoff debería estar ~30 días antes del now (con tolerancia para el delta
        // de cómputo).
        LocalDateTime esperadoMinimo = LocalDateTime.now().minusDays(30).minusMinutes(1);
        LocalDateTime esperadoMaximo = LocalDateTime.now().minusDays(30).plusMinutes(1);
        assertThat(cutoffCaptor.getValue()).isBetween(esperadoMinimo, esperadoMaximo);

        // Estado destino: ARCHIVADA.
        assertThat(archivadaCaptor.getValue()).isEqualTo(EstadoSesionEnum.ARCHIVADA.getValor());

        // now debe ser >= cutoff (separados por retentionDays).
        assertThat(nowCaptor.getValue()).isAfter(cutoffCaptor.getValue());
    }

    @Test
    void limpiar_incrementaMetricasRunsYArchived() {
        when(repo.archivarPorEstadoYUpdatedAtBefore(anyList(), any(), any(), any()))
                .thenReturn(7);

        job.limpiar();

        assertThat(registry.counter("cartones.cleanup.sesion_ruta.runs").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("cartones.cleanup.sesion_ruta.archived").count())
                .isEqualTo(7.0);
        assertThat(registry.counter("cartones.cleanup.sesion_ruta.errors").count())
                .isZero();
        assertThat(registry.timer("cartones.cleanup.sesion_ruta.duration").count())
                .isEqualTo(1L);
    }

    @Test
    void limpiar_excepcionDelRepo_incrementaErrorsCounter() {
        when(repo.archivarPorEstadoYUpdatedAtBefore(anyList(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB caída"));

        try {
            job.limpiar();
        } catch (RuntimeException expected) {
            // se relanza por contrato del job
        }

        assertThat(registry.counter("cartones.cleanup.sesion_ruta.errors").count())
                .isEqualTo(1.0);
    }

    @Test
    void limpiar_distintoRetentionDays_actualizaCutoff() {
        ReflectionTestUtils.setField(job, "retentionDays", 7);
        when(repo.archivarPorEstadoYUpdatedAtBefore(anyList(), any(), any(), any()))
                .thenReturn(0);

        job.limpiar();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).archivarPorEstadoYUpdatedAtBefore(anyList(), cutoffCaptor.capture(), any(), any());

        // Con retention=7, cutoff ~7 días atrás.
        LocalDateTime esperado = LocalDateTime.now().minusDays(7);
        assertThat(cutoffCaptor.getValue()).isBetween(esperado.minusMinutes(1), esperado.plusMinutes(1));
    }

    @Test
    void limpiar_archivadasMayorQueCero_logueaInfo() {
        // El test verifica que el flow no rompe con resultado >0; el log se valida
        // por inspección manual o un appender en otro test si fuera crítico.
        when(repo.archivarPorEstadoYUpdatedAtBefore(anyList(), any(), any(), any()))
                .thenReturn(5);

        job.limpiar();

        verify(repo)
                .archivarPorEstadoYUpdatedAtBefore(
                        eq(List.of(EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor())),
                        any(),
                        eq(EstadoSesionEnum.ARCHIVADA.getValor()),
                        any());
    }
}
