package com.eliasgonzalez.cartones.distribucion.preferencias.repository;

import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreferenciasDistribuidorRepository extends JpaRepository<PreferenciasDistribuidor, String> {
}
