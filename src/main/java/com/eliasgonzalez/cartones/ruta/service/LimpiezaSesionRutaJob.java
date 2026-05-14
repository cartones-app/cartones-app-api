package com.eliasgonzalez.cartones.ruta.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de limpieza de SesionRuta.
 *
 * Las sesiones COMPLETADA y ABANDONADA con updatedAt anterior al cutoff
 * se archivan: el BLOB del Excel se vacía, estado pasa a ARCHIVADA y
 *
 * @SQLRestriction las oculta de queries normales. Las ACTIVA nunca se
 *                 tocan.
 *
 *                 Configurable con APP_RUTA_RETENTION_DAYS (default 30).
 *                 Cron diario a las 03:00 del servidor (configurable con
 *                 APP_RUTA_CLEANUP_CRON) para no chocar con uso normal.
 *
 *                 Métricas Micrometer expuestas en /actuator/prometheus:
 *                 - cartones.cleanup.sesion_ruta.runs.total — contador de
 *                 ejecuciones.
 *                 - cartones.cleanup.sesion_ruta.errors.total — contador de
 *                 errores.
 *                 - cartones.cleanup.sesion_ruta.archived.total — contador de
 *                 sesiones archivadas (acumulativo).
 *                 - cartones.cleanup.sesion_ruta.duration — Timer con duración
 *                 de cada ejecución.
 */
@Component
@Slf4j
public class LimpiezaSesionRutaJob {

    private static final List<String> ESTADOS_ELIMINABLES =
            List.of(EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor());

    private final SesionRutaRepository sesionRutaRepo;
    private final Counter runsCounter;
    private final Counter errorsCounter;
    private final Counter archivedCounter;
    private final Timer durationTimer;

    @Value("${app.ruta.retention-days:30}")
    private int retentionDays;

    public LimpiezaSesionRutaJob(SesionRutaRepository sesionRutaRepo, MeterRegistry registry) {
        this.sesionRutaRepo = sesionRutaRepo;
        this.runsCounter = Counter.builder("cartones.cleanup.sesion_ruta.runs")
                .description("Total de ejecuciones del cron de archivado de SesionRuta")
                .register(registry);
        this.errorsCounter = Counter.builder("cartones.cleanup.sesion_ruta.errors")
                .description("Total de errores durante el archivado de SesionRuta")
                .register(registry);
        this.archivedCounter = Counter.builder("cartones.cleanup.sesion_ruta.archived")
                .description("Total acumulado de sesiones archivadas")
                .register(registry);
        this.durationTimer = Timer.builder("cartones.cleanup.sesion_ruta.duration")
                .description("Duración de cada ejecución del archivado")
                .register(registry);
    }

    @Scheduled(cron = "${app.ruta.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void limpiar() {
        runsCounter.increment();
        durationTimer.record(() -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusDays(retentionDays);
            log.info("Archivado SesionRuta: archivando sesiones {} con updatedAt < {}", ESTADOS_ELIMINABLES, cutoff);
            try {
                int archivadas = sesionRutaRepo.archivarPorEstadoYUpdatedAtBefore(
                        ESTADOS_ELIMINABLES, cutoff, EstadoSesionEnum.ARCHIVADA.getValor(), now);
                archivedCounter.increment(archivadas);
                if (archivadas > 0) {
                    log.info("Archivado SesionRuta completado: {} sesión(es) archivada(s).", archivadas);
                } else {
                    log.debug("Archivado SesionRuta sin trabajo: 0 sesiones a archivar.");
                }
            } catch (RuntimeException e) {
                errorsCounter.increment();
                log.error("Archivado SesionRuta falló", e);
                throw e;
            }
        });
    }
}
