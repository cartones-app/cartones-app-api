package com.eliasgonzalez.cartones.pdftemplate.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.pdftemplate.repository.PdfTemplateRepository;
import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

/**
 * Tests del endpoint público {@code GET /api/pdf-templates/active?tipo=...}.
 *
 * <p>Valida que cualquier usuario autenticado (ADMIN o DISTRIBUIDOR) pueda
 * leer el template activo, y que la respuesta contenga el {@code schemaJson}
 * necesario para que el cliente arme el PDF con pdfme.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PdfTemplateControllerIT extends AbstractPostgresIT {

    private static final String SCHEMA = "{\"basePdf\":\"BLANK_PDF\",\"schemas\":[[]]}";

    @Autowired
    private MockMvc mvc;

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

    @Test
    void active_sinAuthDevuelve401() throws Exception {
        mvc.perform(get("/api/pdf-templates/active").param("tipo", "ETIQUETAS"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void active_conDistribuidorDevuelveTemplate() throws Exception {
        repository.save(template(PdfTemplateTipo.ETIQUETAS, "etq activo", true));

        mvc.perform(get("/api/pdf-templates/active")
                        .param("tipo", "ETIQUETAS")
                        .with(jwtConRol("DISTRIBUIDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo", is("ETIQUETAS")))
                .andExpect(jsonPath("$.nombre", is("etq activo")))
                .andExpect(jsonPath("$.schemaJson").exists());
    }

    @Test
    void active_conAdminTambienFunciona() throws Exception {
        repository.save(template(PdfTemplateTipo.RESUMEN, "res", true));

        mvc.perform(get("/api/pdf-templates/active")
                        .param("tipo", "RESUMEN")
                        .with(jwtConRol("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void active_devuelve404SiNoHayActivo() throws Exception {
        mvc.perform(get("/api/pdf-templates/active")
                        .param("tipo", "ETIQUETAS")
                        .with(jwtConRol("DISTRIBUIDOR")))
                .andExpect(status().isNotFound());
    }

    private static PdfTemplate template(PdfTemplateTipo tipo, String nombre, boolean activo) {
        return PdfTemplate.builder()
                .id(UUID.randomUUID().toString())
                .tipo(tipo)
                .nombre(nombre)
                .schemaJson(SCHEMA)
                .activo(activo)
                .build();
    }

    private static RequestPostProcessor jwtConRol(String rol) {
        return jwt().jwt(j -> j.claim("realm_access", Map.of("roles", java.util.List.of(rol))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + rol));
    }
}
