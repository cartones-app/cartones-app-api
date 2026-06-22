package com.eliasgonzalez.cartones.vendedor.repository;

import com.eliasgonzalez.cartones.vendedor.domain.Vendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendedorRepository extends JpaRepository<Vendedor, Long> {

    Optional<Vendedor> findByNombreIgnoreCase(String nombre);
}
