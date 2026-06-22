package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;

@ExtendWith(MockitoExtension.class)
class DistribucionDescargaServiceTest {

    @Mock
    private IGeneradorPdfService pdfService;

    @Mock
    private SimulacionCache simulacionCache;

    @Mock
    private ProcesoDistribucionRepository procesoRepo;

    @Mock
    private DistribucionOrquestadorService gestionDistribucionService;

    @Mock
    private DistribucionListadoService listadoService;

    @TempDir
    Path tempDir;

    private DistribucionDescargaService service;

    @BeforeEach
    void setup() {
        service = new DistribucionDescargaService(
                pdfService, simulacionCache, procesoRepo,
                gestionDistribucionService, listadoService, tempDir.toString());
    }

    @Test
    void obtenerEtiquetas_devuelveFileSystemResourceSiArchivoExiste() throws IOException {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-1")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusDays(1))
                .build();
        when(listadoService.verificarOwnership("p-1")).thenReturn(proceso);

        Path procesoDir = tempDir.resolve("procesos").resolve("p-1");
        Files.createDirectories(procesoDir);
        Files.writeString(procesoDir.resolve("etiquetas.pdf"), "contenido");

        Resource resultado = service.obtenerEtiquetas("p-1");

        assertThat(resultado).isInstanceOf(FileSystemResource.class);
        assertThat(resultado.exists()).isTrue();
    }

    @Test
    void obtenerEtiquetas_lanza404SiArchivosBorradosEnNoNull() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-2")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusDays(10))
                .archivosBorradosEn(LocalDateTime.now().minusDays(1))
                .build();
        when(listadoService.verificarOwnership("p-2")).thenReturn(proceso);

        assertThatThrownBy(() -> service.obtenerEtiquetas("p-2"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtenerEtiquetas_lanza404SiArchivosGeneradosEnNull() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-3")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();
        when(listadoService.verificarOwnership("p-3")).thenReturn(proceso);

        assertThatThrownBy(() -> service.obtenerEtiquetas("p-3"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtenerResumen_devuelveFileSystemResourceSiArchivoExiste() throws IOException {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-4")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusDays(1))
                .build();
        when(listadoService.verificarOwnership("p-4")).thenReturn(proceso);

        Path procesoDir = tempDir.resolve("procesos").resolve("p-4");
        Files.createDirectories(procesoDir);
        Files.writeString(procesoDir.resolve("resumen.pdf"), "contenido");

        Resource resultado = service.obtenerResumen("p-4");

        assertThat(resultado).isInstanceOf(FileSystemResource.class);
        assertThat(resultado.exists()).isTrue();
    }

    @Test
    void obtenerEtiquetasAdmin_noVerificaOwnershipUsaGestionDistribucion() throws IOException {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-admin")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusDays(1))
                .build();
        when(gestionDistribucionService.buscarProceso("p-admin")).thenReturn(proceso);

        Path procesoDir = tempDir.resolve("procesos").resolve("p-admin");
        Files.createDirectories(procesoDir);
        Files.writeString(procesoDir.resolve("etiquetas.pdf"), "admin-contenido");

        Resource resultado = service.obtenerEtiquetasAdmin("p-admin");

        assertThat(resultado).isInstanceOf(FileSystemResource.class);
        assertThat(resultado.exists()).isTrue();
    }

    @Test
    void obtenerEtiquetas_lanza404SiArchivoFisicoNoExiste() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-sin-archivo")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .archivosGeneradosEn(LocalDateTime.now().minusDays(1))
                .build();
        when(listadoService.verificarOwnership("p-sin-archivo")).thenReturn(proceso);
        // No creamos el archivo en disco

        assertThatThrownBy(() -> service.obtenerEtiquetas("p-sin-archivo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void abandonarProceso_desdePendiente_marcaAbandonadoYPersiste() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-ab")
                .estado(EstadoEnum.PENDIENTE.getValue())
                .build();
        when(listadoService.verificarOwnership("p-ab")).thenReturn(proceso);
        when(procesoRepo.save(proceso)).thenReturn(proceso);

        service.abandonarProceso("p-ab");

        assertThat(proceso.getEstado()).isEqualTo(EstadoEnum.ABANDONADO.getValue());
        verify(procesoRepo).save(proceso);
    }

    @Test
    void abandonarProceso_yaAbandonado_esNoOp() {
        // Fire-and-forget desde el front: si la red duplica el request, la
        // segunda llamada no debe romper ni tocar la DB.
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-ab2")
                .estado(EstadoEnum.ABANDONADO.getValue())
                .build();
        when(listadoService.verificarOwnership("p-ab2")).thenReturn(proceso);

        service.abandonarProceso("p-ab2");

        assertThat(proceso.getEstado()).isEqualTo(EstadoEnum.ABANDONADO.getValue());
        verify(procesoRepo, never()).save(proceso);
    }

    @Test
    void abandonarProceso_completado_lanza422() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("p-comp")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .build();
        when(listadoService.verificarOwnership("p-comp")).thenReturn(proceso);

        assertThatThrownBy(() -> service.abandonarProceso("p-comp"))
                .isInstanceOf(UnprocessableEntityException.class);
        assertThat(proceso.getEstado()).isEqualTo(EstadoEnum.COMPLETADO.getValue());
        verify(procesoRepo, never()).save(proceso);
    }
}
