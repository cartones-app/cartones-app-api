package com.eliasgonzalez.cartones.pdf.service;

import com.eliasgonzalez.cartones.pdf.entity.ProcesoDistribucion;
import com.eliasgonzalez.cartones.pdf.enums.EstadoEnum;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;

import java.util.List;

public class ProcesoIdService {

    public static void PendienteToVerificando(String procesoId, ProcesoDistribucion proceso) {
        if (!EstadoEnum.PENDIENTE.getValue().equals(proceso.getEstado()) &&
            !EstadoEnum.VERIFICANDO.getValue().equals(proceso.getEstado())) {
            throw new UnprocessableEntityException(
                "El proceso no está en estado 'pendiente'.",
                List.of("El proceso " + procesoId + " tiene un estado '" + proceso.getEstado() + "'")
            );
        }
        proceso.setEstado(EstadoEnum.VERIFICANDO.getValue());
    }

    public static void VerificandoToCompletado(String procesoId, ProcesoDistribucion proceso) {
        if (!EstadoEnum.VERIFICANDO.getValue().equals(proceso.getEstado())) {
            throw new UnprocessableEntityException(
                "El proceso no está en estado 'verificando'.",
                List.of("El proceso " + procesoId + " tiene un estado " + proceso.getEstado())
            );
        }
        proceso.setEstado(EstadoEnum.COMPLETADO.getValue());
    }
}
