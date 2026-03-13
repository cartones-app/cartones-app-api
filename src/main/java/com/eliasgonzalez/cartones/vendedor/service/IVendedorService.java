package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.vendedor.dto.FilasIgnoradasDTO;
import com.eliasgonzalez.cartones.vendedor.dto.VendedorResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IVendedorService {

    List<VendedorResponseDTO> listarVendedoresValidos(String procesoIdRecibido);

    FilasIgnoradasDTO procesarExcel(MultipartFile file, String procesoIdCreado);

    String iniciarProceso();
}
