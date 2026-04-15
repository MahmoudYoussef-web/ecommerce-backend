package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
}