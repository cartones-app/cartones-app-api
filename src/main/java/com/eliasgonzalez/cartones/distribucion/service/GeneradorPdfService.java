package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.preferencias.service.PreferenciasDistribuidorService;
import com.eliasgonzalez.cartones.distribucion.preferencias.service.PreferenciasDistribuidorService.PreferenciasResueltas;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.distribucion.service.dto.ResumenDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.distribucion.mapper.DistribucionMapper;
import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeneradorPdfService implements IGeneradorPdfService {

    private final EtiquetasPdfService pdfEtiquetasService;
    private final ResumenPdfService pdfResumenService;
    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;
    private final PreferenciasDistribuidorService preferenciasService;
    private final Path storageDir;

    private static final String ETIQUETAS = "etiquetas";
    private static final String RESUMEN = "resumen";

    /**
     * Crea el directorio raíz de procesos en el constructor. Si el volumen no
     * está montado al arrancar, fallamos rápido con `IllegalStateException`
     * antes de aceptar tráfico — preferible a fallar en la primera generación
     * de archivos con una excepción menos clara.
     */
    public GeneradorPdfService(
            EtiquetasPdfService pdfEtiquetasService,
            ResumenPdfService pdfResumenService,
            ProcesoDistribucionVendedorRepository procesoVendedorRepo,
            PreferenciasDistribuidorService preferenciasService,
            @Value("${app.storage.directory:storage}") String storageDirectory) {
        this.pdfEtiquetasService = pdfEtiquetasService;
        this.pdfResumenService = pdfResumenService;
        this.procesoVendedorRepo = procesoVendedorRepo;
        this.preferenciasService = preferenciasService;
        this.storageDir = Paths.get(storageDirectory);

        Path procesosRoot = storageDir.resolve("procesos");
        try {
            Files.createDirectories(procesosRoot);
            log.info("Directorio de storage inicializado en {}", LogSanitizer.safe(procesosRoot));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo inicializar el directorio de storage para archivos generados", e);
        }
    }

    /**
     * No marcamos @Transactional acá: el caller (DistribucionDescargaService.generarArchivos)
     * sí lo está y los setters de timestamps quedan dentro de su transacción. Si crashea
     * el move atómico del segundo archivo se compensa borrando el primero antes de
     * propagar la excepción — no se puede confiar en el rollback de la transacción para
     * limpiar el FS.
     */
    @Override
    public void generarYPersistirArchivos(
            String procesoId,
            ProcesoDistribucion proceso,
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo
    ) {
        if (!EstadoEnum.SIMULADO.getValue().equals(proceso.getEstado())) {
            throw new UnprocessableEntityException(
                    "El estado del proceso no es válido para generar archivos.",
                    List.of("Estado: " + proceso.getEstado()));
        }

        // Validamos el path ANTES de generar bytes para no gastar CPU si el
        // procesoId es inválido (paranoia: el ownership check ya filtró IDs
        // raros, pero el check es barato y defensivo).
        Path procesoDir = resolverProcesoDirSeguro(procesoId);

        PreferenciasResueltas prefs = preferenciasService.obtenerOPorDefecto(proceso.getCreatedBy());

        Map<String, byte[]> pdfs = generarPdfs(config, fechaSorteoSenete, fechaSorteoTelebingo, procesoId, prefs);
        escribirConAtomicMove(procesoDir, "etiquetas.pdf", pdfs.get(ETIQUETAS), procesoId);
        try {
            escribirConAtomicMove(procesoDir, "resumen.pdf", pdfs.get(RESUMEN), procesoId);
        } catch (FileProcessingException e) {
            // Si el segundo write falla, el rollback de DB no toca el FS — limpiamos
            // el primer archivo para no dejar huérfanos imposibles de detectar
            // (el job no los ve sin archivosGeneradosEn seteado).
            limpiarArchivoTrasFalloParcial(procesoDir.resolve("etiquetas.pdf"));
            throw e;
        }

        proceso.setArchivosGeneradosEn(LocalDateTime.now());
        proceso.setArchivosBorradosEn(null);
    }

    /**
     * Construye el directorio del proceso validando que no escape de
     * {@code storageDir/procesos}. Protege ante {@code procesoId} con
     * {@code ../} aunque el ownership check ya filtraría en DB; defensa en
     * profundidad antes de tocar el FS.
     */
    private Path resolverProcesoDirSeguro(String procesoId) {
        Path procesosRoot = storageDir.resolve("procesos").normalize();
        Path candidate = procesosRoot.resolve(procesoId).normalize();
        if (!candidate.startsWith(procesosRoot) || candidate.equals(procesosRoot)) {
            throw new FileProcessingException(
                    "procesoId inválido para construir path de storage.", List.of());
        }
        return candidate;
    }

    private void limpiarArchivoTrasFalloParcial(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException cleanupEx) {
            log.warn("Cleanup parcial: no se pudo borrar {} tras fallo de write posterior: {}",
                    LogSanitizer.safe(archivo.getFileName()), cleanupEx.getMessage());
        }
    }

    /**
     * Escribe bytes a un archivo .tmp en el mismo directorio y luego hace
     * ATOMIC_MOVE al nombre final. El directorio tmp y destino son el mismo
     * filesystem (mismo directorio), garantizando atomicidad del rename.
     */
    private void escribirConAtomicMove(Path procesoDir, String nombreFinal, byte[] contenido, String procesoId) {
        Path destino = procesoDir.resolve(nombreFinal);
        Path tmp = procesoDir.resolve(nombreFinal + ".tmp");
        try {
            Files.createDirectories(procesoDir);
            Files.write(tmp, contenido);
            Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Archivo escrito atómicamente: {}", LogSanitizer.safe(destino));
        } catch (IOException e) {
            // Limpiar .tmp si quedó por fallo entre write y move. El job no
            // los detecta (solo busca etiquetas.pdf/resumen.pdf), así que sin
            // este cleanup quedarían huérfanos indefinidamente.
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanupEx) {
                log.warn("No se pudo borrar .tmp huérfano tras fallo de write: {}", cleanupEx.getMessage());
            }
            throw new FileProcessingException(
                    "Error escribiendo archivo " + nombreFinal + " para proceso " + LogSanitizer.safe(procesoId),
                    List.of(e.getMessage()));
        }
    }

    /** Helper de generación en memoria. Package-private para uso interno y tests
     *  de la misma capa; el contrato público pasa por {@link IGeneradorPdfService}. */
    Map<String, byte[]> generarPdfs(
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo,
            String procesoId
    ) {
        return generarPdfs(config, fechaSorteoSenete, fechaSorteoTelebingo, procesoId,
                PreferenciasResueltas.defaults());
    }

    Map<String, byte[]> generarPdfs(
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo,
            String procesoId,
            PreferenciasResueltas prefs
    ) {
        log.info("Iniciando generación concurrente de PDFs para proceso: {}", LogSanitizer.safe(procesoId));
        long startTime = System.currentTimeMillis();

        List<ProcesoDistribucionVendedor> registros = procesoVendedorRepo.findAllByProcesoId(procesoId);

        Map<Long, ProcesoDistribucionVendedor> registrosMap = registros.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(
                        ProcesoDistribucionVendedor::getId,
                        r -> r,
                        (existente, reemplazo) -> existente));

        List<EtiquetaDTO> etiquetasMapeado = DistribucionMapper.toEtiquetaDTOs(config, registrosMap);
        List<ResumenDTO> resumenMapeado = DistribucionMapper.toResumenDTOs(config, registrosMap);

        CompletableFuture<byte[]> futureEtiquetas = CompletableFuture.supplyAsync(() -> {
            log.debug("Generando PDF de etiquetas en thread: {} (layout={}, orden={})",
                    Thread.currentThread(), prefs.layout(), prefs.orden());
            return pdfEtiquetasService.generarEtiquetas(
                    etiquetasMapeado, fechaSorteoSenete, fechaSorteoTelebingo,
                    prefs.layout(), prefs.orden());
        });

        CompletableFuture<byte[]> futureResumen = CompletableFuture.supplyAsync(() -> {
            log.debug("Generando PDF de resumen en thread: {}", Thread.currentThread());
            return pdfResumenService.generarResumen(resumenMapeado, fechaSorteoSenete, fechaSorteoTelebingo);
        });

        byte[] etiquetas;
        byte[] resumen;
        try {
            etiquetas = futureEtiquetas.join();
            resumen = futureResumen.join();
        } catch (Exception e) {
            log.error("Error durante la generación concurrente de PDFs", e);
            throw new FileProcessingException(
                    "Error generando PDFs de manera concurrente: " + e.getMessage(),
                    List.of(e.getMessage()));
        }

        if (etiquetas == null) {
            throw new FileProcessingException("Error: El PDF de etiquetas no pudo ser generado.", List.of());
        }
        if (resumen == null) {
            throw new FileProcessingException("Error: El PDF de resumen no pudo ser generado.", List.of());
        }

        long endTime = System.currentTimeMillis();
        log.info("PDFs generados exitosamente en {}ms (concurrente)", endTime - startTime);

        return Map.of(ETIQUETAS, etiquetas, RESUMEN, resumen);
    }
}
