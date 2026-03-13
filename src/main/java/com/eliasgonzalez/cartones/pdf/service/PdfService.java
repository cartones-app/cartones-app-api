package com.eliasgonzalez.cartones.pdf.service;

import com.eliasgonzalez.cartones.pdf.dto.*;
import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import com.eliasgonzalez.cartones.pdf.enums.EstadoEnum;
import com.eliasgonzalez.cartones.pdf.interfaces.IPdfService;
import com.eliasgonzalez.cartones.pdf.mapper.PdfMapper;
import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.vendedor.entity.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import com.eliasgonzalez.cartones.zip.ZipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService implements IPdfService {

    private final PdfEtiquetasService pdfEtiquetasService;
    private final PdfResumenService pdfResumenService;
    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;

    private static final String ETIQUETAS = "etiquetas";
    private static final String RESUMEN = "resumen";

    @Override
    @Transactional
    public Resource obtenerZipPdfs(
            String procesoIdRecibido,
            ProcesoDistribucion proceso,
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo
    ) {
        try {
            Map<String, byte[]> pdfsGenerados = generarPdfs(config, fechaSorteoSenete, fechaSorteoTelebingo, procesoIdRecibido);

            if (!EstadoEnum.VERIFICANDO.getValue().equals(proceso.getEstado())) {
                throw new UnprocessableEntityException(
                    "El estado del proceso no es válido para descarga.",
                    List.of("Estado: " + proceso.getEstado())
                );
            }

            proceso.setPdfEtiquetas(pdfsGenerados.get(ETIQUETAS));
            proceso.setPdfResumen(pdfsGenerados.get(RESUMEN));

            Map<String, byte[]> archivos = new HashMap<>();
            archivos.put("Imprimir_etiquetas.pdf", pdfsGenerados.get(ETIQUETAS));
            archivos.put("Resumen_entrega.pdf", pdfsGenerados.get(RESUMEN));

            return ZipService.crearZip(archivos);

        } catch (IOException e) {
            throw new FileProcessingException("Error generando ZIP", List.of(e.getMessage()));
        } catch (UnprocessableEntityException e) {
            throw e;
        } catch (Exception e) {
            throw new FileProcessingException("Error inesperado en PDF Service", List.of(e.getMessage()));
        }
    }

    /**
     * Genera los PDFs de etiquetas y resumen de manera concurrente usando Virtual Threads.
     */
    public Map<String, byte[]> generarPdfs(
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo,
            String procesoIdRecibido
    ) {
        log.info("Iniciando generación concurrente de PDFs para proceso: {}", procesoIdRecibido);
        long startTime = System.currentTimeMillis();

        // El ID en VendedorSimuladoDTO es el ProcesoDistribucionVendedor.id
        List<ProcesoDistribucionVendedor> registros = procesoVendedorRepo.findAllByProcesoId(procesoIdRecibido);

        Map<Long, ProcesoDistribucionVendedor> registrosMap = registros.stream()
            .filter(r -> r.getId() != null)
            .collect(Collectors.toMap(
                ProcesoDistribucionVendedor::getId,
                r -> r,
                (existente, reemplazo) -> existente
            ));

        List<EtiquetaDTO> etiquetasMapeado = PdfMapper.toEtiquetaDTOs(config, registrosMap);
        List<ResumenDTO> resumenMapeado = PdfMapper.toResumenDTOs(config, registrosMap);

        CompletableFuture<byte[]> futureEtiquetas = CompletableFuture.supplyAsync(() -> {
            log.debug("Generando PDF de etiquetas en thread: {}", Thread.currentThread());
            return pdfEtiquetasService.generarEtiquetas(etiquetasMapeado, fechaSorteoSenete, fechaSorteoTelebingo);
        });

        CompletableFuture<byte[]> futureResumen = CompletableFuture.supplyAsync(() -> {
            log.debug("Generando PDF de resumen en thread: {}", Thread.currentThread());
            return pdfResumenService.generarResumen(resumenMapeado, fechaSorteoSenete, fechaSorteoTelebingo);
        });

        byte[] etiquetas;
        byte[] resumen;
        try {
            etiquetas = futureEtiquetas.join();
            resumen = futureResumen.join();
        } catch (Exception e) {
            log.error("Error durante la generación concurrente de PDFs", e);
            throw new FileProcessingException(
                "Error generando PDFs de manera concurrente: " + e.getMessage(),
                List.of(e.getMessage())
            );
        }

        if (etiquetas == null) {
            throw new FileProcessingException("Error: El PDF de etiquetas no pudo ser generado.", List.of());
        }
        if (resumen == null) {
            throw new FileProcessingException("Error: El PDF de resumen no pudo ser generado.", List.of());
        }

        long endTime = System.currentTimeMillis();
        log.info("PDFs generados exitosamente en {}ms (concurrente)", endTime - startTime);

        Map<String, byte[]> resultado = new HashMap<>();
        resultado.put(ETIQUETAS, etiquetas);
        resultado.put(RESUMEN, resumen);
        return resultado;
    }
}
