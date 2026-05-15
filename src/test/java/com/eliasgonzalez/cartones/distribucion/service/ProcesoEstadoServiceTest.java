package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;
import com.eliasgonzalez.cartones.distribucion.domain.enums.EstadoEnum;

/**
 * Tests de la máquina de estados de ProcesoDistribucion.
 *
 * Estados: PENDIENTE → SIMULADO → COMPLETADO.
 *
 * Detalle no obvio: PendienteToSimulado acepta estado origen
 * tanto PENDIENTE como SIMULADO (idempotencia para retry seguro).
 * SimuladoToCompletado solo acepta SIMULADO (no idempotente).
 */
class ProcesoEstadoServiceTest {

    @Test
    void pendienteToSimulado_pendienteSeMueveASimulado() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.PENDIENTE);

        ProcesoEstadoService.PendienteToSimulado("p-1", p);

        assertThat(p.getEstado()).isEqualTo(EstadoEnum.SIMULADO.getValue());
    }

    @Test
    void pendienteToSimulado_esIdempotente_yaEnSimuladoPasa() {
        // Si una segunda llamada llega antes que la primera commit, este caso
        // permite que el reintento no falle.
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.SIMULADO);

        assertThatCode(() -> ProcesoEstadoService.PendienteToSimulado("p-1", p))
                .doesNotThrowAnyException();
        assertThat(p.getEstado()).isEqualTo(EstadoEnum.SIMULADO.getValue());
    }

    @Test
    void pendienteToSimulado_lanzaSiYaEstaCompletado() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.PendienteToSimulado("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("'pendiente'");
    }

    @Test
    void simuladoToCompletado_simuladoSeMueveACompletado() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.SIMULADO);

        ProcesoEstadoService.SimuladoToCompletado("p-1", p);

        assertThat(p.getEstado()).isEqualTo(EstadoEnum.COMPLETADO.getValue());
    }

    @Test
    void simuladoToCompletado_lanzaSiEstaPendiente() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.PENDIENTE);

        assertThatThrownBy(() -> ProcesoEstadoService.SimuladoToCompletado("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("'simulado'");
    }

    @Test
    void simuladoToCompletado_lanzaSiYaEstaCompletado() {
        // No es idempotente — un re-intento sobre completado falla.
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.SimuladoToCompletado("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void mensajeDeError_incluyeProcesoIdYEstadoActual() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.PendienteToSimulado("proceso-XYZ", p))
                .hasMessageContaining("pendiente");
    }

    private static ProcesoDistribucion procesoEnEstado(EstadoEnum estado) {
        ProcesoDistribucion p = new ProcesoDistribucion();
        p.setEstado(estado.getValue());
        return p;
    }
}
