package com.eliasgonzalez.cartones.distribucion.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DistribucionDescargaService {

    private final IGeneradorPdfService pdfService;
    private final SimulacionCache saveInMemoryTemp;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;
    private final DistribucionOrquestadorService gestionDistribucionService;

    /**
     * Devuelve el ZIP del proceso.
     *
     * <p>
     * Dos caminos:
     * <ol>
     * <li><b>Primer download tras simular</b> (estado=VERIFICANDO):
     * genera los PDFs leyendo del SimulacionCache, los persiste en la fila
     * del proceso y transiciona a COMPLETADO.</li>
     * <li><b>Re-download</b> (estado=COMPLETADO con bytes ya persistidos):
     * arma el ZIP directamente desde los bytes en DB. No depende del
     * SimulacionCache (in-memory) ni regenera nada, así que sobrevive
     * a reinicios del container y a múltiples descargas.</li>
     * </ol>
     *
     * <p>
     * Cualquier otro estado o ausencia de PDFs se delega a la rama 1, donde
     * la validación de estado del PdfService levanta UnprocessableEntityException.
     */
    @Transactional
    public Resource generarPaqueteZip(String procesoId) {
        ProcesoDistribucion proceso = gestionDistribucionService.buscarProceso(procesoId);

        if (esRedownloadValido(proceso)) {
            return zipDesdeBytesPersistidos(proceso);
        }

        Resource zip = pdfService.obtenerZipPdfs(
                procesoId,
                proceso,
                saveInMemoryTemp.getVendedorSimuladoDTOs(),
                saveInMemoryTemp.getFechaSorteoSenete(),
                saveInMemoryTemp.getFechaSorteoTelebingo());

        ProcesoEstadoService.VerificandoToCompletado(procesoId, proceso);
        procesoDistribucionRepo.save(proceso);

        return zip;
    }

    private boolean esRedownloadValido(ProcesoDistribucion proceso) {
        if (!EstadoEnum.COMPLETADO.getValue().equals(proceso.getEstado())) {
            return false;
        }
        byte[] etiq = proceso.getPdfEtiquetas();
        byte[] resu = proceso.getPdfResumen();
        return (etiq != null && etiq.length > 0) || (resu != null && resu.length > 0);
    }

    private Resource zipDesdeBytesPersistidos(ProcesoDistribucion proceso) {
        Map<String, byte[]> archivos = new HashMap<>();
        if (proceso.getPdfEtiquetas() != null && proceso.getPdfEtiquetas().length > 0) {
            archivos.put("Imprimir_etiquetas.pdf", proceso.getPdfEtiquetas());
        }
        if (proceso.getPdfResumen() != null && proceso.getPdfResumen().length > 0) {
            archivos.put("Resumen_entrega.pdf", proceso.getPdfResumen());
        }
        try {
            return ZipEmpaquetadorService.crearZip(archivos);
        } catch (IOException e) {
            throw new FileProcessingException("Error armando ZIP desde bytes persistidos",
                    java.util.List.of(e.getMessage()));
        }
    }
}
