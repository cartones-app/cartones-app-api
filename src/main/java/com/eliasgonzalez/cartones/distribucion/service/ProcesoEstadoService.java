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

    /**
     * Marca el proceso como abandonado. Idempotente: si ya está abandonado,
     * no-op. Si está completado lanza — un proceso terminado con éxito no
     * se abandona (el caller debería ignorar el 422 silenciosamente).
     *
     * Retorna {@code true} si efectivamente cambió el estado;
     * {@code false} si era no-op (ya estaba abandonado). Útil para counters
     * en el job de limpieza.
     */
    public static boolean aAbandonado(String procesoId, ProcesoDistribucion proceso) {
        String actual = proceso.getEstado();
        if (EstadoEnum.ABANDONADO.getValue().equals(actual)) {
            return false;
        }
        if (EstadoEnum.COMPLETADO.getValue().equals(actual)) {
            throw new UnprocessableEntityException(
                "El proceso ya fue completado; no se puede abandonar.",
                List.of("El proceso " + procesoId + " tiene un estado " + actual)
            );
        }
        proceso.setEstado(EstadoEnum.ABANDONADO.getValue());
        return true;
    }
}
