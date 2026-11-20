package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Page<JournalEntry> findAllByOrderByEntryDateDesc(Pageable pageable);
    Optional<JournalEntry> findFirstByDocumentReferenceOrderByIdAsc(String documentReference);
    Page<JournalEntry> findByCompany_IdOrderByEntryDateDesc(Long companyId, Pageable pageable);
    Page<JournalEntry> findByCompanyIsNullOrderByIdAsc(Pageable pageable);
    Optional<JournalEntry> findFirstByCompany_IdAndSourceAndDocumentReferenceOrderByIdAsc(Long companyId, String source, String documentReference);
    boolean existsByCompanyIsNullAndSourceAndDocumentReference(String source, String documentReference);
    boolean existsByCompany_IdNotAndSourceAndDocumentReference(Long companyId, String source, String documentReference);
    boolean existsByCompanyIsNull();
}
