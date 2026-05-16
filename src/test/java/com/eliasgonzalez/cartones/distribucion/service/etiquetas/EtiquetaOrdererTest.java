package com.eliasgonzalez.cartones.distribucion.service.etiquetas;

import static org.assertj.core.api.Assertions.assertThat;

import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Tests del reordenamiento de etiquetas. Foco en el orden INTERCALADO porque
 * es la única lógica no-trivial — SECUENCIAL es un no-op.
 *
 * <p>El invariante clave: al apilar las K hojas resultantes y cortar
 * horizontalmente cada slot, las pilas concatenadas dan la secuencia
 * original 1..N en orden.
 */
class EtiquetaOrdererTest {

    private final EtiquetaOrderer orderer = new EtiquetaOrderer();

    @Test
    void secuencialDevuelveLaListaIntacta() {
        List<EtiquetaDTO> original = etiquetasDe(5);

        List<EtiquetaDTO> out = orderer.ordenar(original, OrdenEtiqueta.SECUENCIAL, 3);

        assertThat(out).isSameAs(original);
    }

    @Test
    void intercaladoConDivisibleExacto() {
        // N=12, L=3 -> K=4
        // Hoja 0: [1, 5, 9]
        // Hoja 1: [2, 6, 10]
        // Hoja 2: [3, 7, 11]
        // Hoja 3: [4, 8, 12]
        List<EtiquetaDTO> out = orderer.ordenar(etiquetasDe(12), OrdenEtiqueta.INTERCALADO, 3);

        assertThat(numerosDe(out)).containsExactly(1, 5, 9,  2, 6, 10,  3, 7, 11,  4, 8, 12);
    }

    @Test
    void intercaladoConRemainderRellenaConNulls() {
        // N=10, L=3 -> K=4 (ceil 10/3 = 4). Total slots = 12; 2 quedan null.
        // Hoja 0: [1, 5, 9]
        // Hoja 1: [2, 6, 10]
        // Hoja 2: [3, 7, _]
        // Hoja 3: [4, 8, _]
        List<EtiquetaDTO> out = orderer.ordenar(etiquetasDe(10), OrdenEtiqueta.INTERCALADO, 3);

        assertThat(out).hasSize(12);
        // Mapeo a numeros o null para el null check.
        List<Integer> nums = out.stream()
                .map(e -> e == null ? null : e.getNumeroVendedor())
                .toList();
        assertThat(nums).containsExactly(1, 5, 9,  2, 6, 10,  3, 7, null,  4, 8, null);
    }

    @Test
    void intercaladoConL4() {
        // N=10, L=4 -> K=3 (ceil 10/4 = 3). Total = 12; 2 null al final.
        // Hoja 0: [1, 4, 7, 10]
        // Hoja 1: [2, 5, 8, _]
        // Hoja 2: [3, 6, 9, _]
        List<EtiquetaDTO> out = orderer.ordenar(etiquetasDe(10), OrdenEtiqueta.INTERCALADO, 4);

        List<Integer> nums = out.stream()
                .map(e -> e == null ? null : e.getNumeroVendedor())
                .toList();
        assertThat(nums).containsExactly(1, 4, 7, 10,  2, 5, 8, null,  3, 6, 9, null);
    }

    @Test
    void apilarYCortarReproduceSecuenciaOriginal() {
        // Test del invariante de uso real. Tras apilar K hojas y cortar
        // horizontalmente, cada pila tiene los items que estaban en el mismo
        // slot s en orden de hoja: out[0*L+s], out[1*L+s], ..., out[(K-1)*L+s].
        // Concatenando las pilas s=0, s=1, ..., s=L-1 debe dar 1..N (ignorando nulls).
        int n = 11;
        int l = 4;
        int k = (n + l - 1) / l;

        List<EtiquetaDTO> out = orderer.ordenar(etiquetasDe(n), OrdenEtiqueta.INTERCALADO, l);

        int prev = 0;
        for (int s = 0; s < l; s++) {
            for (int p = 0; p < k; p++) {
                EtiquetaDTO etiq = out.get(p * l + s);
                if (etiq == null) continue;
                int num = etiq.getNumeroVendedor();
                assertThat(num).as("pila slot=%d hoja=%d", s, p).isEqualTo(prev + 1);
                prev = num;
            }
        }
        assertThat(prev).isEqualTo(n);
    }

    @Test
    void listaVaciaOullDevuelveLoMismo() {
        assertThat(orderer.ordenar(List.of(), OrdenEtiqueta.INTERCALADO, 3)).isEmpty();
        assertThat(orderer.ordenar(null, OrdenEtiqueta.INTERCALADO, 3)).isNull();
    }

    @Test
    void slotsPorHojaInvalidoLanzaIAE() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> orderer.ordenar(etiquetasDe(3), OrdenEtiqueta.INTERCALADO, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- helpers -----

    private List<EtiquetaDTO> etiquetasDe(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> EtiquetaDTO.builder().numeroVendedor(i).nombre("Vendedor " + i).build())
                .toList();
    }

    private List<Integer> numerosDe(List<EtiquetaDTO> etiquetas) {
        return etiquetas.stream().map(EtiquetaDTO::getNumeroVendedor).toList();
    }
}
