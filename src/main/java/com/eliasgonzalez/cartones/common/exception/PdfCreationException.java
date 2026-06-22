package com.eliasgonzalez.cartones.common.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class PdfCreationException extends RuntimeException {

    private final List<String> errorDetails;

    public PdfCreationException(String message, List<String> errorDetails) {
        super(message);
        this.errorDetails = errorDetails;
    }
}
