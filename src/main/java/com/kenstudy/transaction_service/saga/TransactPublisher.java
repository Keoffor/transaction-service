package com.kenstudy.transaction_service.saga;

import com.kenstudy.event.TransactEvent;
import com.kenstudy.transaction_service.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class TransactPublisher {

    @Autowired
    private Sinks.Many<TransactEvent> transSinks;

    public Mono<Void> publishTransactEvent(TransactEvent event) {
        Sinks.EmitResult result = transSinks.tryEmitNext(event);

        if (result.isFailure()) {
            log.error(" Failed to emit TransactEvent:::====:::::: {} ", result.name());
            return Mono.error(new ResourceNotFoundException("Failed to emit TransactEvent: " + result.name()));
        }
        return Mono.empty();
    }

}
