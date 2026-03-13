package com.eliasgonzalez.cartones.distribucion.component;

import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Getter @Setter
public class SimulacionCache {

    private List<VendedorSimuladoDTO> vendedorSimuladoDTOs;
    private LocalDate fechaSorteoSenete;
    private LocalDate fechaSorteoTelebingo;

    public void guardar(List<VendedorSimuladoDTO> vendedorSimuladoDTOs) {
        this.vendedorSimuladoDTOs = vendedorSimuladoDTOs;
    }

}
