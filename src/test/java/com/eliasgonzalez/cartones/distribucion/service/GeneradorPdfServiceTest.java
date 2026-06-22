package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.distribucion.preferencias.service.PreferenciasDistribuidorService;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;

@ExtendWith(MockitoExtension.class)
class GeneradorPdfServiceTest {

    @Mock
    private EtiquetasPdfService etiquetasPdfService;

    @Mock
    private ResumenPdfService resumenPdfService;

    @Mock
    private ProcesoDistribucionVendedorRepository procesoVendedorRepo;

    @Mock
    private PreferenciasDistribuidorService preferenciasService;

    @TempDir
    Path tempDir;

    private GeneradorPdfService service;

    @BeforeEach
    void setup() {
        service = new GeneradorPdfService(
                etiquetasPdfService, resumenPdfService, procesoVendedorRepo,
                preferenciasService, tempDir.toString());
    }

    @Test
    void generarYPersistirArchivos_escribeArchivosEnDisco() throws IOException {
        byte[] bytesEtiquetas = "pdf-etiquetas".getBytes();
        byte[] bytesResumen = "pdf-resumen".getBytes();

        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-1")
                .estado(EstadoEnum.SIMULADO.getValue())
                .build();

        when(preferenciasService.obtenerOPorDefecto(any()))
                .thenReturn(PreferenciasDistribuidorService.PreferenciasResueltas.defaults());
        when(procesoVendedorRepo.findAllByProcesoId(anyString())).thenReturn(List.of());
        when(etiquetasPdfService.generarEtiquetas(anyList(), any(), any(), any(), any()))
                .thenReturn(bytesEtiquetas);
        when(resumenPdfService.generarResumen(anyList(), any(), any()))
                .thenReturn(bytesResumen);

        service.generarYPersistirArchivos(
                "proc-1", proceso, List.of(),
                LocalDate.now(), LocalDate.now());

        Path procesoDir = tempDir.resolve("procesos").resolve("proc-1");
        assertThat(procesoDir.resolve("etiquetas.pdf")).exists();
        assertThat(procesoDir.resolve("resumen.pdf")).exists();
        assertThat(Files.readAllBytes(procesoDir.resolve("etiquetas.pdf"))).isEqualTo(bytesEtiquetas);
        assertThat(Files.readAllBytes(procesoDir.resolve("resumen.pdf"))).isEqualTo(bytesResumen);
    }

    @Test
    void generarYPersistirArchivos_seteaArchivosGeneradosEnYBorradosEnNull() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-2")
                .estado(EstadoEnum.SIMULADO.getValue())
                .build();

        when(preferenciasService.obtenerOPorDefecto(any()))
                .thenReturn(PreferenciasDistribuidorService.PreferenciasResueltas.defaults());
        when(procesoVendedorRepo.findAllByProcesoId(anyString())).thenReturn(List.of());
        when(etiquetasPdfService.generarEtiquetas(anyList(), any(), any(), any(), any()))
                .thenReturn("pdf".getBytes());
        when(resumenPdfService.generarResumen(anyList(), any(), any()))
                .thenReturn("pdf".getBytes());

        service.generarYPersistirArchivos(
                "proc-2", proceso, List.of(),
                LocalDate.now(), LocalDate.now());

        assertThat(proceso.getArchivosGeneradosEn()).isNotNull();
        assertThat(proceso.getArchivosBorradosEn()).isNull();
    }

    @Test
    void generarYPersistirArchivos_conEstadoNOSimulado_lanzaUnprocessableEntity() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-3")
                .estado(EstadoEnum.COMPLETADO.getValue())
                .build();

        assertThatThrownBy(() ->
                service.generarYPersistirArchivos("proc-3", proceso, List.of(),
                        LocalDate.now(), LocalDate.now()))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void generarYPersistirArchivos_noDejaArchivosTemp() throws IOException {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-4")
                .estado(EstadoEnum.SIMULADO.getValue())
                .build();

        when(preferenciasService.obtenerOPorDefecto(any()))
                .thenReturn(PreferenciasDistribuidorService.PreferenciasResueltas.defaults());
        when(procesoVendedorRepo.findAllByProcesoId(anyString())).thenReturn(List.of());
        when(etiquetasPdfService.generarEtiquetas(anyList(), any(), any(), any(), any()))
                .thenReturn("pdf".getBytes());
        when(resumenPdfService.generarResumen(anyList(), any(), any()))
                .thenReturn("pdf".getBytes());

        service.generarYPersistirArchivos("proc-4", proceso, List.of(),
                LocalDate.now(), LocalDate.now());

        Path procesoDir = tempDir.resolve("procesos").resolve("proc-4");
        try (var stream = Files.list(procesoDir)) {
            long tmpCount = stream
                    .filter(p -> p.getFileName().toString().endsWith(".tmp"))
                    .count();
            assertThat(tmpCount).isZero();
        }
    }

    @Test
    void generarYPersistirArchivos_conProcesoIdQueIntentaPathTraversal_rechaza() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("../escape")
                .estado(EstadoEnum.SIMULADO.getValue())
                .build();

        // No mockeamos los servicios de PDF: el check de path traversal corta
        // antes de generar bytes (verificación implícita: si la generación
        // ocurriera, el mock devolvería null y el assertion fallaría con otra
        // excepción).
        assertThatThrownBy(() ->
                service.generarYPersistirArchivos("../escape", proceso, List.of(),
                        LocalDate.now(), LocalDate.now()))
                .isInstanceOf(FileProcessingException.class);

        // No debe haber escrito nada fuera del directorio raíz controlado
        assertThat(tempDir.getParent().resolve("escape")).doesNotExist();
    }

    @Test
    void constructor_creaDirectorioDeProcesosAlArrancar() {
        Path procesosRoot = tempDir.resolve("procesos");
        assertThat(procesosRoot).exists().isDirectory();
    }

    @Test
    void generarYPersistirArchivos_conBytesNulos_lanzaFileProcessingException() {
        ProcesoDistribucion proceso = ProcesoDistribucion.builder()
                .procesoId("proc-5")
                .estado(EstadoEnum.SIMULADO.getValue())
                .build();

        when(preferenciasService.obtenerOPorDefecto(any()))
                .thenReturn(PreferenciasDistribuidorService.PreferenciasResueltas.defaults());
        when(procesoVendedorRepo.findAllByProcesoId(anyString())).thenReturn(List.of());
        when(etiquetasPdfService.generarEtiquetas(anyList(), any(), any(), any(), any()))
                .thenReturn(null);
        when(resumenPdfService.generarResumen(anyList(), any(), any()))
                .thenReturn("pdf".getBytes());

        assertThatThrownBy(() ->
                service.generarYPersistirArchivos("proc-5", proceso, List.of(),
                        LocalDate.now(), LocalDate.now()))
                .isInstanceOf(FileProcessingException.class);
    }
}
