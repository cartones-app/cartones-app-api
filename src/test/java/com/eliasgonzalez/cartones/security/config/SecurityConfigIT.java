package com.eliasgonzalez.cartones.security.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.eliasgonzalez.cartones.support.AbstractPostgresIT;

/**
 * Tests de integración para SecurityConfig.
 *
 * Verifica las reglas de autorización por path:
 * - permitAll: /swagger-ui/**, /v3/api-docs/**, /actuator/health
 * - hasRole("ADMIN"): /api/admin/**, /actuator/** (no health)
 * - hasAnyRole("ADMIN", "DISTRIBUIDOR"): /api/**
 * - denyAll: cualquier otra ruta
 *
 * No carga el flujo Keycloak real — JwtDecoder mockeado y los authorities se
 * inyectan vía postprocessor jwt() de Spring Security Test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    // Realm roles bypass del converter: el postprocessor jwt() acepta authorities
    // ya formados, así que no necesitamos KeycloakRolesConverter en estos tests.

    // ---- Rutas públicas ----------------------------------------------------

    @Test
    void actuatorHealthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void swaggerUiEsPublico() throws Exception {
        // notRejectedByAuth: ni 401 ni 403. Permitimos 200, 302 (redirect a /index),
        // o 404 si Springdoc no resolvió el resource — lo importante es que no haya
        // sido bloqueado por la capa de seguridad.
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().is(notRejectedByAuth()));
    }

    @Test
    void apiDocsEsPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().is(notRejectedByAuth()));
    }

    @Test
    void swaggerUiHtmlAliasEsPublico() throws Exception {
        // /swagger-ui.html está explícitamente en permitAll — cubrirlo separado
        // detecta si alguien lo elimina del config.
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is(notRejectedByAuth()));
    }

    @Test
    void actuatorHealthSubpathEsPublico() throws Exception {
        // /actuator/health/** también permitAll — cubre liveness/readiness para K8s.
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().is(notRejectedByAuth()));
    }

    // ---- /api/admin/** requiere ADMIN --------------------------------------

    @Test
    void adminRutaSinAuthDevuelve401() throws Exception {
        mvc.perform(get("/api/admin/distribuciones")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRutaConDistribuidorDevuelve403() throws Exception {
        mvc.perform(get("/api/admin/distribuciones").with(jwtConRol("DISTRIBUIDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRutaConAdminPermitida() throws Exception {
        // 200, 404 o 500 son válidos — lo importante es que NO sea 401/403.
        mvc.perform(get("/api/admin/distribuciones").with(jwtConRol("ADMIN")))
                .andExpect(status().is(notRejectedByAuth()));
    }

    // ---- /api/** requiere ADMIN o DISTRIBUIDOR -----------------------------

    @Test
    void apiPublicaSinAuthDevuelve401() throws Exception {
        mvc.perform(get("/api/distribuciones")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiConDistribuidorPermitida() throws Exception {
        mvc.perform(get("/api/distribuciones").with(jwtConRol("DISTRIBUIDOR")))
                .andExpect(status().is(notRejectedByAuth()));
    }

    @Test
    void apiConAdminPermitida() throws Exception {
        mvc.perform(get("/api/distribuciones").with(jwtConRol("ADMIN"))).andExpect(status().is(notRejectedByAuth()));
    }

    @Test
    void apiConRolDesconocidoDevuelve403() throws Exception {
        mvc.perform(get("/api/distribuciones").with(jwtConRol("OTRO_ROL"))).andExpect(status().isForbidden());
    }

    // ---- /actuator/** (no health) requiere ADMIN ---------------------------

    @Test
    void actuatorPrometheusSinAuthDevuelve401() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorPrometheusConDistribuidorDevuelve403() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(jwtConRol("DISTRIBUIDOR"))).andExpect(status().isForbidden());
    }

    @Test
    void actuatorPrometheusConAdminPermitida() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(jwtConRol("ADMIN"))).andExpect(status().is(notRejectedByAuth()));
    }

    // ---- Rutas no listadas: denyAll ----------------------------------------

    @Test
    void rutaNoListadaSinAuthDevuelve401() throws Exception {
        mvc.perform(get("/foo/bar")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaNoListadaConAdminDevuelve403() throws Exception {
        // SecurityConfig termina con anyRequest().denyAll(): incluso ADMIN no entra.
        mvc.perform(get("/foo/bar").with(jwtConRol("ADMIN"))).andExpect(status().isForbidden());
    }

    // ---- Helpers -----------------------------------------------------------

    /**
     * Postprocessor que adjunta a la request un JWT mockeado con el rol indicado.
     * Spring Security Test no usa el JwtDecoder bean — sustituye el principal
     * directo en el SecurityContext del test.
     */
    private static RequestPostProcessor jwtConRol(String rol) {
        return jwt().jwt(j -> j.claim("realm_access", java.util.Map.of("roles", java.util.List.of(rol))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + rol));
    }

    /** Status NO 401 ni 403 — la request pasó la capa de auth. */
    private static org.hamcrest.Matcher<Integer> notRejectedByAuth() {
        return org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.not(401), org.hamcrest.Matchers.not(403));
    }
}
