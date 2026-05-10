package com.eliasgonzalez.cartones.ruta.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.ruta.domain.SesionRuta;

@Repository
public interface SesionRutaRepository extends JpaRepository<SesionRuta, Long> {

    Optional<SesionRuta> findBySesionId(String sesionId);

    /**
     * Archiva sesiones (soft delete híbrido):
     *  - Pone archivoExcel = NULL (libera el BLOB pesado, que es lo que llena la BD).
     *  - Cambia estado a ARCHIVADA.
     *  - Marca deletedAt = ahora (excluye de futuras queries vía @SQLRestriction).
     *
     * Solo procesa sesiones que NO estén ya archivadas (la SQLRestriction se
     * aplica al WHERE) y cuyo estado esté en la lista (típicamente COMPLETADA
     * y ABANDONADA — las ACTIVA nunca se archivan automáticamente).
     */
    @Modifying
    @Query("UPDATE SesionRuta s SET s.archivoExcel = NULL, s.estado = :estadoArchivada, s.deletedAt = :now "
            + "WHERE s.estado IN :estados AND s.updatedAt < :cutoff")
    int archivarPorEstadoYUpdatedAtBefore(
            @Param("estados") List<String> estados,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("estadoArchivada") String estadoArchivada,
            @Param("now") LocalDateTime now);
}
