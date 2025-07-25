package com.kenstudy.transaction_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Slf4j
public class GlobalExceptionHandler extends AbstractErrorWebExceptionHandler {


    public GlobalExceptionHandler(GlobalErrorAttributes errorAttributes, WebProperties.Resources resources,
                          ApplicationContext applicationContext) {
        super(errorAttributes, resources, applicationContext);

        this.setMessageWriters(ServerCodecConfigurer.create().getWriters());
        this.setMessageReaders(ServerCodecConfigurer.create().getReaders());
    }


    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorPropertiesMap = getErrorAttributes(request, ErrorAttributeOptions.defaults());

        log.error("Exception caught in global handler:::::::::: {}", errorPropertiesMap.get("message"));
        ErrorMessageResponse error = ErrorMessageResponse.builder()
                .status((int) errorPropertiesMap.get("status"))
                .error((String) errorPropertiesMap.get("error"))
                .message((String) errorPropertiesMap.get("message"))
                .path(request.path())
                .build();
        return ServerResponse.status(error.getStatus())
                .bodyValue(error);
    }
}
