package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;
import com.eliasgonzalez.cartones.distribucion.configuracion.service.ConfiguracionArchivosService;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class LimpiezaProcesoJobTest {

    @Mock
    private ConfiguracionArchivosService configuracionService;

    @Mock
    private ProcesoDistribucionRepository procesoRepo;

    @TempDir
    Path tempDir;

    private LimpiezaProcesoJob job;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        job = new LimpiezaProcesoJob(
                tempDir.toString(), configuracionService, procesoRepo, registry);
    }

    @Test
    void limpiar_conEliminacionDesactivada_noTocaNada() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(3).eliminacionActiva(false).build();
        when(configuracionService.obtener()).thenReturn(config);

        job.limpiar();

        verify(procesoRepo, never()).findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(any());
        verify(procesoRepo, never()).save(any());
        assertThat(registry.counter("cartones.cleanup.storage.deleted").count()).isZero();
    }

    @Test
    void limpiar_conEliminacionActivaYSinCandidatos_noHaceNada() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(3).eliminacionActiva(true).build();
        when(configuracionService.obtener()).thenReturn(config);
        when(procesoRepo.findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(any()))
                .thenReturn(List.of());

        job.limpiar();

        verify(procesoRepo, never()).save(any());
        assertThat(registry.counter("cartones.cleanup.storage.deleted").count()).isZero();
    }

    @Test
    void limpiar_borraCandidatosYSetearchivosBorradosEn() throws IOException {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(3).eliminacionActiva(true).build();
        when(configuracionService.obtener()).thenReturn(config);

        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-1")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusMonths(4))
                .build();

        Path procesoDir = tempDir.resolve("procesos").resolve("proc-1");
        Files.createDirectories(procesoDir);
        Files.writeString(procesoDir.resolve("etiquetas.pdf"), "contenido-etiquetas");
        Files.writeString(procesoDir.resolve("resumen.pdf"), "contenido-resumen");

        when(procesoRepo.findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(any()))
                .thenReturn(List.of(proceso));
        when(procesoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.limpiar();

        assertThat(procesoDir.resolve("etiquetas.pdf")).doesNotExist();
        assertThat(procesoDir.resolve("resumen.pdf")).doesNotExist();

        ArgumentCaptor<ProcesoDistribucion> captor = ArgumentCaptor.forClass(ProcesoDistribucion.class);
        verify(procesoRepo).save(captor.capture());
        assertThat(captor.getValue().getArchivosBorradosEn()).isNotNull();
        assertThat(registry.counter("cartones.cleanup.storage.deleted").count()).isEqualTo(1.0);
    }

    @Test
    void limpiar_esIdempotenteSiArchivosNoExisten() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(3).eliminacionActiva(true).build();
        when(configuracionService.obtener()).thenReturn(config);

        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-sin-files")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusMonths(4))
                .build();

        when(procesoRepo.findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(any()))
                .thenReturn(List.of(proceso));
        when(procesoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // No hay archivos en disco — no debe lanzar excepción
        job.limpiar();

        verify(procesoRepo).save(any());
        assertThat(registry.counter("cartones.cleanup.storage.errors").count()).isZero();
    }

    @Test
    void limpiar_usaUmbralBasadoEnRetencionMeses() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(6).eliminacionActiva(true).build();
        when(configuracionService.obtener()).thenReturn(config);
        when(procesoRepo.findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(any()))
                .thenReturn(List.of());

        job.limpiar();

        ArgumentCaptor<LocalDateTime> umbralCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(procesoRepo)
                .findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(
                        umbralCaptor.capture());

        LocalDateTime umbralEsperado = LocalDateTime.now().minusMonths(6);
        assertThat(umbralCaptor.getValue())
                .isBetween(umbralEsperado.minusMinutes(1), umbralEsperado.plusMinutes(1));
    }

    @Test
    void ejecutarLimpiezaTrimestral_incrementaRunsCounter() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L).retencionMeses(3).eliminacionActiva(false).build();
        when(configuracionService.obtener()).thenReturn(config);

        job.ejecutarLimpiezaTrimestral();

        assertThat(registry.counter("cartones.cleanup.storage.runs").count()).isEqualTo(1.0);
        assertThat(registry.timer("cartones.cleanup.storage.duration").count()).isEqualTo(1L);
    }
}
