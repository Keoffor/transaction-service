package com.kenstudy.transaction_service.repository;

import com.kenstudy.transaction_service.model.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TransactionRepository extends ReactiveCrudRepository<Transaction, Integer> {
    @Query("SELECT * FROM transaction WHERE id = :id")
    Mono<Transaction> findByIdCustom(Integer id);
}
