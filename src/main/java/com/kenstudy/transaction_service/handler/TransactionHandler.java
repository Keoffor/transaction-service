package com.kenstudy.transaction_service.handler;

import com.kenstudy.transaction.TransferRequestDTO;
import com.kenstudy.transaction_service.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class TransactionHandler {
    final private TransactionService transactionService;

    @Autowired
    public TransactionHandler (TransactionService transactionService){
        this.transactionService = transactionService;
    }

        public Mono<ServerResponse> transferFund(ServerRequest serverRequest) {
            return serverRequest.bodyToMono(TransferRequestDTO.class)
                    .flatMap(transactionService::transferFund)
                    .flatMap(transferResponse ->
                            ServerResponse.status(HttpStatus.CREATED)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(transferResponse)
                    );
        }

        public Mono<ServerResponse> getTransaction(ServerRequest req){
            Integer transactId = Integer.parseInt(req.pathVariable("transactId"));

            return transactionService.getTransaction(transactId)
                    .flatMap(transaction ->
                            ServerResponse.status(HttpStatus.OK)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(transaction));
        }
}
