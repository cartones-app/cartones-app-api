package com.eliasgonzalez.cartones.distribucion.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.exception.GoneException;
import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.controller.dto.DistribucionDatosPdfDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.mapper.DistribucionMapper;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.distribucion.service.dto.ResumenDTO;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Devuelve los datos crudos de un proceso para que el cliente arme los PDFs
 * con pdfme.
 *
 * <p>
 * <b>Fuente de los datos</b>: el {@code SimulacionCache} in-memory que el
 * orquestador llena al simular. La cache se pierde si el container reinicia
 * antes de que el cliente descargue — en ese caso devolvemos
 * {@link GoneException}
 * (HTTP 410) y el cliente debe re-simular. La idempotencia de re-descarga
 * será resuelta en una iteración posterior persistiendo los datos crudos.
 *
 * <p>
 * El ownership se valida fuera de este service (en el controller /
 * orquestador).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistribucionDatosPdfService {

    private final SimulacionCache simulacionCache;
    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;

    @Transactional(readOnly = true)
    public DistribucionDatosPdfDTO obtenerDatos(String procesoId) {
        List<VendedorSimuladoDTO> simulados = simulacionCache.getVendedorSimuladoDTOs();
        if (simulados == null || simulados.isEmpty()) {
            log.warn("Cache vacía para proceso {}; cliente debe re-simular.", procesoId);
            throw new GoneException(
                    "Los datos para regenerar el PDF ya no están disponibles. Volvé a simular.",
                    List.of("El servidor se reinició entre la simulación y la descarga, o nunca se simuló este proceso."));
        }

        // Mismo mapeo que usa GeneradorPdfService para alimentar a los services de
        // OpenPDF.
        Map<Long, ProcesoDistribucionVendedor> registrosMap = procesoVendedorRepo
                .findAllByProcesoId(procesoId)
                .stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(
                        ProcesoDistribucionVendedor::getId,
                        r -> r,
                        (existente, reemplazo) -> existente));

        List<EtiquetaDTO> etiquetas = DistribucionMapper.toEtiquetaDTOs(simulados, registrosMap);
        List<ResumenDTO> resumen = DistribucionMapper.toResumenDTOs(simulados, registrosMap);

        return new DistribucionDatosPdfDTO(
                etiquetas, resumen, simulacionCache.getFechaSorteoSenete(), simulacionCache.getFechaSorteoTelebingo());
    }
}
