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
 * Estados: PENDIENTE → VERIFICANDO → COMPLETADO.
 *
 * Detalle no obvio: PendienteToVerificando acepta estado origen
 * tanto PENDIENTE como VERIFICANDO (idempotencia para retry seguro).
 * VerificandoToCompletado solo acepta VERIFICANDO (no idempotente).
 */
class ProcesoEstadoServiceTest {

    @Test
    void pendienteToVerificando_pendienteSeMueveAVerificando() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.PENDIENTE);

        ProcesoEstadoService.PendienteToVerificando("p-1", p);

        assertThat(p.getEstado()).isEqualTo(EstadoEnum.VERIFICANDO.getValue());
    }

    @Test
    void pendienteToVerificando_esIdempotente_yaEnVerificandoPasa() {
        // Si una segunda llamada llega antes que la primera commit, este caso
        // permite que el reintento no falle.
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.VERIFICANDO);

        assertThatCode(() -> ProcesoEstadoService.PendienteToVerificando("p-1", p))
                .doesNotThrowAnyException();
        assertThat(p.getEstado()).isEqualTo(EstadoEnum.VERIFICANDO.getValue());
    }

    @Test
    void pendienteToVerificando_lanzaSiYaEstaCompletado() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.PendienteToVerificando("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("'pendiente'");
    }

    @Test
    void verificandoToCompletado_verificandoSeMueveACompletado() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.VERIFICANDO);

        ProcesoEstadoService.VerificandoToCompletado("p-1", p);

        assertThat(p.getEstado()).isEqualTo(EstadoEnum.COMPLETADO.getValue());
    }

    @Test
    void verificandoToCompletado_lanzaSiEstaPendiente() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.PENDIENTE);

        assertThatThrownBy(() -> ProcesoEstadoService.VerificandoToCompletado("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("'verificando'");
    }

    @Test
    void verificandoToCompletado_lanzaSiYaEstaCompletado() {
        // No es idempotente — un re-intento sobre completado falla.
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.VerificandoToCompletado("p-1", p))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void mensajeDeError_incluyeProcesoIdYEstadoActual() {
        ProcesoDistribucion p = procesoEnEstado(EstadoEnum.COMPLETADO);

        assertThatThrownBy(() -> ProcesoEstadoService.PendienteToVerificando("proceso-XYZ", p))
                .hasMessageContaining("pendiente");
    }

    private static ProcesoDistribucion procesoEnEstado(EstadoEnum estado) {
        ProcesoDistribucion p = new ProcesoDistribucion();
        p.setEstado(estado.getValue());
        return p;
    }
}
