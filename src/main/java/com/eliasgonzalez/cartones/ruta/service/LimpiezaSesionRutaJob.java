package com.eliasgonzalez.cartones.ruta.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job de limpieza de SesionRuta.
 *
 * Las sesiones COMPLETADA y ABANDONADA con updatedAt anterior al cutoff
 * se eliminan junto con sus registros (FK ON DELETE CASCADE en
 * sesion_ruta_registro). Las ACTIVA nunca se borran — pueden estar en
 * uso por un distribuidor que las dejó pausadas.
 *
 * Configurable con APP_RUTA_RETENTION_DAYS (default 30).
 * Cron diario a las 03:00 del servidor para no chocar con uso normal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LimpiezaSesionRutaJob {

    private static final List<String> ESTADOS_ELIMINABLES =
            List.of(EstadoSesionEnum.COMPLETADA.getValor(), EstadoSesionEnum.ABANDONADA.getValor());

    private final SesionRutaRepository sesionRutaRepo;

    @Value("${app.ruta.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "${app.ruta.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void limpiar() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(retentionDays);
        log.info("Archivado SesionRuta: archivando sesiones {} con updatedAt < {}", ESTADOS_ELIMINABLES, cutoff);
        int archivadas = sesionRutaRepo.archivarPorEstadoYUpdatedAtBefore(
                ESTADOS_ELIMINABLES,
                cutoff,
                com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum.ARCHIVADA.getValor(),
                now);
        if (archivadas > 0) {
            log.info("Archivado SesionRuta completado: {} sesión(es) archivada(s).", archivadas);
        } else {
            log.debug("Archivado SesionRuta sin trabajo: 0 sesiones a archivar.");
        }
    }
}
