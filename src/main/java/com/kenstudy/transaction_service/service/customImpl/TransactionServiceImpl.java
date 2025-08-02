package com.kenstudy.transaction_service.service.customImpl;

import com.kenstudy.customer.CustomerResponseDTO;
import com.kenstudy.payment.PaymentRequestDTO;
import com.kenstudy.payment.PaymentResponseDTO;
import com.kenstudy.transaction.TransactStatus;
import com.kenstudy.transaction.TransactType;
import com.kenstudy.transaction.TransactionResponseDTO;
import com.kenstudy.transaction.TransferRequestDTO;
import com.kenstudy.transaction_service.config.client.TransactClient;
import com.kenstudy.transaction_service.exception.ResourceNotFoundException;
import com.kenstudy.transaction_service.exception.TransactionNotFoundException;
import com.kenstudy.transaction_service.model.Transaction;
import com.kenstudy.transaction_service.repository.TransactionRepository;
import com.kenstudy.transaction_service.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;

@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactClient transactClient;
    private final TransactionRepository transactionRepository;
    private final TransactionalOperator txOperator;

    @Autowired
    public TransactionServiceImpl(TransactClient transactClient, TransactionRepository transactionRepository, TransactionalOperator txOperator) {
        this.transactClient = transactClient;
        this.transactionRepository = transactionRepository;
        this.txOperator = txOperator;
    }


    @Override
    public Mono<TransactionResponseDTO> transferFund(TransferRequestDTO requestDTO) {
        if (ObjectUtils.isEmpty(requestDTO)) {
            return Mono.error(new TransactionNotFoundException("Transfer fund request must not be empty"));
        }
        // Transactional DB Save (no external call inside)
        Mono<Transaction> savedTransactionMono = Mono.justOrEmpty(requestDTO.getSenderAcctId())
                .flatMap(transactClient::getCustomerAndAcctDetails)
                .switchIfEmpty(Mono.error(new TransactionNotFoundException("Sender details not found")))
                .zipWith(Mono.justOrEmpty(requestDTO.getRecipientAcctId())
                        .flatMap(transactClient::getCustomerAndAcctDetails)
                    .switchIfEmpty(Mono.error(new TransactionNotFoundException("Receiver details not found"))))
                .flatMap(tuple -> {
                    var sender = tuple.getT1();
                    var receiver = tuple.getT2();

                    return checkTransactRequest(sender, requestDTO, receiver)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Invalid transaction request")))
                            .flatMap(transactionRepository::save)
                            .flatMap(saved -> {
                                if (saved.getId() == null) {
                                    return Mono.error(new TransactionNotFoundException("Transaction ID is null after save"));
                                }
                                return Mono.just(saved);
                    });
        }).as(txOperator::transactional); // commit DB transaction before making external call

// External PaymentDelivery API Call (after DB transaction is committed/persisted)
        return savedTransactionMono.delayElement(Duration.ofMillis(200)) // delay to ensure DB visibility
                .flatMap(trs -> mapToPaymtTrans(trs, requestDTO)
                        .flatMap(transactClient::makePaymentTransfer)
                        .flatMap(payRes -> mapToTransact(payRes, trs))
                        .flatMap(transactionRepository::save)
                        .flatMap(finalSave -> mapToTransResponseDto(finalSave, requestDTO)));

    }

    private Mono<Transaction> mapToTransact(PaymentResponseDTO response, Transaction trans) {
        if (ObjectUtils.isEmpty(response)) {
            return Mono.error(new ResourceNotFoundException("Payment Delivery is empty"));
        }
        trans.setTransactionStatus(TransactStatus.TRANSACTION_COMPLETED.name());
        trans.setCreatedDate(LocalDate.now());
        return Mono.just(trans);
    }


    @Override
    public Mono<Transaction> getTransaction(Integer transactId) {
        return Mono.justOrEmpty(transactId).flatMap(transactionRepository::findById);
    }


    private Mono<PaymentRequestDTO> mapToPaymtTrans(Transaction res, TransferRequestDTO trans) {
        PaymentRequestDTO paymt = new PaymentRequestDTO();
        paymt.setTransactStatus(res.getTransactionStatus());
        paymt.setTransactionId(res.getId());
        paymt.setAmount(res.getAmount());
        paymt.setCustomerId(trans.getSenderId());
        paymt.setAccountId(res.getAccountId());
        paymt.setRecipientId(res.getRecipientId());
        return Mono.just(paymt);
    }


    private Mono<Transaction> checkTransactRequest(CustomerResponseDTO sender, TransferRequestDTO requestDTO, CustomerResponseDTO receiver) {

        if (!sender.getAccountId().equals(requestDTO.getSenderAcctId()) || !receiver.getAccountId().equals(requestDTO.getRecipientAcctId())) {
            return Mono.error(new TransactionNotFoundException("Sender/Receiver Account ID does not match"));
        }

        if (requestDTO.getAmount() < 2) {
            return Mono.error(new TransactionNotFoundException("Transfer must be from $2"));
        }
        if (!sender.getId().equals(requestDTO.getSenderId())) {
            return Mono.error(new TransactionNotFoundException("Customer Id: " + requestDTO.getSenderId() + " with Account " + "details not found"));
        }

        if (requestDTO.getAmount() > 10000) {
            return Mono.error(new TransactionNotFoundException("Transfer must not exceed $10,000.00"));
        }

        Transaction transaction = new Transaction();
        transaction.setAccountId(requestDTO.getSenderAcctId());
        transaction.setRecipientId(requestDTO.getRecipientAcctId());
        transaction.setAmount(requestDTO.getAmount());
        transaction.setDescription(requestDTO.getDescription());
        transaction.setTransactionStatus(TransactStatus.TRANSACTION_CREATED.name());
        transaction.setTransactType(TransactType.TRANSFER.name());
        transaction.setCreatedDate(LocalDate.now());

        return Mono.just(transaction);
    }

    private Mono<TransactionResponseDTO> mapToTransResponseDto(Transaction trans, TransferRequestDTO req) {
        TransactionResponseDTO output = new TransactionResponseDTO();
        output.setAccountId(trans.getAccountId());
        output.setTransactionId(trans.getId());
        output.setCustomerId(req.getSenderId());
        output.setRecipientId(trans.getRecipientId());
        output.setAmount(trans.getAmount());
        output.setTransactionType(trans.getTransactType());
        output.setCreatedDated(trans.getCreatedDate());
        output.setStatus(trans.getTransactionStatus());
        output.setDescription(req.getDescription());
        output.setAmount(req.getAmount());
        return Mono.just(output);
    }
}
