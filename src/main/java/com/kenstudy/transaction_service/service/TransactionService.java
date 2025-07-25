package com.kenstudy.transaction_service.service;

import com.kenstudy.transaction.TransactionResponseDTO;
import com.kenstudy.transaction.TransferRequestDTO;
import com.kenstudy.transaction_service.model.Transaction;
import reactor.core.publisher.Mono;

public interface TransactionService {
    Mono<TransactionResponseDTO> transferFund(TransferRequestDTO requestDTO);
    Mono<Transaction>getTransaction(Integer transactId);
}
