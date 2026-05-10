package com.eliasgonzalez.cartones.distribucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;

@Repository
public interface ProcesoDistribucionRepository extends JpaRepository<ProcesoDistribucion, String> {

    /**
     * Lista todos los procesos creados por el usuario indicado, más recientes primero.
     * Usado por GET /api/distribuciones (cada usuario ve solo lo suyo).
     */
    List<ProcesoDistribucion> findAllByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Busca un proceso por id y creador. Si el usuario no es dueño, retorna empty.
     * Usado para validar ownership antes de la descarga.
     */
    Optional<ProcesoDistribucion> findByProcesoIdAndCreatedBy(String procesoId, String createdBy);

    /**
     * Lista todos los procesos (vista admin), más recientes primero.
     */
    List<ProcesoDistribucion> findAllByOrderByCreatedAtDesc();
}
