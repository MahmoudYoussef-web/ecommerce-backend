package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_lines")
public class JournalLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal credit;
}