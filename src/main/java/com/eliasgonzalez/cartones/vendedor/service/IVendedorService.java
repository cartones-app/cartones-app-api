package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.controller.dto.VendedorResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IVendedorService {

    List<VendedorResponseDTO> listarVendedoresValidos(String procesoIdRecibido);

    /**
     * Crea el {@code ProcesoDistribucion} y procesa el Excel en la misma
     * transacción. Si el Excel falla validación, rollback completo —
     * no quedan procesos huérfanos en estado PENDIENTE.
     */
    CargaVendedoresResponseDTO cargarDesdeExcel(MultipartFile file);
}
