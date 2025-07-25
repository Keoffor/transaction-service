package com.kenstudy.transaction_service.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.HashMap;
import java.util.Map;

@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {


    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
      Throwable error = getError(request);
      Map<String, Object> errorAttributes = new HashMap<>();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (error instanceof TransactionNotFoundException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (error instanceof ResourceNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        }
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", error.getMessage());
        errorAttributes.put("path", request.path());
        return errorAttributes;
    }
}
