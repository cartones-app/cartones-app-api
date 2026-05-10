package com.eliasgonzalez.cartones.distribucion.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LimpiezaProcesoJob {

    private final Path storageDir;

    public LimpiezaProcesoJob(@Value("${app.storage.directory:storage}") String storageDirectory) {
        this.storageDir = Paths.get(storageDirectory);
    }

    @Scheduled(cron = "0 0 0 1 1/3 ?")
    public void ejecutarLimpiezaTrimestral() {
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
            log.error("Fallo al recorrer el directorio de storage '{}'", storageDir, e);
            return;
        }

        log.info(
                "Limpieza trimestral completada en '{}': {} elementos borrados, {} errores.",
                storageDir,
                borrados.get(),
                errores.get());
    }
}
