package com.eliasgonzalez.cartones.distribucion.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;

/**
 * Tests del SimulacionCache.
 *
 * <strong>Estado actual: BUG conocido de aislamiento por procesoId.</strong>
 * SimulacionCache es un @Component singleton de Spring con un único campo
 * mutable {@code vendedorSimuladoDTOs}. No hay namespacing por procesoId, así
 * que dos simulaciones concurrentes (cliente A y cliente B) pisan los datos
 * mutuamente. Cuando A pide su ZIP, recibe los datos de la simulación de B.
 *
 * Estos tests documentan ese comportamiento. Cuando se introduzca aislamiento
 * por procesoId (refactor pendiente para cerrar el agujero del audit) deberán
 * actualizarse:
 *  - Reemplazar el singleton por un Map&lt;procesoId, datos&gt; o por un
 *    @RequestScope con propagación explícita del procesoId.
 *  - El test "guardarPisaElEstadoAnterior" debería invertirse: cada save por
 *    procesoId no afecta a otros procesoIds.
 */
class SimulacionCacheTest {

    @Test
    void guardar_setteaLaListaDeVendedores() {
        SimulacionCache cache = new SimulacionCache();
        VendedorSimuladoDTO v = new VendedorSimuladoDTO(1L, "v1", List.of("01-10"), List.of("11-20"));

        cache.guardar(List.of(v));

        assertThat(cache.getVendedorSimuladoDTOs()).containsExactly(v);
    }

    @Test
    void guardarPisaElEstadoAnterior() {
        // BUG documentado: el segundo guardar pisa los datos del primero, sin
        // ningún mecanismo de aislamiento por procesoId. Cuando se arregle el
        // bug (issue conocido), este test debe invertirse o eliminarse.
        SimulacionCache cache = new SimulacionCache();
        VendedorSimuladoDTO procesoA = new VendedorSimuladoDTO(1L, "A", List.of(), List.of());
        VendedorSimuladoDTO procesoB = new VendedorSimuladoDTO(2L, "B", List.of(), List.of());

        cache.guardar(List.of(procesoA));
        cache.guardar(List.of(procesoB));

        assertThat(cache.getVendedorSimuladoDTOs()).containsExactly(procesoB).doesNotContain(procesoA);
    }

    @Test
    void fechasSorteoSeMantienenIndependientes() {
        // Igual que la lista de vendedores: setters globales, sin aislamiento.
        SimulacionCache cache = new SimulacionCache();

        cache.setFechaSorteoSenete(LocalDate.of(2026, 5, 10));
        cache.setFechaSorteoTelebingo(LocalDate.of(2026, 5, 11));

        assertThat(cache.getFechaSorteoSenete()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(cache.getFechaSorteoTelebingo()).isEqualTo(LocalDate.of(2026, 5, 11));
    }
}
