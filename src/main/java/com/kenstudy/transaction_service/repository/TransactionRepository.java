package com.kenstudy.transaction_service.repository;

import com.kenstudy.transaction_service.model.Transaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, Integer> {

}
