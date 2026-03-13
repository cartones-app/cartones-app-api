package com.eliasgonzalez.cartones.distribucion.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipEmpaquetadorService {

    /**
     * Crea un archivo ZIP en memoria a partir de un mapa de archivos.
     * @param archivos Map donde Key es el nombre del archivo (ej: "resumen.pdf")
     * y Value es el contenido en bytes.
     * @return Resource (ByteArrayResource) listo para ser enviado por el Controller.
     */
    public static Resource crearZip(Map<String, byte[]> archivos) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entrada : archivos.entrySet()) {
                String nombreArchivo = entrada.getKey();
                byte[] contenido = entrada.getValue();

                if (contenido != null && contenido.length > 0) {
                    agregarArchivo(zos, nombreArchivo, contenido);
                }
            }
        }

        // Retornamos un ByteArrayResource
        return new ByteArrayResource(baos.toByteArray());
    }

    private static void agregarArchivo(ZipOutputStream zos, String nombre, byte[] contenido) throws IOException {
        // Creamos la entrada del ZIP con el nombre del archivo
        ZipEntry entrada = new ZipEntry(nombre);
        zos.putNextEntry(entrada);

        // Escribimos los bytes directamente en el stream del ZIP
        zos.write(contenido);

        zos.closeEntry();
    }
}
