package com.eliasgonzalez.cartones.vendedor.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.eliasgonzalez.cartones.common.excel.AbstractExcelParser;
import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.common.util.ExcelUtil;
import com.eliasgonzalez.cartones.common.util.TextoUtil;
import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.domain.Vendedor;
import com.eliasgonzalez.cartones.vendedor.domain.enums.ExcelColumnaEnum;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import com.eliasgonzalez.cartones.vendedor.repository.VendedorRepository;
import com.eliasgonzalez.cartones.vendedor.service.dto.VendedorExcelDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta la lectura del Excel de distribución y persiste los datos.
 * Estrategia "todo o nada": si alguna fila tiene errores críticos, se aborta sin guardar nada.
 * Por cada fila válida: upsert en la tabla vendedor (maestro) +
 * creación de ProcesoDistribucionVendedor (datos de este proceso).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelVendedorLectorService extends AbstractExcelParser {

    private static final List<String> COLUMNAS_REQUERIDAS = List.of(
            ExcelColumnaEnum.VENDEDOR.getValor(),
            ExcelColumnaEnum.SALDO.getValor(),
            ExcelColumnaEnum.CANT_SENETE.getValor(),
            ExcelColumnaEnum.RESULT_SENETE.getValor(),
            ExcelColumnaEnum.CANT_TELEBINGO.getValor(),
            ExcelColumnaEnum.RESULT_TELEBINGO.getValor());

    private final VendedorRepository vendedorRepo;
    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;
    private final ExcelVendedorValidadorService validadorService;

    @Transactional
    public CargaVendedoresResponseDTO leerExcel(MultipartFile file, String procesoIdCreado) {
        log.info("Iniciando procesamiento del archivo Excel: {}", file.getOriginalFilename());

        List<String> erroresGlobales = new ArrayList<>();
        List<ProcesoDistribucionVendedor> registrosParaGuardar = new ArrayList<>();
        List<String> filasIgnoradas = new ArrayList<>();

        try (InputStream is = file.getInputStream();
                Workbook wb = WorkbookFactory.create(is)) {

            FormulaEvaluator evaluator = crearEvaluador(wb);
            Sheet sheet = obtenerHoja(wb, ExcelColumnaEnum.HOJA.getValor());
            Map<String, Integer> idx = construirIndiceColumnas(sheet);
            validarEncabezados(idx, COLUMNAS_REQUERIDAS);

            Integer vIdx = idx.get(TextoUtil.normalize(ExcelColumnaEnum.VENDEDOR.getValor()));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                int filaActual = i + 1;
                Row row = sheet.getRow(i);

                if (ExcelUtil.isRowEmpty(row, vIdx, evaluator)) {
                    filasIgnoradas.add("Fila " + filaActual + ": Vacía o sin nombre de vendedor. Omitiendo.");
                    log.warn(
                            "Se omite la fila {} porque el nombre del vendedor está vacío. ProcesoId: {}",
                            filaActual,
                            procesoIdCreado);
                    continue;
                }

                try {
                    VendedorExcelDTO dto = mapearFilaADTO(idx, row, filaActual, evaluator);
                    List<String> erroresFila = validadorService.validate(dto);

                    if (!erroresFila.isEmpty()) {
                        erroresGlobales.addAll(erroresFila);
                    } else {
                        registrosParaGuardar.add(resolverVendedorYCrearRegistro(dto, procesoIdCreado));
                    }
                } catch (Exception e) {
                    String errorMessage =
                            String.format("Fila %d: Error inesperado al procesar: %s", filaActual, e.getMessage());
                    log.error(errorMessage);
                    erroresGlobales.add(errorMessage);
                }
            }

            if (!erroresGlobales.isEmpty()) {
                log.warn("Se detectaron {} errores. Abortando operación sin guardar nada.", erroresGlobales.size());
                throw new ExcelProcessingException(
                        "El archivo Excel contiene errores. No se ha guardado ningún dato.", erroresGlobales);
            }

            if (!registrosParaGuardar.isEmpty()) {
                procesoVendedorRepo.saveAll(registrosParaGuardar);
                log.info("Se han guardado exitosamente {} registros.", registrosParaGuardar.size());
            }

            if (!filasIgnoradas.isEmpty()) {
                CargaVendedoresResponseDTO filasIgnoradasDTO = CargaVendedoresResponseDTO.builder()
                        .filasIgnoradas(filasIgnoradas)
                        .procesoId(procesoIdCreado)
                        .build();
                log.info(
                        "Carga completada con {} fila(s) ignorada(s) para procesoId={}",
                        filasIgnoradas.size(),
                        procesoIdCreado);
                log.debug("Detalle de filas ignoradas: {}", filasIgnoradasDTO);
                return filasIgnoradasDTO;
            }

            return CargaVendedoresResponseDTO.builder()
                    .procesoId(procesoIdCreado)
                    .build();

        } catch (ExcelProcessingException | FileProcessingException e) {
            log.error(
                    "[INTERNO] Fallo en el procesamiento del Excel. Errores: {}. Mensaje: {}",
                    erroresGlobales,
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Fallo crítico en el procesamiento del Excel", e);
            throw new RuntimeException("Error al procesar el archivo Excel: " + e.getMessage(), e);
        }
    }

    private ProcesoDistribucionVendedor resolverVendedorYCrearRegistro(VendedorExcelDTO dto, String procesoId) {
        String nombre = dto.getNombre().trim();
        Vendedor vendedor = vendedorRepo
                .findByNombreIgnoreCase(nombre)
                .orElseGet(() ->
                        vendedorRepo.save(Vendedor.builder().nombre(nombre).build()));

        String deudaStr = dto.getDeudaStr();
        BigDecimal deuda = (deudaStr == null || deudaStr.isBlank()) ? BigDecimal.ZERO : new BigDecimal(deudaStr.trim());

        return ProcesoDistribucionVendedor.builder()
                .vendedor(vendedor)
                .procesoId(procesoId)
                .cantidadSenete(dto.getCantidadSenete())
                .resultadoSenete(dto.getResultadoSenete())
                .cantidadTelebingo(dto.getCantidadTelebingo())
                .resultadoTelebingo(dto.getResultadoTelebingo())
                .deuda(deuda)
                .build();
    }

    private VendedorExcelDTO mapearFilaADTO(
            Map<String, Integer> idx, Row row, int filaActual, FormulaEvaluator evaluator) {
        return VendedorExcelDTO.builder()
                .nombre(ExcelUtil.getStringCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.VENDEDOR.getValor())), evaluator))
                .deudaStr(ExcelUtil.getStringCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.SALDO.getValor())), evaluator))
                .cantidadSenete(ExcelUtil.getIntCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.CANT_SENETE.getValor())), evaluator))
                .resultadoSenete(ExcelUtil.getIntCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.RESULT_SENETE.getValor())), evaluator))
                .cantidadTelebingo(ExcelUtil.getIntCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.CANT_TELEBINGO.getValor())), evaluator))
                .resultadoTelebingo(ExcelUtil.getIntCell(
                        row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.RESULT_TELEBINGO.getValor())), evaluator))
                .filaActual(filaActual)
                .build();
    }
}
