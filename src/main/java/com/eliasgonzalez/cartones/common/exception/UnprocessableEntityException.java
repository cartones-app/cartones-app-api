package com.eliasgonzalez.cartones.common.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class UnprocessableEntityException extends RuntimeException {

    private final List<String> errorDetails;

    public UnprocessableEntityException(String message, List<String> errorDetails) {
        super(message);
        this.errorDetails = errorDetails;
    }
}
