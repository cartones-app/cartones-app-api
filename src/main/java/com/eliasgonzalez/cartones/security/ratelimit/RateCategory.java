package com.eliasgonzalez.cartones.security.ratelimit;

/**
 * Familias de endpoints con costo similar. Cada categoría tiene su propio
 * presupuesto de requests por minuto: agrupar por costo permite distintos
 * RPM sin un bucket por endpoint individual.
 *
 * <ul>
 *   <li>{@link #UPLOAD} — carga de Excel (parseo POI + validación,
 *       memory-heavy). Default bajo.</li>
 *   <li>{@link #COMPUTE} — generación intensiva: simulación de distribución,
 *       generación de PDFs, exportación de Excel. Default moderado.</li>
 * </ul>
 *
 * Para abuso volumétrico genérico (cualquier GET) el rate limit debe vivir
 * en el proxy reverso (Cloudflare/nginx), no acá.
 */
public enum RateCategory {
    UPLOAD,
    COMPUTE,
}
