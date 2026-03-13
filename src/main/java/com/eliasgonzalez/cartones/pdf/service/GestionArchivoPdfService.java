package com.eliasgonzalez.cartones.pdf.service;

import com.eliasgonzalez.cartones.pdf.component.SaveInMemoryTemp;
import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import com.eliasgonzalez.cartones.pdf.interfaces.IPdfService;
import com.eliasgonzalez.cartones.pdf.interfaces.ProcesoDistribucionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GestionArchivoPdfService {

    private final IPdfService pdfService;
    private final SaveInMemoryTemp saveInMemoryTemp;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;
    private final GestionDistribucionService gestionDistribucionService;

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

        ProcesoIdService.VerificandoToCompletado(procesoId, proceso);
        procesoDistribucionRepo.save(proceso);

        return zip;
    }
}
