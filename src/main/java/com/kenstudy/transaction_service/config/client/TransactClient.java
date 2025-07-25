package com.kenstudy.transaction_service.config.client;


import com.kenstudy.customer.CustomerResponseDTO;
import com.kenstudy.payment.PaymentRequestDTO;
import com.kenstudy.payment.PaymentResponseDTO;
import com.kenstudy.transaction_service.exception.ErrorMessageResponse;
import com.kenstudy.transaction_service.exception.ResourceNotFoundException;
import com.kenstudy.transaction_service.exception.TransactionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TransactClient {
    private static final String URL_ACCOUNT_HOST = "http://localhost:4001";
    private static final String URL_PAYMT_HOST = "http://localhost:4003";
    private final WebClient webClient;

    @Autowired
    public TransactClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(URL_ACCOUNT_HOST).build();
    }

    public Mono<CustomerResponseDTO> getCustomerAndAcctDetails(Integer accountId) {
        return webClient.get()
                .uri("/v1/account/customer-acct-details/{accountId}", accountId)
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                    clientResponse.bodyToMono(ErrorMessageResponse.class)
                        .flatMap(body -> Mono.error(
                            new TransactionNotFoundException(
                                "Account service client error: " + body.getMessage()))))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                    clientResponse.bodyToMono(ErrorMessageResponse.class)
                        .flatMap(body -> Mono.error(
                            new ResourceNotFoundException(
                                "Account server error: " + body.getMessage()))))
                .bodyToMono(CustomerResponseDTO.class);
    }

    public Mono<PaymentResponseDTO> makePaymentTransfer(PaymentRequestDTO payDto) {

        log.info("Payment delivery payload :::: {} ", payDto);
        return webClient.post().uri(URL_PAYMT_HOST + "/v1/payment/make-transfer").contentType(MediaType.APPLICATION_JSON).bodyValue(payDto).retrieve().onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class).flatMap(body -> Mono.error(new TransactionNotFoundException("Client errorMessage calling payment-delivery (" + response.statusCode() + "): " + body)))).onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class).flatMap(body -> Mono.error(new TransactionNotFoundException("Server error (" + response.statusCode() + "): " + body)))).bodyToMono(PaymentResponseDTO.class);
    }
}
