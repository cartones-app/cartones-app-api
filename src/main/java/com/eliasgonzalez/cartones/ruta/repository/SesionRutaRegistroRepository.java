package com.eliasgonzalez.cartones.ruta.repository;

import com.eliasgonzalez.cartones.ruta.entity.SesionRutaRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesionRutaRegistroRepository extends JpaRepository<SesionRutaRegistro, Long> {

    List<SesionRutaRegistro> findAllBySesionRutaId(Long sesionRutaId);

    // Registros con al menos un campo obligatorio sin completar
    @Query("SELECT r FROM SesionRutaRegistro r " +
           "WHERE r.sesionRuta.id = :sesionRutaId " +
           "AND (r.seneteTotalEnviado IS NULL OR r.telebingoTotalEnviado IS NULL " +
           "OR r.pago1 IS NULL OR r.pago2 IS NULL)")
    List<SesionRutaRegistro> findConCamposIncompletos(@Param("sesionRutaId") Long sesionRutaId);
}
