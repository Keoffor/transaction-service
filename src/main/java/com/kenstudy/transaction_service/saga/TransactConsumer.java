package com.kenstudy.transaction_service.saga;

import com.kenstudy.event.AccountEvent;
import com.kenstudy.event.CustomerEvent;
import com.kenstudy.event.TransactEvent;
import com.kenstudy.event.status.AccountStatus;
import com.kenstudy.event.status.CustomerStatus;
import com.kenstudy.event.status.TransStatus;
import com.kenstudy.transaction.TransferRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
public class TransactConsumer {
    private final TransactHandler handler;

    @Autowired
    public TransactConsumer(TransactHandler handler) {
        this.handler = handler;
    }

    @Bean
    public Consumer<CustomerEvent> consumeCustomerEvent(){
        //listen to customer topic
        //check request created
        //if transfer request created proceed to initiate transaction
        //if transfer request status is failed, update transaction as failed.
        return this::processCustomerTransfer;

    }

    private void processCustomerTransfer(CustomerEvent customerEvent) {
        if (CustomerStatus.TRANSFER_CREATED.equals(customerEvent.getStatus()) && !customerEvent.isEventClosed()) {
            handler.createTransact(customerEvent);
            return;// Assuming createTransact returns Mono<Something>
        }
        TransactEvent transactEvent = new TransactEvent();
        transactEvent.setRequestDTO(customerEvent.getTransRequestDTO());
        handler.cancelEvent(
                transactEvent,
                customerEvent.getTransRequestDTO(),
                customerEvent.getErrorMessage(),
                TransStatus.TRANSACTION_FAILED
        ).then();
    }


    @Bean
    public Function<Flux<AccountEvent>, Flux<TransactEvent>> updateTransactionAccount() {
        return accountEventFlux -> accountEventFlux.flatMap(this::processAcctConsumeEvent);
    }
    private Mono<TransactEvent> processAcctConsumeEvent(AccountEvent accountEvent) {
        if (AccountStatus.PAYMENT_COMPLETED.equals(accountEvent.getAccountStatus())
                && accountEvent.isEventClosed()) {
            return this.handler.updateAcctConsumeEvent(accountEvent);
        } else {
            TransactEvent transactEvent = new TransactEvent();
            TransferRequestDTO dto = handler.mapToTransactionDto(accountEvent.getPaymentRequestDTO());
            transactEvent.setRequestDTO(dto);
            return this.handler.cancelEvent(transactEvent,
                    transactEvent.getTransRequestDTO(),
                    transactEvent.getErrorMessage(),
                    transactEvent.getTransStatus());
        }
    }

}
