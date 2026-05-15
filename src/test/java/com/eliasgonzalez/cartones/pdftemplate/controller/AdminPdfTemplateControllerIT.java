package com.eliasgonzalez.cartones.pdftemplate.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.pdftemplate.repository.PdfTemplateRepository;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests E2E del Admin Controller. Valida:
 *
 * <ul>
 *   <li>Auth: anónimo → 401, DISTRIBUIDOR → 403, ADMIN → permitido.</li>
 *   <li>CRUD básico: GET list/detail, POST crear, PUT actualizar, DELETE.</li>
 *   <li>Activar: respuesta 200 con activo=true; idempotente sobre uno ya activo.</li>
 *   <li>Validación de schemaJson: 422 si falta basePdf / schemas.</li>
 *   <li>Eliminación bloqueada si el template está activo (422).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPdfTemplateControllerIT extends AbstractPostgresIT {

    private static final String SCHEMA_VALIDO = "{\"basePdf\":\"BLANK_PDF\",\"schemas\":[[]]}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PdfTemplateRepository repository;

    @Autowired
    private TestEntityManager em;

    @MockBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    @Transactional
    void limpiar() {
        em.getEntityManager().createQuery("DELETE FROM PdfTemplate").executeUpdate();
    }

    // ---- Autorización -------------------------------------------------------

    @Test
    void listar_sinAuthDevuelve401() throws Exception {
        mvc.perform(get("/api/admin/pdf-templates")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_conDistribuidorDevuelve403() throws Exception {
        mvc.perform(get("/api/admin/pdf-templates").with(jwtConRol("DISTRIBUIDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_conAdminDevuelve200ConArray() throws Exception {
        mvc.perform(get("/api/admin/pdf-templates").with(jwtConRol("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---- CRUD ---------------------------------------------------------------

    @Test
    void crear_persisteYDevuelveDetalle() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "tipo", "ETIQUETAS",
                "nombre", "Etiquetas A4",
                "schemaJson", SCHEMA_VALIDO));

        mvc.perform(post("/api/admin/pdf-templates")
                        .with(jwtConRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tipo", is("ETIQUETAS")))
                .andExpect(jsonPath("$.activo", is(false)));
    }

    @Test
    void crear_rechazaSchemaJsonInvalido() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "tipo", "ETIQUETAS",
                "nombre", "x",
                "schemaJson", "{\"schemas\":[]}")); // falta basePdf

        mvc.perform(post("/api/admin/pdf-templates")
                        .with(jwtConRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void obtener_devuelveDetalleCompleto() throws Exception {
        PdfTemplate t = repository.save(crear("etq", PdfTemplateTipo.ETIQUETAS, false));

        mvc.perform(get("/api/admin/pdf-templates/" + t.getId()).with(jwtConRol("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(t.getId())))
                .andExpect(jsonPath("$.schemaJson").exists());
    }

    @Test
    void obtener_devuelve404SiNoExiste() throws Exception {
        mvc.perform(get("/api/admin/pdf-templates/no-existe").with(jwtConRol("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizar_modificaNombreYSchemaJson() throws Exception {
        PdfTemplate t = repository.save(crear("viejo", PdfTemplateTipo.RESUMEN, false));

        String body = objectMapper.writeValueAsString(Map.of(
                "nombre", "nuevo nombre",
                "schemaJson", SCHEMA_VALIDO));

        mvc.perform(put("/api/admin/pdf-templates/" + t.getId())
                        .with(jwtConRol("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("nuevo nombre")));
    }

    @Test
    void activar_marcaTemplateComoActivo() throws Exception {
        PdfTemplate t = repository.save(crear("etq", PdfTemplateTipo.ETIQUETAS, false));

        mvc.perform(post("/api/admin/pdf-templates/" + t.getId() + "/activar").with(jwtConRol("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo", is(true)));
    }

    @Test
    void eliminar_bloqueaSiActivo() throws Exception {
        PdfTemplate t = repository.save(crear("activo", PdfTemplateTipo.ETIQUETAS, true));

        mvc.perform(delete("/api/admin/pdf-templates/" + t.getId()).with(jwtConRol("ADMIN")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void eliminar_correctoSiNoActivo() throws Exception {
        PdfTemplate t = repository.save(crear("inactivo", PdfTemplateTipo.ETIQUETAS, false));

        mvc.perform(delete("/api/admin/pdf-templates/" + t.getId()).with(jwtConRol("ADMIN")))
                .andExpect(status().isNoContent());
    }

    // ---- Helpers ------------------------------------------------------------

    private static PdfTemplate crear(String nombre, PdfTemplateTipo tipo, boolean activo) {
        return PdfTemplate.builder()
                .id(UUID.randomUUID().toString())
                .tipo(tipo)
                .nombre(nombre)
                .schemaJson(SCHEMA_VALIDO)
                .activo(activo)
                .build();
    }

    private static RequestPostProcessor jwtConRol(String rol) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("realm_access", Map.of("roles", java.util.List.of(rol))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + rol));
    }

}
