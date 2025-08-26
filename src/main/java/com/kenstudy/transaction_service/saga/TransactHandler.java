package com.kenstudy.transaction_service.saga;

import com.kenstudy.event.*;
import com.kenstudy.event.status.AccountStatus;
import com.kenstudy.event.status.CustomerStatus;
import com.kenstudy.event.status.TransStatus;
import com.kenstudy.payment.PaymentRequestDTO;
import com.kenstudy.transaction.TransferRequestDTO;
import com.kenstudy.transaction_service.exception.TransactionNotFoundException;
import com.kenstudy.transaction_service.model.Transaction;
import com.kenstudy.transaction_service.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Configuration
@Slf4j
public class TransactHandler {

    private final TransactionRepository transRepo;
    private final TransactPublisher transactPublisher;
    private final R2dbcEntityTemplate template;

    @Autowired
    public TransactHandler(TransactionRepository transRepo, TransactPublisher transactPublisher, R2dbcEntityTemplate template) {
        this.transRepo = transRepo;
        this.transactPublisher = transactPublisher;
        this.template = template;
    }


    public Mono<Void> createTransact(CustomerEvent customerEvent) {
        TransactEvent transactEvent = new TransactEvent();
        TransferRequestDTO dto = customerEvent.getTransRequestDTO();
        transactEvent.setCustomerEventId(customerEvent.getEventId());
        log.warn("correlationId {} ", transactEvent.getCustomerEventId());
        // Pre-check for null/empty event
        if (ObjectUtils.isEmpty(customerEvent) || customerEvent.isEventClosed()) {
            log.warn("Received closed or empty CustomerEvent. Skipping transaction. Event: {}", customerEvent);
            if (ObjectUtils.isNotEmpty(customerEvent)){
                return handleEventFailures(transactEvent, dto,
                        "Event already closed or empty. Skipping transaction.").then();
            }
            return Mono.empty();

        }

        // Validate required fields
        if (ObjectUtils.isEmpty(dto.getSenderId()) ||
                ObjectUtils.isEmpty(dto.getSenderAcctId()) ||
                ObjectUtils.isEmpty(dto.getRecipientId()) ||
                ObjectUtils.isEmpty(dto.getRecipientAcctId())) {

            return handleEventFailures(transactEvent, dto,
                    "Missing required transaction fields").then();
        }

        // Check customer status
        if (CustomerStatus.FAILURE.equals(customerEvent.getStatus())) {
            return handleEventFailures(transactEvent, dto,
                    "Customer status indicates transfer failure").then();
        }

        // Happy path – Save transaction, publish initiation event
        return transRepo.save(maptoEntity(dto))
                .flatMap(saved -> {
                    dto.setTransactId(saved.getId());
                    transactEvent.setRequestDTO(dto);
                    transactEvent.setStatus(TransStatus.TRANSACTION_INITIATED);
                    transactEvent.setErrorMessage(null);
                    transactEvent.setError(false);
                    transactEvent.setEventClosed(false);
                    return transactPublisher.publishTransactEvent(transactEvent);
                })
                .doOnSuccess(v -> log.info("Transaction processed successfully"))
                .doOnError(e -> log.error("Transaction processing failed", e));
    }




    public Mono<TransactEvent> updateAcctConsumeEvent(AccountEvent accountEvent) {
        TransactEvent transactEvent = new TransactEvent();

        TransferRequestDTO dto = mapToTransactionDto(accountEvent.getPaymentRequestDTO());
        if(ObjectUtils.isEmpty(dto)) {

            log.warn("Received empty AccountEvent. Skipping transaction. Event: {}", accountEvent);
            return handleEventFailures(transactEvent, dto,
                        "Event already closed or empty. Skipping transaction.");

        }
        if (ObjectUtils.isEmpty(dto.getSenderId()) ||
                ObjectUtils.isEmpty(dto.getSenderAcctId()) ||
                ObjectUtils.isEmpty(dto.getRecipientId()) ||
                ObjectUtils.isEmpty(dto.getRecipientAcctId())) {
            log.error("Missing required account fields in DTO:::===::: {}", dto);

           return  handleEventFailures(transactEvent, dto,
                    "Missing required transaction fields");
        }

        // Check accountEvent status
        if (AccountStatus.PAYMENT_FAILED.equals(accountEvent.getAccountStatus()) ||
                AccountStatus.PAYMENT_CANCELLED.equals(accountEvent.getAccountStatus())) {
            return handleEventFailures(transactEvent, dto,
                    "AccountEvent status indicates payment failure");
        }
        log.info("proceeding to Happy Path {} ", dto);
        // Happy Path: update transaction event state
       return transRepo.findByIdCustom(dto.getTransactId())
               .switchIfEmpty(Mono.error(
                       new TransactionNotFoundException("transaction Id is empty or not found "+dto.getTransactId())))
               .flatMap( trans -> {
                   boolean matches =
                           trans.getAccountId().equals(dto.getSenderAcctId()) &&
                           trans.getRecipientId().equals(dto.getRecipientAcctId());
                   if(!matches){
                       return handleEventFailures(transactEvent, dto,
                               "Transaction account IDs do not match");
                   }
                   mapTo(trans, dto);
                   return transRepo.save(trans)
                           .flatMap(saved -> {
                       dto.setTransactId(saved.getId());
                       transactEvent.setRequestDTO(dto);
                       transactEvent.setStatus(TransStatus.TRANSACTION_COMPLETED);
                       transactEvent.setEventClosed(true);
                       transactEvent.setError(false);
                       transactEvent.setErrorMessage(null);
                       return Mono.just(transactEvent);
                   });

               })   .onErrorResume(ex -> {
                   // Covers cases where the DB query or save throws an error
                   log.error("Transaction processing error for DTO {}: {}", dto, ex.getMessage(), ex);
                   transactEvent.setCustomerEventId(accountEvent.getCustomerEventId());
                   return handleEventFailures(transactEvent, dto, ex.getMessage());
               });
    }

