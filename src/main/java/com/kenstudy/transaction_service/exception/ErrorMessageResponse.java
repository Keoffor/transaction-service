package com.kenstudy.transaction_service.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor
@Data

public class ErrorMessageResponse {
    private int status;
    private String error;
    private String message;
    private String path;

}
