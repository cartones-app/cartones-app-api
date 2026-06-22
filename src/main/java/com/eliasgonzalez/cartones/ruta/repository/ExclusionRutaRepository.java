package com.eliasgonzalez.cartones.ruta.repository;

import com.eliasgonzalez.cartones.ruta.domain.ExclusionRuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExclusionRutaRepository extends JpaRepository<ExclusionRuta, Long> {

    List<ExclusionRuta> findByActivoTrue();

    Optional<ExclusionRuta> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);
}
