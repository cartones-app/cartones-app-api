package com.eliasgonzalez.cartones.common.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.eliasgonzalez.cartones.common.exception.FileProcessingException;

class MultipartFileValidatorTest {

    @Test
    void validar_pasaConXlsxYContentTypeOpenXML() {
        MultipartFile f = new MockMultipartFile(
                "file",
                "vendedores.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        assertThatCode(() -> MultipartFileValidator.validarXlsx(f)).doesNotThrowAnyException();
    }

    @Test
    void validar_pasaConVndMsExcelComoContentType() {
        // Algunos browsers (Edge legacy, Excel 2010 export) reportan este MIME para
        // xlsx.
        MultipartFile f = new MockMultipartFile("file", "datos.xlsx", "application/vnd.ms-excel", new byte[] {1});

        assertThatCode(() -> MultipartFileValidator.validarXlsx(f)).doesNotThrowAnyException();
    }

    @Test
    void validar_pasaConOctetStreamComoContentType() {
        // Caso real: a veces el browser no detecta MIME y manda octet-stream.
        MultipartFile f = new MockMultipartFile("file", "datos.xlsx", "application/octet-stream", new byte[] {1});

        assertThatCode(() -> MultipartFileValidator.validarXlsx(f)).doesNotThrowAnyException();
    }

    @Test
    void validar_pasaSiContentTypeEsNull() {
        // Sin content-type explícito (curl crudo, tests, fetch sin Content-Type).
        // Aceptado mientras la extensión sea .xlsx.
        MultipartFile f = new MockMultipartFile("file", "datos.xlsx", null, new byte[] {1});

        assertThatCode(() -> MultipartFileValidator.validarXlsx(f)).doesNotThrowAnyException();
    }

    @Test
    void validar_lanzaSiNombreEsCsv() {
        MultipartFile f = new MockMultipartFile("file", "datos.csv", "text/csv", new byte[] {1});

        assertThatThrownBy(() -> MultipartFileValidator.validarXlsx(f))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void validar_lanzaSiNombreSinExtension() {
        MultipartFile f = new MockMultipartFile(
                "file", "vendedores", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1
                });

        assertThatThrownBy(() -> MultipartFileValidator.validarXlsx(f)).isInstanceOf(FileProcessingException.class);
    }

    @Test
    void validar_lanzaSiContentTypeEsTextPlain() {
        // Un atacante podría renombrar un .txt a .xlsx, pero el browser puede
        // reportar el content-type real. Rechazamos.
        MultipartFile f = new MockMultipartFile("file", "fake.xlsx", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> MultipartFileValidator.validarXlsx(f))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining("Content-Type");
    }

    @Test
    void validar_lanzaSiArchivoEsNull() {
        assertThatThrownBy(() -> MultipartFileValidator.validarXlsx(null)).isInstanceOf(FileProcessingException.class);
    }

    @Test
    void validar_lanzaSiArchivoEstaVacio() {
        MultipartFile f = new MockMultipartFile(
                "file", "vacio.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThatThrownBy(() -> MultipartFileValidator.validarXlsx(f))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void validar_aceptaExtensionUppercase() {
        // Defensa contra usuarios con extensión .XLSX en mayúsculas.
        MultipartFile f = new MockMultipartFile(
                "file", "datos.XLSX", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1
                });

        assertThatCode(() -> MultipartFileValidator.validarXlsx(f)).doesNotThrowAnyException();
    }
}
