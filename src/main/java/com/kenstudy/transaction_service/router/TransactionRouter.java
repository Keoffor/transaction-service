package com.kenstudy.transaction_service.router;

import com.kenstudy.transaction_service.handler.TransactionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class TransactionRouter {
    @Bean
    public RouterFunction<ServerResponse> route(TransactionHandler transHandler) {
        return RouterFunctions.route()
                .path("/v1/transaction", builder -> builder
                        .POST("/fund-transfer",
                                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                                transHandler::transferFund)
                        .GET("/{transactId}",
                                RequestPredicates.accept(MediaType.APPLICATION_JSON),
                                transHandler::getTransaction)
                )
                .build();
    }
}
