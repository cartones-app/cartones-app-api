package com.eliasgonzalez.cartones.distribucion.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de limpieza trimestral del storage local.
 *
 * Métricas Micrometer:
 * - cartones.cleanup.storage.runs.total — ejecuciones.
 * - cartones.cleanup.storage.deleted.total — archivos borrados (acumulativo).
 * - cartones.cleanup.storage.errors.total — errores de borrado / walk.
 * - cartones.cleanup.storage.duration — Timer.
 */
@Component
@Slf4j
public class LimpiezaProcesoJob {

    private final Path storageDir;
    private final Counter runsCounter;
    private final Counter deletedCounter;
    private final Counter errorsCounter;
    private final Timer durationTimer;

    public LimpiezaProcesoJob(
            @Value("${app.storage.directory:storage}") String storageDirectory, MeterRegistry registry) {
        this.storageDir = Paths.get(storageDirectory);
        this.runsCounter = Counter.builder("cartones.cleanup.storage.runs")
                .description("Total de ejecuciones del cron de limpieza de storage")
                .register(registry);
        this.deletedCounter = Counter.builder("cartones.cleanup.storage.deleted")
                .description("Total acumulado de archivos/carpetas borradas")
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

    private void limpiar() {
        if (!Files.exists(storageDir)) {
            log.debug("Directorio de storage '{}' no existe, se omite limpieza.", storageDir);
            return;
        }

        AtomicLong borrados = new AtomicLong();
        AtomicLong errores = new AtomicLong();

        try (Stream<Path> s = Files.walk(storageDir)) {
            s.sorted(Comparator.reverseOrder()) // Borra archivos antes que carpetas
                    .forEach(path -> {
                        if (path.equals(storageDir)) return; // No borrar la raíz
                        try {
                            Files.delete(path);
                            borrados.incrementAndGet();
                        } catch (IOException e) {
                            errores.incrementAndGet();
                            log.warn("No se pudo borrar: {}", path);
                            log.debug("Detalle del fallo borrando {}:", path, e);
                        }
                    });
        } catch (IOException e) {
            errorsCounter.increment();
            log.error("Fallo al recorrer el directorio de storage '{}'", storageDir, e);
            return;
        }

        deletedCounter.increment(borrados.get());
        errorsCounter.increment(errores.get());
        log.info(
                "Limpieza trimestral completada en '{}': {} elementos borrados, {} errores.",
                storageDir,
                borrados.get(),
                errores.get());
    }
}
