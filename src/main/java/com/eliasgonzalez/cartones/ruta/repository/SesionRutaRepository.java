package com.eliasgonzalez.cartones.ruta.repository;

import com.eliasgonzalez.cartones.ruta.entity.SesionRuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SesionRutaRepository extends JpaRepository<SesionRuta, Long> {

    Optional<SesionRuta> findBySesionId(String sesionId);
}
