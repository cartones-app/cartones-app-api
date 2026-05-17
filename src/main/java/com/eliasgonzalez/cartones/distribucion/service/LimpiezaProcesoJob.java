package com.eliasgonzalez.cartones.distribucion.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;
import com.eliasgonzalez.cartones.distribucion.configuracion.service.ConfiguracionArchivosService;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de limpieza trimestral de archivos en filesystem.
 *
 * Métricas Micrometer:
 * - cartones.cleanup.storage.runs.total — ejecuciones.
 * - cartones.cleanup.storage.deleted.total — procesos con archivos borrados.
 * - cartones.cleanup.storage.errors.total — errores de IO.
 * - cartones.cleanup.storage.duration — Timer.
 */
@Component
@Slf4j
public class LimpiezaProcesoJob {

    private final Path storageDir;
    private final ConfiguracionArchivosService configuracionService;
    private final ProcesoDistribucionRepository procesoRepo;
    private final Counter runsCounter;
    private final Counter deletedCounter;
    private final Counter errorsCounter;
    private final Timer durationTimer;

    public LimpiezaProcesoJob(
            @Value("${app.storage.directory:storage}") String storageDirectory,
            ConfiguracionArchivosService configuracionService,
            ProcesoDistribucionRepository procesoRepo,
            MeterRegistry registry) {
        this.storageDir = Paths.get(storageDirectory);
        this.configuracionService = configuracionService;
        this.procesoRepo = procesoRepo;
        this.runsCounter = Counter.builder("cartones.cleanup.storage.runs")
                .description("Total de ejecuciones del cron de limpieza de storage")
                .register(registry);
        this.deletedCounter = Counter.builder("cartones.cleanup.storage.deleted")
                .description("Total acumulado de procesos con archivos borrados")
                .register(registry);
        this.errorsCounter = Counter.builder("cartones.cleanup.storage.errors")
                .description("Total de errores de IO durante limpieza")
                .register(registry);
        this.durationTimer = Timer.builder("cartones.cleanup.storage.duration")
                .description("Duración de cada ejecución de limpieza")
                .register(registry);
    }

    @Scheduled(cron = "0 0 0 1 1/3 ?")
    public void ejecutarLimpiezaTrimestral() {
        runsCounter.increment();
        durationTimer.record(this::limpiar);
    }

    void limpiar() {
        ConfiguracionArchivos config = configuracionService.obtener();

        if (!config.isEliminacionActiva()) {
            log.info("Eliminación desactivada por configuración. Se omite limpieza de archivos.");
            return;
        }

        LocalDateTime umbral = LocalDateTime.now().minusMonths(config.getRetencionMeses());
        List<ProcesoDistribucion> candidatos = procesoRepo
                .findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(umbral);

        log.info("Limpieza trimestral: {} procesos candidatos a borrado (umbral={})", candidatos.size(), umbral);

        int borrados = 0;
        int errores = 0;

        for (ProcesoDistribucion proceso : candidatos) {
            String procesoId = proceso.getProcesoId();
            Path procesoDir = resolverProcesoDirSeguro(procesoId);
            if (procesoDir == null) {
                errores++;
                continue;
            }

            // Orden DB-first: marcamos el proceso como borrado antes de tocar el FS.
            // Si el save falla, no borramos archivos (estado consistente). Si el save
            // OK y después falla el delete de FS, queda archivo huérfano en disco
            // pero la verdad de negocio (DB) ya dice "no disponible" y el frontend
            // lo respeta. La alternativa (borrar FS primero) crearía la ventana donde
            // DB dice disponible pero el archivo ya no existe — peor UX.
            //
            // Aislamos el save en su propio try/catch para que una falla puntual
            // (ej. OptimisticLockingException por @Version) no aborte el resto del
            // batch trimestral.
            try {
                proceso.setArchivosBorradosEn(LocalDateTime.now());
                procesoRepo.save(proceso);
            } catch (RuntimeException e) {
                errores++;
                log.warn("No se pudo marcar borrado el proceso {}: {}",
                        LogSanitizer.safe(procesoId), e.getMessage());
                continue;
            }
            borrados++;

            boolean errorEnProceso = false;
            errorEnProceso |= !borrarArchivoSilencioso(procesoDir.resolve("etiquetas.pdf"), procesoId);
            errorEnProceso |= !borrarArchivoSilencioso(procesoDir.resolve("resumen.pdf"), procesoId);
            intentarBorrarDirectorioSiVacio(procesoDir, procesoId);

            if (errorEnProceso) {
                errores++;
            }
        }

        deletedCounter.increment(borrados);
        errorsCounter.increment(errores);
        log.info("Limpieza trimestral completada: {} procesos marcados como borrados, {} errores de IO.",
                borrados, errores);
    }

    /**
     * Borra el archivo si existe. Ignora silenciosamente si no existe.
     * Loguea warn si falla por otro motivo.
     *
     * @return true si no hubo error de IO relevante (incluye "no existe").
     */
    private boolean borrarArchivoSilencioso(Path archivo, String procesoId) {
        try {
            Files.deleteIfExists(archivo);
            return true;
        } catch (IOException e) {
            log.warn("No se pudo borrar archivo {} del proceso {}: {}",
                    LogSanitizer.safe(archivo.getFileName()), LogSanitizer.safe(procesoId), e.getMessage());
            log.debug("Detalle del fallo borrando {}:", LogSanitizer.safe(archivo.getFileName()), e);
            return false;
        }
    }

    private void intentarBorrarDirectorioSiVacio(Path dir, String procesoId) {
        try {
            if (Files.exists(dir) && esDirectorioVacio(dir)) {
                Files.delete(dir);
            }
        } catch (IOException e) {
            log.debug("No se pudo borrar directorio vacío del proceso {}: {}",
                    LogSanitizer.safe(procesoId), e.getMessage());
        }
    }

    private boolean esDirectorioVacio(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        }
    }

    /**
     * Resuelve el directorio del proceso validando que esté dentro de
     * {@code storageDir/procesos}. Devuelve null si el path escaparía.
     * Defensa en profundidad — los procesoId vienen de DB acá, no del usuario,
     * pero igual normalizamos por si una row corrupta tiene un ID raro.
     */
    private Path resolverProcesoDirSeguro(String procesoId) {
        Path procesosRoot = storageDir.resolve("procesos").normalize();
        Path candidate = procesosRoot.resolve(procesoId).normalize();
        if (!candidate.startsWith(procesosRoot) || candidate.equals(procesosRoot)) {
            log.warn("procesoId con path inválido descartado de la limpieza: {}",
                    LogSanitizer.safe(procesoId));
            return null;
        }
        return candidate;
    }
}
