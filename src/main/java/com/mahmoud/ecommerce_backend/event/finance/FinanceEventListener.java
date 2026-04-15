package com.mahmoud.ecommerce_backend.event.finance;

import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.event.payment.PaymentCompletedEvent;
import com.mahmoud.ecommerce_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceEventListener {

    private final JournalEntryRepository journalEntryRepository;
    private final PaymentRepository paymentRepository;
    private final ChartOfAccountRepository coaRepository;

    @EventListener
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {

        Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        BigDecimal amount = payment.getAmount();

        ChartOfAccount ar = coaRepository.findByCode("1100")
                .orElseThrow(() -> new IllegalStateException("AR account not found"));

        ChartOfAccount revenue = coaRepository.findByCode("4000")
                .orElseThrow(() -> new IllegalStateException("Revenue account not found"));

        JournalEntry entry = JournalEntry.builder()
                .reference("PAYMENT#" + payment.getId())
                .description("Payment received for order #" + event.getOrderId())
                .postedAt(Instant.now())
                .build();

        JournalLine debit = JournalLine.builder()
                .journalEntry(entry)
                .account(ar)
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .build();

        JournalLine credit = JournalLine.builder()
                .journalEntry(entry)
                .account(revenue)
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .build();

        entry.setLines(List.of(debit, credit));

        journalEntryRepository.save(entry);

        log.info("JournalEntry created | paymentId={} amount={}",
                payment.getId(),
                amount);
    }
}