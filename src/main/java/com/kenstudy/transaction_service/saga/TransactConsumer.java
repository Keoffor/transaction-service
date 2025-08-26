package com.kenstudy.transaction_service.saga;

import com.kenstudy.event.AccountEvent;
import com.kenstudy.event.CustomerEvent;
import com.kenstudy.event.TransactEvent;
import com.kenstudy.payment.PaymentRequestDTO;
import com.kenstudy.transaction.TransferRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
@Slf4j
public class TransactConsumer {
    private final TransactHandler handler;
    private final StreamBridge streamBridge;

    @Autowired
    public TransactConsumer(TransactHandler handler, StreamBridge streamBridge) {
        this.handler = handler;
        this.streamBridge = streamBridge;
    }


    @Bean
    public Consumer<CustomerEvent> customerEventConsumer() {
        return customerEvent -> handler.createTransact(customerEvent)
                .doOnSuccess( s -> log.info("Transact event successfully created"))
                .subscribe(); // Trigger the reactive chain
    }

//    public Consumer<AccountEvent> accountEventResultPublisher(StreamBridge streamBridge) {
//        return acct -> {
//            if (!acct.isError() && acct.isEventClosed()) {
//                handler.updateAcctConsumeEvent(acct)
//                        .doOnNext(success -> {
//                            log.info("Publishing success event: {}", success);
//                            streamBridge.send("updateTransactEventSuccess-out-0", success);
//                        })
//                        .subscribe(); // trigger
//            } else {
//                TransferRequestDTO dto = handler.mapToTransactionDto(acct.getPaymentRequestDTO());
//                String reason = acct.getErrorMessage() != null ? acct.getErrorMessage() : "Event closed or failed";
//                handler.handleEventFailures(new TransactEvent(), dto, reason)
//                        .doOnNext(failure -> {
//                            log.error("Publishing failure event: {}", failure);
//                            streamBridge.send("updateTransactEventFailure-out-0", failure);
//                        })
//                        .subscribe(); // trigger
//            }
//        };
//    }


    @Bean
    public Function<Flux<AccountEvent>, Mono<Void>> accountEventResultPublisher() {
        return acctEvents -> acctEvents
                .flatMap(acct -> handler.updateAcctConsumeEvent(acct)
                    .flatMap(trans -> {
                        trans.setCustomerEventId(acct.getCustomerEventId());
                        if (trans.isError()) {
                            log.info("Event already closed/failed. Publishing failure to Kafka: {}", trans);
                            return Mono.defer(() -> {
                                trans.setCustomerEventId(acct.getCustomerEventId());
                                streamBridge.send("transactEventFailure-out-0", trans);
                                return Mono.empty();
                            });

                        }
                        log.info("Processed event, publishing success to Kafka: {}", trans);
                        return Mono.defer(() -> {
                        streamBridge.send("transactEventSuccess-out-0", trans);
                        return Mono.empty();
                        });

                    })
                )
                .then(); // convert the Flux<Void> to Mono<Void>
    }

    @Bean
    public Function<Flux<AccountEvent>, Mono<Void>> accountEventFailureConsumer() {
        TransactEvent event = new TransactEvent();
        return failedEvents -> failedEvents
                .flatMap(failedEvent -> {
                    TransferRequestDTO dto = handler.mapToTransactionDto(failedEvent.getPaymentRequestDTO());
                    event.setCustomerEventId(failedEvent.getCustomerEventId());
                    String reason = failedEvent.getErrorMessage() != null
                            ? failedEvent.getErrorMessage()
                            : "Event closed or failed";

                    return handler.handleEventFailures(event, dto, reason)
                            .doOnNext(e -> log.info("Transact Event to publish {} ", e))
                            .flatMap(failure -> Mono.defer(() -> {
                                streamBridge.send("transactEventFailure-out-0", failure);
                                return Mono.empty();
                            }));

                })
                .then();
    }

}
