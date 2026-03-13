package com.eliasgonzalez.cartones.pdf.interfaces;

import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcesoDistribucionRepository extends JpaRepository<ProcesoDistribucion, String> {
}
