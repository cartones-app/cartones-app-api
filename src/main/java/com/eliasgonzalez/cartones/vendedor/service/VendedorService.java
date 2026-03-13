package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import com.eliasgonzalez.cartones.pdf.interfaces.ProcesoDistribucionRepository;
import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.controller.dto.VendedorResponseDTO;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.service.IVendedorService;
import com.eliasgonzalez.cartones.vendedor.mapper.VendedorMapper;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VendedorService implements IVendedorService {

    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;
    private final ExcelVendedorLectorService excelVendedorLectorService;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;

    @Override
    public List<VendedorResponseDTO> listarVendedoresValidos(String procesoId) {
        List<ProcesoDistribucionVendedor> registros = procesoVendedorRepo.findVendedoresValidos(procesoId);
        log.info("Vendedores válidos obtenidos de BD para proceso {}: {}", procesoId, registros.size());
        return VendedorMapper.toVendedorResponseDTOs(registros);
    }

    @Override
    public CargaVendedoresResponseDTO procesarExcel(MultipartFile file, String procesoIdCreado) {
        return excelVendedorLectorService.leerExcel(file, procesoIdCreado);
    }

    @Override
    public String iniciarProceso() {
        String procesoIdCreado = UUID.randomUUID().toString();
        procesoDistribucionRepo.save(
            ProcesoDistribucion.builder()
                .procesoId(procesoIdCreado)
                .build()
        );
        return procesoIdCreado;
    }
}
