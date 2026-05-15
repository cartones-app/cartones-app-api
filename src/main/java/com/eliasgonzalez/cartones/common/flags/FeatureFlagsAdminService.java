package com.eliasgonzalez.cartones.common.flags;

import java.util.List;

import com.eliasgonzalez.cartones.common.flags.dto.FlagViewDTO;
import com.eliasgonzalez.cartones.common.flags.dto.SetFlagRequest;

/**
 * API admin para gestionar overrides runtime de flags. Solo debe consumirse
 * desde controllers protegidos con {@code hasRole('ADMIN')}.
 */
public interface FeatureFlagsAdminService {

    /**
     * Lista todos los flags conocidos (registro estático) con su valor efectivo y
     * si tienen override activo.
     */
    List<FlagViewDTO> listarFlags();

    /**
     * Devuelve el detalle de un flag puntual. Lanza si la clave no está registrada.
     */
    FlagViewDTO obtenerFlag(String flagKey);

    /** Crea o actualiza el override de un flag. */
    FlagViewDTO setOverride(String flagKey, SetFlagRequest request);

    /**
     * Elimina el override — el valor efectivo vuelve al default de
     * {@code flags.yml}.
     */
    void clearOverride(String flagKey);
}
