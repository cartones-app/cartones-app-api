package com.eliasgonzalez.cartones.distribucion.preferencias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import com.eliasgonzalez.cartones.distribucion.preferencias.repository.PreferenciasDistribuidorRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

/**
 * Tests unitarios del service. Foco en:
 * <ul>
 *   <li>defaults cuando no hay row,</li>
 *   <li>upsert (crea si no existe, actualiza si existe),</li>
 *   <li>username vacío/null rechazado en guardar.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PreferenciasDistribuidorServiceTest {

    @Mock
    private PreferenciasDistribuidorRepository repo;

    @InjectMocks
    private PreferenciasDistribuidorService service;

    @Test
    void obtenerOPorDefectoDevuelveDefaultsCuandoNoHayRow() {
        when(repo.findById("juan")).thenReturn(Optional.empty());

        var out = service.obtenerOPorDefecto("juan");

        assertThat(out.layout()).isEqualTo(LayoutEtiqueta.TRES_POR_HOJA);
        assertThat(out.orden()).isEqualTo(OrdenEtiqueta.SECUENCIAL);
    }

    @Test
    void obtenerOPorDefectoDevuelveValoresPersistidos() {
        var entity = PreferenciasDistribuidor.builder()
                .username("juan")
                .layoutEtiqueta(LayoutEtiqueta.CUATRO_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.INTERCALADO)
                .build();
        when(repo.findById("juan")).thenReturn(Optional.of(entity));

        var out = service.obtenerOPorDefecto("juan");

        assertThat(out.layout()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        assertThat(out.orden()).isEqualTo(OrdenEtiqueta.INTERCALADO);
    }

    @Test
    void obtenerOPorDefectoConUsernameNullEsDefaults() {
        var out = service.obtenerOPorDefecto(null);

        assertThat(out.layout()).isEqualTo(LayoutEtiqueta.TRES_POR_HOJA);
        assertThat(out.orden()).isEqualTo(OrdenEtiqueta.SECUENCIAL);
        verifyNoInteractions(repo);
    }

    @Test
    void guardarCreaRowSiNoExiste() {
        when(repo.findById("juan")).thenReturn(Optional.empty());
        when(repo.save(any(PreferenciasDistribuidor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var saved = service.guardar("juan", LayoutEtiqueta.CUATRO_POR_HOJA, OrdenEtiqueta.INTERCALADO);

        assertThat(saved.getUsername()).isEqualTo("juan");
        assertThat(saved.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        assertThat(saved.getOrdenEtiqueta()).isEqualTo(OrdenEtiqueta.INTERCALADO);
        verify(repo, times(1)).save(any(PreferenciasDistribuidor.class));
    }

    @Test
    void guardarActualizaRowExistente() {
        var existente = PreferenciasDistribuidor.builder()
                .username("juan")
                .layoutEtiqueta(LayoutEtiqueta.TRES_POR_HOJA)
                .ordenEtiqueta(OrdenEtiqueta.SECUENCIAL)
                .build();
        when(repo.findById("juan")).thenReturn(Optional.of(existente));
        when(repo.save(any(PreferenciasDistribuidor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var saved = service.guardar("juan", LayoutEtiqueta.CUATRO_POR_HOJA, OrdenEtiqueta.INTERCALADO);

        assertThat(saved).isSameAs(existente); // misma instancia, mutada
        assertThat(saved.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.CUATRO_POR_HOJA);
        assertThat(saved.getOrdenEtiqueta()).isEqualTo(OrdenEtiqueta.INTERCALADO);
    }

    @Test
    void guardarConValoresNullAplicaDefaults() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        when(repo.save(any(PreferenciasDistribuidor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var saved = service.guardar("juan", null, null);

        assertThat(saved.getLayoutEtiqueta()).isEqualTo(LayoutEtiqueta.TRES_POR_HOJA);
        assertThat(saved.getOrdenEtiqueta()).isEqualTo(OrdenEtiqueta.SECUENCIAL);
    }

    @Test
    void guardarConUsernameBlankLanzaIAE() {
        assertThatThrownBy(() -> service.guardar(" ", LayoutEtiqueta.TRES_POR_HOJA, OrdenEtiqueta.SECUENCIAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardar(null, LayoutEtiqueta.TRES_POR_HOJA, OrdenEtiqueta.SECUENCIAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
