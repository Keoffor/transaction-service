package com.kenstudy.transaction_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "transaction")
public class Transaction {
    @Id
    private Integer id;
    @Column("account_id")
    private Integer accountId;
    private Integer recipientId;
    private String description;
    private Double amount;
    @Column("transact_type")
    private String TransactType;
    @Column("transact_status")
    private String transactionStatus;
    private LocalDate createdDate;

}
