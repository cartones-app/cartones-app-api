package com.eliasgonzalez.cartones.distribucion.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EstadoEnum {

    /** Se subió el Excel pero todavía no se simuló la distribución. */
    PENDIENTE("pendiente"),
    /** Se ejecutó la simulación. Si el usuario no genera archivos y descarta
     *  el proceso, queda en este estado hasta que algo lo marque como
     *  ABANDONADO (endpoint explícito desde el front o el job de limpieza). */
    SIMULADO("simulado"),
    /** Se generaron los archivos físicos. Estado terminal exitoso. */
    COMPLETADO("completado"),
    /** El usuario descartó el flujo o el job de limpieza marcó el proceso
     *  como caduco (sin actividad). Estado terminal — no se vuelve atrás. */
    ABANDONADO("abandonado");

    @JsonValue
    private final String value;
}
