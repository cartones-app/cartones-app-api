package com.eliasgonzalez.cartones.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

    @AfterEach
    void limpiarContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pathDeUpload_consumeBucketUpload() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 100);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        MockHttpServletResponse first = ejecutar(filter, "POST", "/api/vendedores/carga");
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = ejecutar(filter, "POST", "/api/vendedores/carga");
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void pathDeCompute_consumeBucketCompute() throws Exception {
        // Capacidad asimétrica: UPLOAD=100 (irrelevante), COMPUTE=1.
        // Confirma que el filter resuelve la categoría del endpoint.
        RateLimiter limiter = new RateLimiter(100, 1);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        MockHttpServletResponse first = ejecutar(filter, "POST", "/api/distribuciones/abc-123/simular");
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = ejecutar(filter, "POST", "/api/distribuciones/abc-123/simular");
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void uploadAgotado_noBloqueaCompute() throws Exception {
        // UPLOAD=1, COMPUTE=1. Tras agotar UPLOAD, COMPUTE sigue disponible.
        RateLimiter limiter = new RateLimiter(1, 1);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        ejecutar(filter, "POST", "/api/vendedores/carga");
        MockHttpServletResponse uploadBloqueado =
                ejecutar(filter, "POST", "/api/vendedores/carga");
        assertThat(uploadBloqueado.getStatus()).isEqualTo(429);

        MockHttpServletResponse computeOk =
                ejecutar(filter, "POST", "/api/distribuciones/p1/archivos");
        assertThat(computeOk.getStatus()).isEqualTo(200);
    }

    @Test
    void pathNoMatcheado_passthrough() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimiter(1, 1));
        // Endpoint no rate-limiteado: aunque se invoque muchas veces, todas
        // deben pasar. shouldNotFilter retorna true → ni siquiera entra al filtro.
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response =
                    ejecutar(filter, "GET", "/api/distribuciones");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void getEnPathDeUpload_passthrough() throws Exception {
        // Solo POST tiene rate limit. GET al mismo path pasa sin gastar bucket.
        RateLimitFilter filter = new RateLimitFilter(new RateLimiter(1, 1));
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response =
                    ejecutar(filter, "GET", "/api/vendedores/carga");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void pathDeExportarRuta_categoriaCompute() throws Exception {
        RateLimiter limiter = new RateLimiter(100, 1);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        ejecutar(filter, "POST", "/api/ruta/sess-1/exportar");
        MockHttpServletResponse bloqueado =
                ejecutar(filter, "POST", "/api/ruta/sess-1/exportar");
        assertThat(bloqueado.getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse ejecutar(RateLimitFilter filter, String method, String uri)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }
}
