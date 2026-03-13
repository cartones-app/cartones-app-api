package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.excel.interfaces.IExcelService;
import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import com.eliasgonzalez.cartones.pdf.interfaces.ProcesoDistribucionRepository;
import com.eliasgonzalez.cartones.vendedor.dto.FilasIgnoradasDTO;
import com.eliasgonzalez.cartones.vendedor.dto.VendedorResponseDTO;
import com.eliasgonzalez.cartones.vendedor.entity.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.interfaces.IVendedorService;
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
    private final IExcelService excelService;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;

    @Override
    public List<VendedorResponseDTO> listarVendedoresValidos(String procesoId) {
        List<ProcesoDistribucionVendedor> registros = procesoVendedorRepo.findVendedoresValidos(procesoId);
        log.info("Vendedores válidos obtenidos de BD para proceso {}: {}", procesoId, registros.size());
        return VendedorMapper.toVendedorResponseDTOs(registros);
    }

    @Override
    public FilasIgnoradasDTO procesarExcel(MultipartFile file, String procesoIdCreado) {
        return excelService.leerExcel(file, procesoIdCreado);
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
