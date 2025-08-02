package com.kenstudy.transaction_service.saga;

import com.kenstudy.event.*;
import com.kenstudy.event.status.CustomerStatus;
import com.kenstudy.event.status.TransStatus;
import com.kenstudy.payment.PaymentRequestDTO;
import com.kenstudy.transaction.TransferRequestDTO;
import com.kenstudy.transaction_service.model.Transaction;
import com.kenstudy.transaction_service.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Configuration
@Slf4j
public class TransactHandler {

    private final TransactionRepository transRepo;
    private final TransactPublisher transactPublisher;

    @Autowired
    public TransactHandler(TransactionRepository transRepo, TransactPublisher transactPublisher) {
        this.transRepo = transRepo;
        this.transactPublisher = transactPublisher;
    }

        public Mono<Void> createTransact(CustomerEvent customerEvent) {
            TransactEvent transactEvent = new TransactEvent();
            TransferRequestDTO dto = customerEvent.getTransRequestDTO();
            if (ObjectUtils.isEmpty(customerEvent)) {
                log.error("CustomerEvent is null or empty. Cannot proceed with transaction.");
                persistFailedTransact(dto);
                cancelEvent(transactEvent,dto,
                        "CustomerEvent is null or empty. Cannot proceed with transaction.",
                         TransStatus.TRANSACTION_FAILED);
//                return transactPublisher.publishTransactEvent(transactEvent);
            }

            if (ObjectUtils.isEmpty(dto.getSenderId()) ||
                    ObjectUtils.isEmpty(dto.getSenderAcctId()) ||
                    ObjectUtils.isEmpty(dto.getRecipientId()) ||
                    ObjectUtils.isEmpty(dto.getRecipientAcctId())) {

                log.error("Missing required transaction fields in DTO:::===::: {}", dto);
                persistFailedTransact(dto);
                cancelEvent(transactEvent,dto,
                        "Missing required transaction fields",
                        TransStatus.TRANSACTION_FAILED);
//                return transactPublisher.publishTransactEvent(transactEvent);
            }

            if (customerEvent.getStatus().equals(CustomerStatus.TRANSFER_FAILED)) {
                log.error("Customer status indicates transfer failure:::===::: {}", customerEvent.getStatus());
                persistFailedTransact(dto);
                cancelEvent(transactEvent,dto,"Customer event failed. transfer unsuccessful",
                        TransStatus.TRANSACTION_FAILED);
//                return transactPublisher.publishTransactEvent(transactEvent);
            }
            // Save to DB and publish initiation event
        return  transRepo.save(maptoEntity(dto))
                .flatMap(saved -> {
                    dto.setTransactId(saved.getId());
                    transactEvent.setRequestDTO(dto);
                    transactEvent.setTStatus(TransStatus.TRANSACTION_INITIATED);
                    transactEvent.setIsEventClosed(false);
                    return transactPublisher.publishTransactEvent(transactEvent);
                })
                .doOnSuccess(v -> log.info("Transaction processed successfully"))
                .doOnError(e -> log.error("Transaction processing failed", e))
                .onErrorResume(e -> Mono.empty());

        }

    public Mono<TransactEvent> updateAcctConsumeEvent(AccountEvent accountEvent) {
        TransactEvent transactEvent = new TransactEvent();
        TransferRequestDTO dto = mapToTransactionDto(accountEvent.getPaymentRequestDTO());
        if(ObjectUtils.isEmpty(dto)){
            persistFailedTransact(dto);
            cancelEvent(transactEvent,dto,
                    "AccountEvent is null or empty. Cannot proceed with transaction.", TransStatus.TRANSACTION_FAILED);
        }
        if (ObjectUtils.isEmpty(dto.getSenderId()) ||
                ObjectUtils.isEmpty(dto.getSenderAcctId()) ||
                ObjectUtils.isEmpty(dto.getRecipientId()) ||
                ObjectUtils.isEmpty(dto.getRecipientAcctId())) {
            log.error("Missing required account fields in DTO:::===::: {}", dto);
            persistFailedTransact(dto);
           return cancelEvent(transactEvent,dto,"Missing required account fields",TransStatus.TRANSACTION_FAILED);
        }
       return Mono.justOrEmpty(transRepo.findById(dto.getTransactId()))
               .flatMap( trans -> mapTo(trans, dto, transactEvent))
               .flatMap(transRepo::save)
               .flatMap(saved -> {
                   dto.setTransactId(saved.getId());
                   transactEvent.setRequestDTO(dto);
                   transactEvent.setTStatus(TransStatus.TRANSACTION_COMPLETED);
                   transactEvent.setIsEventClosed(true);
                    return Mono.just(transactEvent);
                });
    }

