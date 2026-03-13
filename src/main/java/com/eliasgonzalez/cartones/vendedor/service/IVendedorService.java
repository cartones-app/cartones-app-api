package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.controller.dto.VendedorResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IVendedorService {

    List<VendedorResponseDTO> listarVendedoresValidos(String procesoIdRecibido);

    CargaVendedoresResponseDTO procesarExcel(MultipartFile file, String procesoIdCreado);

    String iniciarProceso();
}
