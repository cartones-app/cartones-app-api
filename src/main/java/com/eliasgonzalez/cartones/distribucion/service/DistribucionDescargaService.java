package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.service.IGeneradorPdfService;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DistribucionDescargaService {

    private final IGeneradorPdfService pdfService;
    private final SimulacionCache saveInMemoryTemp;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;
    private final DistribucionOrquestadorService gestionDistribucionService;

    @Transactional
    public Resource generarPaqueteZip(String procesoId) {
        ProcesoDistribucion proceso = gestionDistribucionService.buscarProceso(procesoId);

        Resource zip = pdfService.obtenerZipPdfs(
            procesoId,
            proceso,
            saveInMemoryTemp.getVendedorSimuladoDTOs(),
            saveInMemoryTemp.getFechaSorteoSenete(),
            saveInMemoryTemp.getFechaSorteoTelebingo()
        );

        ProcesoEstadoService.VerificandoToCompletado(procesoId, proceso);
        procesoDistribucionRepo.save(proceso);

        return zip;
    }
}
