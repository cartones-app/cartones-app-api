package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;

import java.util.List;

public class ProcesoEstadoService {

    public static void PendienteToSimulado(String procesoId, ProcesoDistribucion proceso) {
        if (!EstadoEnum.PENDIENTE.getValue().equals(proceso.getEstado()) &&
            !EstadoEnum.SIMULADO.getValue().equals(proceso.getEstado())) {
            throw new UnprocessableEntityException(
                "El proceso no está en estado 'pendiente'.",
                List.of("El proceso " + procesoId + " tiene un estado '" + proceso.getEstado() + "'")
            );
        }
        proceso.setEstado(EstadoEnum.SIMULADO.getValue());
    }

    public static void SimuladoToCompletado(String procesoId, ProcesoDistribucion proceso) {
        if (!EstadoEnum.SIMULADO.getValue().equals(proceso.getEstado())) {
            throw new UnprocessableEntityException(
                "El proceso no está en estado 'simulado'.",
                List.of("El proceso " + procesoId + " tiene un estado " + proceso.getEstado())
            );
        }
        proceso.setEstado(EstadoEnum.COMPLETADO.getValue());
    }
}
