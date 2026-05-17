package com.eliasgonzalez.cartones.distribucion.configuracion.repository;

import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionArchivosRepository extends JpaRepository<ConfiguracionArchivos, Long> {
}
