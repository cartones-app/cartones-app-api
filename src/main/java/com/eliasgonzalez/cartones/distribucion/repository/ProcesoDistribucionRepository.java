package com.eliasgonzalez.cartones.distribucion.repository;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcesoDistribucionRepository extends JpaRepository<ProcesoDistribucion, String> {
}
