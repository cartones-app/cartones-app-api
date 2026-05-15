package com.eliasgonzalez.cartones.distribucion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eliasgonzalez.cartones.common.exception.GoneException;
import com.eliasgonzalez.cartones.distribucion.component.SimulacionCache;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;

/**
 * Tests del service que arma los datos crudos para que el cliente
 * (pdfme) renderice los PDFs.
 *
 * <p>
 * Foco: el fallback 410 Gone cuando la cache in-memory se perdió.
 */
@ExtendWith(MockitoExtension.class)
class DistribucionDatosPdfServiceTest {

    @Mock
    private SimulacionCache simulacionCache;

    @Mock
    private ProcesoDistribucionVendedorRepository procesoVendedorRepo;

    @InjectMocks
    private DistribucionDatosPdfService service;

    @Test
    void obtenerDatos_lanzaGoneSiLaCacheEstaVacia() {
        when(simulacionCache.getVendedorSimuladoDTOs()).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerDatos("p-1"))
                .isInstanceOf(GoneException.class)
                .hasMessageContaining("simular");
    }

    @Test
    void obtenerDatos_lanzaGoneSiLaCacheEsListaVacia() {
        when(simulacionCache.getVendedorSimuladoDTOs()).thenReturn(List.of());

        assertThatThrownBy(() -> service.obtenerDatos("p-1"))
                .isInstanceOf(GoneException.class);
    }

    // El happy path requiere armar manualmente entidades ProcesoDistribucionVendedor
    // con id+pdf+saldo para que DistribucionMapper no tire NPE. Eso ya está
    // cubierto por el test end-to-end del controller (cuando exista) y por el
    // smoke manual; no agrega valor duplicarlo acá. Foco unit: el camino 410 Gone.
}
