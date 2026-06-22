package com.eliasgonzalez.cartones.distribucion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;

@Repository
public interface ProcesoDistribucionRepository extends JpaRepository<ProcesoDistribucion, String> {

    Optional<ProcesoDistribucion> findByProcesoIdAndCreatedBy(String procesoId, String createdBy);

    @Query(
            value = "SELECT proceso_id AS procesoId, estado AS estado, "
                    + "created_at AS createdAt, updated_at AS updatedAt, "
                    + "created_by AS createdBy, "
                    + "archivos_generados_en AS archivosGeneradosEn, "
                    + "archivos_borrados_en AS archivosBorradosEn "
                    + "FROM proceso_distribucion "
                    + "WHERE created_by = :createdBy "
                    + "ORDER BY created_at DESC",
            nativeQuery = true)
    List<ProcesoDistribucionResumenView> findResumenByCreatedBy(@Param("createdBy") String createdBy);

    @Query(
            value = "SELECT proceso_id AS procesoId, estado AS estado, "
                    + "created_at AS createdAt, updated_at AS updatedAt, "
                    + "created_by AS createdBy, "
                    + "archivos_generados_en AS archivosGeneradosEn, "
                    + "archivos_borrados_en AS archivosBorradosEn "
                    + "FROM proceso_distribucion "
                    + "ORDER BY created_at DESC",
            nativeQuery = true)
    List<ProcesoDistribucionResumenView> findAllResumenOrderByCreatedAtDesc();

    List<ProcesoDistribucion> findByArchivosGeneradosEnNotNullAndArchivosGeneradosEnBeforeAndArchivosBorradosEnIsNull(
            LocalDateTime umbral);

    /**
     * Procesos en alguno de los estados pasados (típicamente
     * {@code "pendiente"} y {@code "simulado"}) creados antes del umbral.
     * Usado por el job de limpieza para marcar como ABANDONADO los procesos
     * que quedaron en estados intermedios y nunca se completaron ni se
     * descartaron explícitamente desde el front.
     */
    List<ProcesoDistribucion> findByEstadoInAndCreatedAtBefore(
            List<String> estados, LocalDateTime umbral);
}
