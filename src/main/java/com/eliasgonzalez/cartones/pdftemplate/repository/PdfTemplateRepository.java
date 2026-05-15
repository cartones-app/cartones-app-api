package com.eliasgonzalez.cartones.pdftemplate.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;

@Repository
public interface PdfTemplateRepository extends JpaRepository<PdfTemplate, String> {

    Optional<PdfTemplate> findByTipoAndActivoTrue(PdfTemplateTipo tipo);

    List<PdfTemplate> findAllByOrderByCreatedAtDesc();

    /**
     * Desactiva todos los templates de un tipo EXCEPTO el que se indica.
     * Útil para la operación de "activar" donde garantizamos que solo uno
     * quede activo por tipo, dentro de una transacción.
     */
    @Modifying
    @Query("UPDATE PdfTemplate t SET t.activo = false WHERE t.tipo = :tipo AND t.id <> :id AND t.activo = true")
    int desactivarOtrosDelTipo(@Param("tipo") PdfTemplateTipo tipo, @Param("id") String id);
}
