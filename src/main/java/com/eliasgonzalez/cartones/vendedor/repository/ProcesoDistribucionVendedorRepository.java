package com.eliasgonzalez.cartones.vendedor.repository;

import com.eliasgonzalez.cartones.vendedor.entity.ProcesoDistribucionVendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcesoDistribucionVendedorRepository extends JpaRepository<ProcesoDistribucionVendedor, Long> {

    // Solo los vendedores con cartones asignados (senete o telebingo) para el PDF
    @Query("SELECT pdv FROM ProcesoDistribucionVendedor pdv " +
           "WHERE (pdv.cantidadSenete > 0 OR pdv.cantidadTelebingo > 0) " +
           "AND pdv.procesoId = :procesoId")
    List<ProcesoDistribucionVendedor> findVendedoresValidos(@Param("procesoId") String procesoId);

    // Todos los vendedores del proceso (para el PDF mapper)
    List<ProcesoDistribucionVendedor> findAllByProcesoId(String procesoId);
}
