package com.eliasgonzalez.cartones.distribucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;

@Repository
public interface ProcesoDistribucionRepository extends JpaRepository<ProcesoDistribucion, String> {

    /**
     * Busca un proceso por id y creador. Si el usuario no es dueño, retorna empty.
     * Usado para validar ownership antes de la descarga.
     */
    Optional<ProcesoDistribucion> findByProcesoIdAndCreatedBy(String procesoId, String createdBy);

    /**
     * Lista los procesos del usuario actual SIN cargar los BLOBs.
     * Query nativa con OCTET_LENGTH calcula el tamaño en bytes en el motor
     * (PostgreSQL) sin transferir los bytes al cliente JDBC. Devuelve la
     * projection liviana ProcesoDistribucionResumenView.
     */
    @Query(
            value = "SELECT proceso_id AS procesoId, estado AS estado, "
                    + "created_at AS createdAt, updated_at AS updatedAt, "
                    + "created_by AS createdBy, "
                    + "COALESCE(OCTET_LENGTH(pdf_etiquetas), 0) AS tamanoEtiquetasBytes, "
                    + "COALESCE(OCTET_LENGTH(pdf_resumen), 0) AS tamanoResumenBytes "
                    + "FROM proceso_distribucion "
                    + "WHERE created_by = :createdBy "
                    + "ORDER BY created_at DESC",
            nativeQuery = true)
    List<ProcesoDistribucionResumenView> findResumenByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Vista admin: lista todos los procesos del sistema, más recientes primero,
     * SIN cargar los BLOBs. Misma proyección liviana que findResumenByCreatedBy.
     */
    @Query(
            value = "SELECT proceso_id AS procesoId, estado AS estado, "
                    + "created_at AS createdAt, updated_at AS updatedAt, "
                    + "created_by AS createdBy, "
                    + "COALESCE(OCTET_LENGTH(pdf_etiquetas), 0) AS tamanoEtiquetasBytes, "
                    + "COALESCE(OCTET_LENGTH(pdf_resumen), 0) AS tamanoResumenBytes "
                    + "FROM proceso_distribucion "
                    + "ORDER BY created_at DESC",
            nativeQuery = true)
    List<ProcesoDistribucionResumenView> findAllResumenOrderByCreatedAtDesc();
}
