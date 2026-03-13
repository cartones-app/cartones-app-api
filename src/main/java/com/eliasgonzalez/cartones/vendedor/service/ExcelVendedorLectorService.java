package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.common.exception.FileProcessingException;
import com.eliasgonzalez.cartones.common.util.ExcelUtil;
import com.eliasgonzalez.cartones.common.util.TextoUtil;
import com.eliasgonzalez.cartones.vendedor.domain.enums.ExcelColumnaEnum;
import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.service.dto.VendedorExcelDTO;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.domain.Vendedor;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import com.eliasgonzalez.cartones.vendedor.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la lectura del Excel de distribución y persiste los datos.
 * Estrategia "todo o nada": si alguna fila tiene errores críticos, se aborta sin guardar nada.
 * Por cada fila válida: upsert en la tabla vendedor (maestro) +
 * creación de ProcesoDistribucionVendedor (datos de este proceso).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelVendedorLectorService {

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

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.clearAllCachedResultValues();
            evaluator.evaluateAll();

            int sheetIndex = wb.getSheetIndex(ExcelColumnaEnum.HOJA.getValor());
            if (sheetIndex < 0) {
                throw new ExcelProcessingException(
                    "La hoja ('" + ExcelColumnaEnum.HOJA.getValor() + "') no fue encontrada.", List.of());
            }
            Sheet sheet = wb.getSheetAt(sheetIndex);

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ExcelProcessingException("El archivo Excel está vacío o no tiene encabezados.", List.of());
            }

            Map<String, Integer> idx = new HashMap<>();
            for (Cell c : header) {
                String name = c.getStringCellValue();
                if (name != null) idx.put(TextoUtil.normalize(name), c.getColumnIndex());
            }

            validarEncabezados(idx);

            Integer vIdx = idx.get(TextoUtil.normalize(ExcelColumnaEnum.VENDEDOR.getValor()));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                int filaActual = i + 1;
                Row row = sheet.getRow(i);

                if (ExcelUtil.isRowEmpty(row, vIdx, evaluator)) {
                    filasIgnoradas.add("Fila " + filaActual + ": Vacía o sin nombre de vendedor. Omitiendo.");
                    log.warn("Se omite la fila {} porque el nombre del vendedor está vacío. ProcesoId: {}", filaActual, procesoIdCreado);
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
                    String errorMessage = String.format("Fila %d: Error inesperado al procesar: %s", filaActual, e.getMessage());
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
                    .filasIgnoradas(filasIgnoradas).procesoId(procesoIdCreado).build();
                log.info(filasIgnoradasDTO.toString());
                return filasIgnoradasDTO;
            }

            return CargaVendedoresResponseDTO.builder().procesoId(procesoIdCreado).build();

        } catch (ExcelProcessingException | FileProcessingException e) {
            log.error("[INTERNO] Fallo en el procesamiento del Excel. Errores: {}. Mensaje: {}", erroresGlobales, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Fallo crítico en el procesamiento del Excel", e);
            throw new RuntimeException("Error al procesar el archivo Excel: " + e.getMessage(), e);
        }
    }

    private ProcesoDistribucionVendedor resolverVendedorYCrearRegistro(VendedorExcelDTO dto, String procesoId) {
        String nombre = dto.getNombre().trim();
        Vendedor vendedor = vendedorRepo.findByNombreIgnoreCase(nombre)
            .orElseGet(() -> vendedorRepo.save(Vendedor.builder().nombre(nombre).build()));

        String deudaStr = dto.getDeudaStr();
        BigDecimal deuda = (deudaStr == null || deudaStr.isBlank())
            ? BigDecimal.ZERO
            : new BigDecimal(deudaStr.trim());

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

    private void validarEncabezados(Map<String, Integer> idx) {
        String[] required = {
            ExcelColumnaEnum.VENDEDOR.getValor(),
            ExcelColumnaEnum.SALDO.getValor(),
            ExcelColumnaEnum.CANT_SENETE.getValor(),
            ExcelColumnaEnum.RESULT_SENETE.getValor(),
            ExcelColumnaEnum.CANT_TELEBINGO.getValor(),
            ExcelColumnaEnum.RESULT_TELEBINGO.getValor()
        };
        List<String> faltantes = new ArrayList<>();
        for (String h : required) {
            if (!idx.containsKey(TextoUtil.normalize(h))) faltantes.add(h);
        }
        if (!faltantes.isEmpty()) {
            throw new ExcelProcessingException("Faltan encabezados requeridos: " + faltantes, List.of());
        }
    }

    private VendedorExcelDTO mapearFilaADTO(Map<String, Integer> idx, Row row, int filaActual, FormulaEvaluator evaluator) {
        return VendedorExcelDTO.builder()
            .nombre(ExcelUtil.getStringCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.VENDEDOR.getValor())), evaluator))
            .deudaStr(ExcelUtil.getStringCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.SALDO.getValor())), evaluator))
            .cantidadSenete(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.CANT_SENETE.getValor())), evaluator))
            .resultadoSenete(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.RESULT_SENETE.getValor())), evaluator))
            .cantidadTelebingo(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.CANT_TELEBINGO.getValor())), evaluator))
            .resultadoTelebingo(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(ExcelColumnaEnum.RESULT_TELEBINGO.getValor())), evaluator))
            .filaActual(filaActual)
            .build();
    }
}
