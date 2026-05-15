package com.eliasgonzalez.cartones.distribucion.controller.dto;

import java.time.LocalDate;
import java.util.List;

import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.distribucion.service.dto.ResumenDTO;

/**
 * Datos crudos del proceso para que el cliente arme los PDFs con pdfme.
 *
 * <p>
 * Reusa los DTOs internos {@code EtiquetaDTO} / {@code ResumenDTO} que ya
 * alimentaban a {@code EtiquetasPdfService} y {@code ResumenPdfService} —
 * misma forma, ahora servida por HTTP.
 */
public record DistribucionDatosPdfDTO(
                List<EtiquetaDTO> etiquetas,
                List<ResumenDTO> resumen,
                LocalDate fechaSorteoSenete,
                LocalDate fechaSorteoTelebingo) {
}
