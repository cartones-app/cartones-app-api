package com.eliasgonzalez.cartones.distribucion.service.etiquetas;

import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reordena la lista de etiquetas según {@link OrdenEtiqueta} antes de que el
 * generador dibuje las hojas. La salida tiene SIEMPRE el mismo tamaño que la
 * entrada — los slots vacíos del último grupo se mantienen como huecos al
 * dibujar (responsabilidad del renderer).
 *
 * <p><strong>SECUENCIAL</strong>: no-op, devuelve la misma lista.
 *
 * <p><strong>INTERCALADO</strong>: pensado para imprimir, apilar hojas y
 * cortar todas a la vez. Con N etiquetas y L slots por hoja, hay
 * {@code K = ceil(N / L)} hojas. La etiqueta en hoja {@code p} (0-indexed) y
 * slot {@code s} (0-indexed) es la posición {@code s · K + p} de la lista
 * original. Las posiciones que excederían N se dejan {@code null}.
 *
 * <p>Ejemplo (N=10, L=3, K=4):
 * <pre>
 *   Secuencial: hojas [1,2,3] [4,5,6] [7,8,9] [10,_,_]
 *   Intercalado: hojas [1,5,9] [2,6,10] [3,7,_] [4,8,_]
 * </pre>
 * Al apilar las 4 hojas y cortar horizontalmente: pila top [1,2,3,4],
 * pila medio [5,6,7,8], pila bottom [9,10] — concatenadas dan 1..10 en orden.
 */
@Component
public class EtiquetaOrderer {

    public List<EtiquetaDTO> ordenar(List<EtiquetaDTO> originales, OrdenEtiqueta orden, int slotsPorHoja) {
        if (originales == null || originales.isEmpty()) {
            return originales;
        }
        if (slotsPorHoja <= 0) {
            throw new IllegalArgumentException("slotsPorHoja debe ser > 0, fue " + slotsPorHoja);
        }
        OrdenEtiqueta efectivo = orden != null ? orden : OrdenEtiqueta.defaultValue();
        return switch (efectivo) {
            case SECUENCIAL -> originales;
            case INTERCALADO -> intercalar(originales, slotsPorHoja);
        };
    }

    private List<EtiquetaDTO> intercalar(List<EtiquetaDTO> originales, int L) {
        int n = originales.size();
        int k = (n + L - 1) / L; // ceil(n / L)
        int total = k * L;       // mantiene tamaño de "grid completa"; los huecos quedan null

        // Salida en orden: hoja 0 slot 0, hoja 0 slot 1, ..., hoja 0 slot L-1,
        //                  hoja 1 slot 0, ..., hoja k-1 slot L-1.
        // Para hoja p slot s la etiqueta original es la posición (s * k + p).
        List<EtiquetaDTO> salida = new ArrayList<>(total);
        for (int p = 0; p < k; p++) {
            for (int s = 0; s < L; s++) {
                int idxOriginal = s * k + p;
                salida.add(idxOriginal < n ? originales.get(idxOriginal) : null);
            }
        }
        return salida;
    }
}