    private void mapTo(Transaction trans, TransferRequestDTO dto) {
                trans.setTransactionStatus(TransStatus.TRANSACTION_COMPLETED.name());
                trans.setAmount(dto.getAmount());
                trans.setDescription(dto.getDescription());
                trans.setCreatedDate(LocalDate.now());
                trans.setAccountId(dto.getSenderAcctId());
                trans.setRecipientId(dto.getRecipientAcctId());
                trans.setIsClosed(true);

    }

    private Transaction maptoEntity (TransferRequestDTO dto) {
        Transaction transaction = new Transaction();
        transaction.setTransactionStatus(TransStatus.TRANSACTION_INITIATED.name());
        transaction.setTransactType("Transfer");
        transaction.setAmount(dto.getAmount());
        transaction.setDescription(dto.getDescription());
        transaction.setCreatedDate(LocalDate.now());
        transaction.setAccountId(dto.getSenderAcctId());
        transaction.setRecipientId(dto.getRecipientAcctId());
        transaction.setIsClosed(false);
        return transaction;
    }

    public  <E extends CancelableEvent<D,S>, D,S> E cancelEvent(E event, D dto, String reason, S failure) {
        log.warn("Cancelling event due to: {}", reason);
        event.setRequestDTO(dto);
        event.setErrorMessage(reason);
        event.setStatus(failure);
        event.setError(true);
        event.setEventClosed(true);
        return event;
    }

    public Mono<Void> persistFailedTransact(TransferRequestDTO dto) {
        return findByTransactId(dto.getTransactId())

                .switchIfEmpty(Mono.error(new TransactionNotFoundException(
                        "transaction Id is empty or not found " + dto.getTransactId())))
                .flatMap(trans -> {
                    log.info("logging fetched transaction record {}", trans);
                    trans.setRecipientId(dto.getRecipientId());
                    trans.setTransactionStatus(dto.getStatus());
                    trans.setTransactType("Transfer");
                    trans.setAmount(dto.getAmount());
                    trans.setDescription(dto.getDescription());
                    trans.setAccountId(dto.getSenderAcctId());
                    trans.setIsClosed(true);
                    trans.setCreatedDate(LocalDate.now());
                    return transRepo.save(trans);
                })
                .doOnSuccess(saved -> log.info("Saved failed activity for {}", saved))
                .doOnError(error -> log.error("Failed to save account activity: {}", error.getMessage()))
                .then(); // returns Mono<Void>
    }


    public Mono<TransactEvent> handleEventFailures(TransactEvent event,
                                                   TransferRequestDTO dto, String reason) {
        log.error(reason);
        dto.setStatus(TransStatus.TRANSACTION_FAILED.name());
        return persistFailedTransact(dto)
                .thenReturn(cancelEvent(event, dto, reason, TransStatus.TRANSACTION_FAILED));
    }


    public TransferRequestDTO mapToTransactionDto(PaymentRequestDTO paydto) {
        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setRecipientId(paydto.getRecipientId());
        dto.setSenderAcctId(paydto.getAccountId());
        dto.setTransactId(paydto.getTransactionId());
        dto.setAmount(paydto.getAmount());
        dto.setSenderId(paydto.getCustomerId());
        dto.setRecipientAcctId(paydto.getRecipientAcctId());
        dto.setDescription(paydto.getDescription());
        return dto;
    }

    public Mono<Transaction> findByTransactId(Integer id) {
        return template.select(Transaction.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .first();
    }

}
