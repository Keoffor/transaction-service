package com.kenstudy.transaction_service.config;

import com.kenstudy.event.TransactEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.function.Supplier;

@Configuration
public class TransactPublisherConfig {

    @Bean
    public Sinks.Many<TransactEvent> transactSinks(){

        return Sinks.many().multicast().onBackpressureBuffer();
    }

    @Bean
    public Supplier<Flux<TransactEvent>> transactSupplier(Sinks.Many<TransactEvent> sinks){

        return sinks::asFlux;
    }
}
