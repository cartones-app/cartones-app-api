package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.distribucion.controller.dto.SimulacionRequestDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.repository.ProcesoDistribucionRepository;
import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistribucionOrquestadorService {

    private final DistribucionAlgoritmoService distribucionService;
    private final ProcesoDistribucionRepository procesoDistribucionRepo;
    private final SimulacionCache saveInMemoryTemp;

    @Transactional
    public List<VendedorSimuladoDTO> procesarSimulacion(String procesoId, SimulacionRequestDTO solicitud) {
        ProcesoDistribucion proceso = buscarProceso(procesoId);
        log.info("Proceso encontrado: {}", proceso.getProcesoId());

        ProcesoEstadoService.PendienteToVerificando(procesoId, proceso);
        procesoDistribucionRepo.save(proceso);

        List<VendedorSimuladoDTO> resultado = distribucionService.simularDistribucion(solicitud);

        saveInMemoryTemp.guardar(resultado);
        saveInMemoryTemp.setFechaSorteoSenete(solicitud.getFechaSorteoSenete());
        saveInMemoryTemp.setFechaSorteoTelebingo(solicitud.getFechaSorteoTelebingo());

        return resultado;
    }

    public ProcesoDistribucion buscarProceso(String procesoId) {
        return procesoDistribucionRepo.findById(procesoId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "El proceso con ID " + procesoId + " no existe.", List.of()
            ));
    }
}
