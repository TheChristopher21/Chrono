package com.chrono.chrono.dto.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CreateJournalEntryRequest {
    private LocalDate entryDate;
    private String description;
    private String source = "MANUAL";
    private String documentReference;
    private List<CreateJournalEntryRequestLine> lines;

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(String documentReference) {
        this.documentReference = documentReference;
    }

    public List<CreateJournalEntryRequestLine> getLines() {
        return lines;
    }

    public void setLines(List<CreateJournalEntryRequestLine> lines) {
        this.lines = lines;
    }

    public static class CreateJournalEntryRequestLine {
        private Long accountId;
        private BigDecimal debit;
        private BigDecimal credit;
        private String memo;

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public BigDecimal getDebit() {
            return debit;
        }

        public void setDebit(BigDecimal debit) {
            this.debit = debit;
        }

        public BigDecimal getCredit() {
            return credit;
        }

        public void setCredit(BigDecimal credit) {
            this.credit = credit;
        }

        public String getMemo() {
            return memo;
        }

        public void setMemo(String memo) {
            this.memo = memo;
        }
    }
}
