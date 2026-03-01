package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.JournalEntry;

import java.time.LocalDate;
import java.util.List;

public class JournalEntryDTO {
    private final Long id;
    private final LocalDate entryDate;
    private final String description;
    private final String source;
    private final String documentReference;
    private final List<JournalEntryLineDTO> lines;

    public JournalEntryDTO(Long id, LocalDate entryDate, String description, String source,
                           String documentReference, List<JournalEntryLineDTO> lines) {
        this.id = id;
        this.entryDate = entryDate;
        this.description = description;
        this.source = source;
        this.documentReference = documentReference;
        this.lines = lines;
    }

    public static JournalEntryDTO from(JournalEntry entry) {
        return new JournalEntryDTO(
                entry.getId(),
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getSource(),
                entry.getDocumentReference(),
                entry.getLines().stream().map(JournalEntryLineDTO::from).toList());
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getDescription() {
        return description;
    }

    public String getSource() {
        return source;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public List<JournalEntryLineDTO> getLines() {
        return lines;
    }
}
