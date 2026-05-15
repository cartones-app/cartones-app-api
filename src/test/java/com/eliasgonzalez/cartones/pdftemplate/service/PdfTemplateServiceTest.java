package com.eliasgonzalez.cartones.pdftemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateCreateDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateUpdateDTO;
import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.pdftemplate.repository.PdfTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests de la lógica core del PdfTemplateService:
 * - validación de schemaJson
 * - eliminación bloqueada si el template está activo
 * - activación atómica que desactiva el resto del mismo tipo
 * - idempotencia de activar() sobre uno ya activo
 */
@ExtendWith(MockitoExtension.class)
class PdfTemplateServiceTest {

    private static final String SCHEMA_VALIDO = "{\"basePdf\":\"BLANK_PDF\",\"schemas\":[[]]}";

    @Mock
    private PdfTemplateRepository repository;

    private PdfTemplateService service;

    @BeforeEach
    void setUp() {
        // Usamos un ObjectMapper real (es trivial y la validación lo toca de verdad).
        service = new PdfTemplateService(repository, new ObjectMapper());
    }

    @Test
    void obtenerActivo_lanzaSiNoHayActivo() {
        when(repository.findByTipoAndActivoTrue(PdfTemplateTipo.ETIQUETAS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerActivo(PdfTemplateTipo.ETIQUETAS))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ETIQUETAS");
    }

    @Test
    void obtener_lanzaSiNoExiste() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void crear_persisteConActivoFalsePorDefault() {
        PdfTemplateCreateDTO dto = new PdfTemplateCreateDTO(
                PdfTemplateTipo.RESUMEN, "Resumen v1", SCHEMA_VALIDO);
        when(repository.save(any(PdfTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        PdfTemplate creado = service.crear(dto);

        assertThat(creado.isActivo()).isFalse();
        assertThat(creado.getNombre()).isEqualTo("Resumen v1");
        assertThat(creado.getTipo()).isEqualTo(PdfTemplateTipo.RESUMEN);
    }

    @Test
    void crear_lanzaSiSchemaNoTieneBasePdf() {
        PdfTemplateCreateDTO dto = new PdfTemplateCreateDTO(
                PdfTemplateTipo.RESUMEN, "x", "{\"schemas\":[]}");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("basePdf");
        verify(repository, never()).save(any());
    }

    @Test
    void crear_lanzaSiSchemaNoTieneSchemas() {
        PdfTemplateCreateDTO dto = new PdfTemplateCreateDTO(
                PdfTemplateTipo.RESUMEN, "x", "{\"basePdf\":\"BLANK\"}");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("schemas");
    }

    @Test
    void crear_lanzaSiSchemaNoEsJsonValido() {
        PdfTemplateCreateDTO dto = new PdfTemplateCreateDTO(
                PdfTemplateTipo.RESUMEN, "x", "no soy json");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void crear_lanzaSiSchemaJsonEsArrayEnLugarDeObjeto() {
        PdfTemplateCreateDTO dto = new PdfTemplateCreateDTO(
                PdfTemplateTipo.RESUMEN, "x", "[]");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void actualizar_modificaNombreYSchemaPersistiendo() {
        PdfTemplate existente = template("t-1", PdfTemplateTipo.ETIQUETAS, false);
        when(repository.findById("t-1")).thenReturn(Optional.of(existente));
        when(repository.save(any(PdfTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        PdfTemplate actualizado = service.actualizar(
                "t-1", new PdfTemplateUpdateDTO("nuevo nombre", SCHEMA_VALIDO));

        assertThat(actualizado.getNombre()).isEqualTo("nuevo nombre");
        assertThat(actualizado.getSchemaJson()).isEqualTo(SCHEMA_VALIDO);
    }

    @Test
    void eliminar_bloqueaSiTemplateEstaActivo() {
        PdfTemplate activo = template("t-1", PdfTemplateTipo.ETIQUETAS, true);
        when(repository.findById("t-1")).thenReturn(Optional.of(activo));

        assertThatThrownBy(() -> service.eliminar("t-1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("activo");
        verify(repository, never()).delete(any());
    }

    @Test
    void eliminar_eliminaSiNoEstaActivo() {
        PdfTemplate inactivo = template("t-1", PdfTemplateTipo.ETIQUETAS, false);
        when(repository.findById("t-1")).thenReturn(Optional.of(inactivo));

        service.eliminar("t-1");

        verify(repository).delete(inactivo);
    }

    @Test
    void activar_desactivaLosOtrosDelTipoYMarcaElTargetComoActivo() {
        PdfTemplate target = template("t-1", PdfTemplateTipo.ETIQUETAS, false);
        when(repository.findById("t-1")).thenReturn(Optional.of(target));
        when(repository.desactivarOtrosDelTipo(PdfTemplateTipo.ETIQUETAS, "t-1")).thenReturn(1);
        when(repository.save(any(PdfTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        PdfTemplate activado = service.activar("t-1");

        assertThat(activado.isActivo()).isTrue();
        verify(repository).desactivarOtrosDelTipo(PdfTemplateTipo.ETIQUETAS, "t-1");
        verify(repository).save(target);
    }

    @Test
    void activar_esIdempotenteSiYaEstaActivo() {
        PdfTemplate yaActivo = template("t-1", PdfTemplateTipo.ETIQUETAS, true);
        when(repository.findById("t-1")).thenReturn(Optional.of(yaActivo));

        PdfTemplate resultado = service.activar("t-1");

        assertThat(resultado.isActivo()).isTrue();
        // No tocar el repo si ya está activo — evita escrituras innecesarias.
        verify(repository, never()).desactivarOtrosDelTipo(any(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void activar_lanzaSiNoExiste() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activar("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).desactivarOtrosDelTipo(any(), anyString());
    }

    private static PdfTemplate template(String id, PdfTemplateTipo tipo, boolean activo) {
        return PdfTemplate.builder()
                .id(id)
                .tipo(tipo)
                .nombre("test " + id)
                .schemaJson(SCHEMA_VALIDO)
                .activo(activo)
                .build();
    }
}
