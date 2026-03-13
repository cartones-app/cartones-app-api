package com.eliasgonzalez.cartones.vendedor.dto;

import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Setter @ToString
@Builder
public class FilasIgnoradasDTO {

    private List<String> filasIgnoradas;
    private String procesoId;

}
