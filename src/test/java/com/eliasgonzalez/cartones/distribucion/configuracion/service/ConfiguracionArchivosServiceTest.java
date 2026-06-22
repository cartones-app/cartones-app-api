package com.eliasgonzalez.cartones.distribucion.configuracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.distribucion.configuracion.domain.ConfiguracionArchivos;
import com.eliasgonzalez.cartones.distribucion.configuracion.repository.ConfiguracionArchivosRepository;

@ExtendWith(MockitoExtension.class)
class ConfiguracionArchivosServiceTest {

    @Mock
    private ConfiguracionArchivosRepository repo;

    @InjectMocks
    private ConfiguracionArchivosService service;

    @Test
    void obtener_devuelveConfiguracionExistente() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L)
                .retencionMeses(3)
                .eliminacionActiva(true)
                .build();
        when(repo.findById(1L)).thenReturn(Optional.of(config));

        ConfiguracionArchivos resultado = service.obtener();

        assertThat(resultado.getRetencionMeses()).isEqualTo(3);
        assertThat(resultado.isEliminacionActiva()).isTrue();
    }

    @Test
    void obtener_lanzaExcepcionSiNoExisteSingleton() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void actualizar_modificaYGuardaConfiguracion() {
        ConfiguracionArchivos config = ConfiguracionArchivos.builder()
                .id(1L)
                .retencionMeses(3)
                .eliminacionActiva(true)
                .build();
        when(repo.findById(1L)).thenReturn(Optional.of(config));
        when(repo.save(any(ConfiguracionArchivos.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionArchivos resultado = service.actualizar(6, false);

        assertThat(resultado.getRetencionMeses()).isEqualTo(6);
        assertThat(resultado.isEliminacionActiva()).isFalse();
        verify(repo).save(config);
    }

    @Test
    void actualizar_lanzaExcepcionSiNoExisteSingleton() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(3, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