    private Mono<Transaction> mapTo(Mono<Transaction> transt, TransferRequestDTO dto, TransactEvent event) {
        return transt.flatMap(trans -> {
            boolean matches = trans.getId().equals(dto.getTransactId()) &&
                    trans.getAccountId().equals(dto.getSenderAcctId()) &&
                    trans.getRecipientId().equals(dto.getRecipientAcctId());
            if (matches) {
                trans.setTransactionStatus(TransStatus.TRANSACTION_COMPLETED.name());
                trans.setAmount(dto.getAmount());
                trans.setDescription(dto.getDescription());
                trans.setCreatedDate(LocalDate.now());
                trans.setAccountId(dto.getSenderId());
                trans.setRecipientId(dto.getRecipientId());
                trans.setIsClosed(true);
                return Mono.just(trans);
            } else {
                persistFailedTransact(dto);
                return cancelEvent(event, dto, "transaction Id does not match", TransStatus.TRANSACTION_FAILED)
                    .then(Mono.empty());
            }
        });
    }

    private Transaction maptoEntity (TransferRequestDTO dto) {
        Transaction transaction = new Transaction();
        transaction.setTransactionStatus(TransStatus.TRANSACTION_INITIATED.name());
        transaction.setTransactType("Transfer");
        transaction.setAmount(dto.getAmount());
        transaction.setDescription(dto.getDescription());
        transaction.setCreatedDate(LocalDate.now());
        transaction.setAccountId(dto.getSenderId());
        transaction.setRecipientId(dto.getRecipientId());
        return transaction;
    }

    public  <E extends CancelableEvent<D,S>, D,S> Mono<E> cancelEvent(E event, D dto, String reason, S failure) {
        log.warn("Cancelling event due to: {}", reason);
        event.setRequestDTO(dto);
        event.setErrorMessage(reason);
        event.setTStatus(failure);
        event.setIsEventClosed(true);
        return Mono.just(event);
    }

    public void persistFailedTransact(TransferRequestDTO dto) {
       Transaction trans = new Transaction();
        trans.setRecipientId(dto.getRecipientId());
        trans.setTransactionStatus(TransStatus.TRANSACTION_FAILED.name());
        trans.setTransactType("Transfer");
        trans.setAmount(dto.getAmount());
        trans.setDescription(dto.getDescription());
        trans.setAccountId(dto.getSenderId());
        trans.setIsClosed(true);
        trans.setCreatedDate(LocalDate.now());
        transRepo.save(trans)
                .doOnSuccess(saved -> log.info("Saved failed activity for account {}",saved))
                .doOnError(error -> log.error("Failed to save account activity: {}", error.getMessage()))
                .subscribe();
    }

    public TransferRequestDTO mapToTransactionDto(PaymentRequestDTO paydto) {
        TransferRequestDTO dto = new TransferRequestDTO();
        dto.setRecipientId(paydto.getRecipientId());
        dto.setSenderAcctId(paydto.getAccountId());
        dto.setTransactId(paydto.getTransactionId());
        dto.setAmount(paydto.getAmount());
        dto.setSenderId(paydto.getCustomerId());
        dto.setRecipientAcctId(paydto.getRecipientAcctId());
        dto.setStatus(paydto.getTransactStatus());
        dto.setDescription(dto.getDescription());
        return dto;
    }

}
