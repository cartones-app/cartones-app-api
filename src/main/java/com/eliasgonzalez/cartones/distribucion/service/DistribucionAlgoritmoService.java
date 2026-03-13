package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.controller.dto.SimulacionRequestDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorInputDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.RangoCortadoDTO;
import com.eliasgonzalez.cartones.distribucion.service.dto.RangoLogico;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class DistribucionAlgoritmoService {

    // Margen amplio para permitir saltos grandes si el pool se genera automáticamente
    private static final int MARGEN_SEGURIDAD = 100;

    public List<VendedorSimuladoDTO> simularDistribucion(SimulacionRequestDTO request) {

        validarConfiguracion(request);

        // 1. CALCULAR DEMANDA
        int demandaTotalSenete = request.getVendedores().stream()
                .mapToInt(v -> v.getCantidadSenete() == null ? 0 : v.getCantidadSenete()).sum();

        int demandaTotalTelebingo = request.getVendedores().stream()
                .mapToInt(v -> v.getCantidadTelebingo() == null ? 0 : v.getCantidadTelebingo()).sum();

        // 2. CONVERTIR POOLS (Pilas de papel)
        // Nota: Creamos pools grandes para soportar el desperdicio "ilimitado"
        LinkedList<RangoLogico> poolSenete = convertirPool(
                request.getPoolSenete(), request.getInicioSeneteGral(), demandaTotalSenete
        );

        LinkedList<RangoLogico> poolTelebingo = convertirPool(
                request.getPoolTelebingo(), request.getInicioTelebingoGral(), demandaTotalTelebingo
        );

        // 3. MEZCLA ÚNICA (Orden Sagrado)
        List<VendedorInputDTO> vendedoresOrdenados = new ArrayList<>(request.getVendedores());
        if (request.isMezclar()) {
            Collections.shuffle(vendedoresOrdenados);
        }

        // 4. MAPAS DE RESULTADOS
        Map<Long, List<String>> resultadosSenete = new HashMap<>();
        Map<Long, List<String>> resultadosTelebingo = new HashMap<>();

        // 5. EJECUTAR LÓGICA (Procesamos cada juego por separado pero CON EL MISMO ORDEN de personas)
        procesarFilaConDesperdicioIlimitado(poolSenete, vendedoresOrdenados, true, resultadosSenete);
        procesarFilaConDesperdicioIlimitado(poolTelebingo, vendedoresOrdenados, false, resultadosTelebingo);

        // 6. RETORNAR DTOs
        // Ordenamos por rango de Seneté para el PDF (1, 2, 3...)
        return request.getVendedores().stream()
                .map(v -> VendedorSimuladoDTO.builder()
                        .id(v.getId())
                        .nombre(v.getNombre())
                        .rangosSenete(resultadosSenete.getOrDefault(v.getId(), new ArrayList<>()))
                        .rangosTelebingo(resultadosTelebingo.getOrDefault(v.getId(), new ArrayList<>()))
                        .build())
                .sorted(Comparator.comparingInt(this::extraerInicioOrdenamiento))
                .collect(Collectors.toList());
    }

    private void procesarFilaConDesperdicioIlimitado(LinkedList<RangoLogico> pool,
                                                     List<VendedorInputDTO> vendedoresEnOrden,
                                                     boolean esSenete,
                                                     Map<Long, List<String>> mapaResultados) {

        for (VendedorInputDTO vendedorOriginal : vendedoresEnOrden) {

            // Obtenemos la cantidad necesaria para ESTE juego
            int cantidadNecesaria = esSenete ? (vendedorOriginal.getCantidadSenete() == null ? 0 : vendedorOriginal.getCantidadSenete())
                    : (vendedorOriginal.getCantidadTelebingo() == null ? 0 : vendedorOriginal.getCantidadTelebingo());

            if (cantidadNecesaria <= 0) continue;

            Integer terminacion = esSenete ? vendedorOriginal.getTerminacionSenete() : vendedorOriginal.getTerminacionTelebingo();
            boolean esVip = (terminacion != null && terminacion >= 0);

            // BUCLE: El vendedor se queda en ventanilla hasta completar su pedido
            while (cantidadNecesaria > 0 && !pool.isEmpty()) {

                RangoLogico rangoActual = pool.getFirst();
                int inicioActual = rangoActual.getInicio();
                int disponibleBloque = rangoActual.getCantidad();

                boolean quemarPapel = false;
                int aQuemar = 0;
                int aTomar = 0;

                // --- LÓGICA DE DECISIÓN SIMPLIFICADA ---

                if (!esVip) {
                    // NORMAL: No quema nada, toma lo que hay.
                    aTomar = Math.min(cantidadNecesaria, disponibleBloque);
                } else {
                    // VIP: ¿Mi terminación está en el rango inmediato que tomaría?
                    // "Inmediato" significa: si tomo 'cantidadNecesaria' cartones empezando AHORA.
                    if (rangoContieneTerminacion(inicioActual, cantidadNecesaria, terminacion)) {
                        // SÍ: La tengo. Tomo los cartones.
                        aTomar = Math.min(cantidadNecesaria, disponibleBloque);
                    } else {
                        // NO: Está más adelante.
                        // Calculamos distancia exacta.
                        int distancia = calcularDistancia(inicioActual, terminacion);

                        // Como permitimos desperdicio ILIMITADO, quemamos exactamente la distancia
                        // para quedar parados sobre el número (o avanzar si el bloque se acaba).
                        quemarPapel = true;
                        aQuemar = Math.min(distancia, disponibleBloque);
                    }
                }

                // --- EJECUCIÓN FÍSICA ---

                if (quemarPapel) {
                    // Tiramos hojas a la basura (nadie se las lleva)
                    // Esto avanza el contador 'inicio' del rango
                    rangoActual.setInicio(rangoActual.getInicio() + aQuemar);
                    consumirBloqueSiVacio(pool, rangoActual);
                    // NO restamos cantidadNecesaria, el vendedor sigue esperando.
                }
                else if (aTomar > 0) {
                    // Asignamos al vendedor
                    int finCorte = rangoActual.getInicio() + aTomar - 1;
                    String rangoStr = rangoActual.getInicio() + "-" + finCorte;

                    mapaResultados.computeIfAbsent(vendedorOriginal.getId(), k -> new ArrayList<>()).add(rangoStr);

                    rangoActual.setInicio(finCorte + 1);
                    consumirBloqueSiVacio(pool, rangoActual);

                    cantidadNecesaria -= aTomar;
                }
                else {
                    // Caso borde (pool vacío o error)
                    pool.removeFirst();
                }
            }
        }
    }

    private void consumirBloqueSiVacio(LinkedList<RangoLogico> pool, RangoLogico rango) {
        if (rango.getInicio() > rango.getFin()) {
            pool.removeFirst();
        }
    }

    private int calcularDistancia(int numeroActual, int terminacionDeseada) {
        int termActual = numeroActual % 100;
        if (termActual == terminacionDeseada) return 0;

        if (termActual < terminacionDeseada) {
            return terminacionDeseada - termActual;
        } else {
            return (100 - termActual) + terminacionDeseada;
        }
    }

    private boolean rangoContieneTerminacion(int inicio, int cantidad, int terminacionDeseada) {
        int distancia = calcularDistancia(inicio, terminacionDeseada);
        // Si la distancia es menor que la cantidad que voy a llevar,
        // significa que el número deseado caerá en mis manos.
        return distancia < cantidad;
    }

    // --- MÉTODOS AUXILIARES ---

    private LinkedList<RangoLogico> convertirPool(List<RangoCortadoDTO> rangosCortados, Integer inicioGeneral, int demandaTotal) {
        if (rangosCortados != null && !rangosCortados.isEmpty()) {
            return rangosCortados.stream()
                    .map(d -> new RangoLogico(d.getInicio(), d.getFin()))
                    .collect(Collectors.toCollection(LinkedList::new));
        }
        if (inicioGeneral != null) {
            LinkedList<RangoLogico> poolAjustado = new LinkedList<>();
            // Multiplicamos x4 o x5 la demanda total para tener suficiente papel para quemar

            int finCalculado = inicioGeneral + demandaTotal + MARGEN_SEGURIDAD;
            log.info("El fin calculado es: {}", finCalculado);

            poolAjustado.add(new RangoLogico(inicioGeneral, finCalculado));
            return poolAjustado;
        }
        return new LinkedList<>();
    }

    private void validarConfiguracion(SimulacionRequestDTO request) {
        boolean tieneSenete = (request.getPoolSenete() != null && !request.getPoolSenete().isEmpty())
                || request.getInicioSeneteGral() != null;
        boolean tieneTelebingo = (request.getPoolTelebingo() != null && !request.getPoolTelebingo().isEmpty())
                || request.getInicioTelebingoGral() != null;

        if (!tieneSenete && !tieneTelebingo) {
            throw new IllegalArgumentException("Debes configurar al menos un rango para Seneté o Telebingo.");
        }
    }

    private int extraerInicioOrdenamiento(VendedorSimuladoDTO dto) {
        List<String> rangos = (dto.getRangosSenete() != null && !dto.getRangosSenete().isEmpty())
                ? dto.getRangosSenete() : dto.getRangosTelebingo();

        if (rangos == null || rangos.isEmpty()) return Integer.MAX_VALUE;
        String primerRango = rangos.get(0);
        if (!Character.isDigit(primerRango.charAt(0))) return Integer.MAX_VALUE;

        try {
            return Integer.parseInt(primerRango.split("-")[0].trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}