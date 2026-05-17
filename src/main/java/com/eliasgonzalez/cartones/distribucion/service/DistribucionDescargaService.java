package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
public class DistribucionDescargaService {

    private final IGeneradorPdfService pdfService;
    private final SimulacionCache saveInMemoryTemp;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;
    private final DistribucionOrquestadorService gestionDistribucionService;
    private final DistribucionListadoService listadoService;
    private final Path storageDir;

    public DistribucionDescargaService(
            IGeneradorPdfService pdfService,
            SimulacionCache saveInMemoryTemp,
            ProcesoDistribucionRepository procesoDistribucionRepo,
            DistribucionOrquestadorService gestionDistribucionService,
            DistribucionListadoService listadoService,
            @Value("${app.storage.directory:storage}") String storageDirectory) {
        this.pdfService = pdfService;
        this.saveInMemoryTemp = saveInMemoryTemp;
        this.procesoDistribucionRepo = procesoDistribucionRepo;
        this.gestionDistribucionService = gestionDistribucionService;
        this.listadoService = listadoService;
        this.storageDir = Paths.get(storageDirectory);
    }

    /**
     * Genera los archivos PDF para un proceso en estado SIMULADO, transiciona a
     * COMPLETADO y persiste los timestamps.
     */
    @Transactional
    public ProcesoDistribucion generarArchivos(String procesoId) {
        ProcesoDistribucion proceso = listadoService.verificarOwnership(procesoId);

        pdfService.generarYPersistirArchivos(
                procesoId,
                proceso,
                saveInMemoryTemp.getVendedorSimuladoDTOs(),
                saveInMemoryTemp.getFechaSorteoSenete(),
                saveInMemoryTemp.getFechaSorteoTelebingo());

        ProcesoEstadoService.SimuladoToCompletado(procesoId, proceso);
        return procesoDistribucionRepo.save(proceso);
    }

    /**
     * Devuelve el archivo de etiquetas para descarga.
     * Verifica ownership y disponibilidad del archivo.
     */
    @Transactional(readOnly = true)
    public Resource obtenerEtiquetas(String procesoId) {
        ProcesoDistribucion proceso = listadoService.verificarOwnership(procesoId);
        validarArchivosDisponibles(proceso, procesoId);
        return resolverArchivo(procesoId, "etiquetas.pdf");
    }

    /**
     * Devuelve el archivo de resumen para descarga.
     * Verifica ownership y disponibilidad del archivo.
     */
    @Transactional(readOnly = true)
    public Resource obtenerResumen(String procesoId) {
        ProcesoDistribucion proceso = listadoService.verificarOwnership(procesoId);
        validarArchivosDisponibles(proceso, procesoId);
        return resolverArchivo(procesoId, "resumen.pdf");
    }

    /**
     * Versión admin: obtiene etiquetas sin verificar ownership.
     */
    @Transactional(readOnly = true)
    public Resource obtenerEtiquetasAdmin(String procesoId) {
        ProcesoDistribucion proceso = gestionDistribucionService.buscarProceso(procesoId);
        validarArchivosDisponibles(proceso, procesoId);
        return resolverArchivo(procesoId, "etiquetas.pdf");
    }

    /**
     * Versión admin: obtiene resumen sin verificar ownership.
     */
    @Transactional(readOnly = true)
    public Resource obtenerResumenAdmin(String procesoId) {
        ProcesoDistribucion proceso = gestionDistribucionService.buscarProceso(procesoId);
        validarArchivosDisponibles(proceso, procesoId);
        return resolverArchivo(procesoId, "resumen.pdf");
    }

    private void validarArchivosDisponibles(ProcesoDistribucion proceso, String procesoId) {
        if (proceso.getArchivosGeneradosEn() == null || proceso.getArchivosBorradosEn() != null) {
            throw new ResourceNotFoundException("Archivos no disponibles para el proceso " + procesoId + ".",
                    List.of());
        }
    }

    /**
     * Resuelve el path absoluto del archivo dentro del directorio del proceso y
     * valida que no escape de {@code storageDir/procesos}. Defensa en
     * profundidad: el ownership check vía DB ya filtra IDs inválidos pero los
     * IDs que no matchean nunca llegan acá; igualmente normalizamos antes de
     * tocar el FS por si el contrato cambia.
     */
    private Resource resolverArchivo(String procesoId, String nombreArchivo) {
        Path procesosRoot = storageDir.resolve("procesos").normalize();
        Path procesoDir = procesosRoot.resolve(procesoId).normalize();
        if (!procesoDir.startsWith(procesosRoot) || procesoDir.equals(procesosRoot)) {
            throw new ResourceNotFoundException(
                    "Archivos no disponibles para el proceso solicitado.", List.of());
        }
        Path archivo = procesoDir.resolve(nombreArchivo);
        if (!Files.exists(archivo)) {
            throw new ResourceNotFoundException(
                    "Archivo físico no encontrado para el proceso solicitado.", List.of());
        }
        return new FileSystemResource(archivo);
    }
}
