package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.JournalEntryLine;

import java.math.BigDecimal;

public class JournalEntryLineDTO {
    private final Long accountId;
    private final String accountCode;
    private final String accountName;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private final String memo;

    public JournalEntryLineDTO(Long accountId, String accountCode, String accountName,
                               BigDecimal debit, BigDecimal credit, String memo) {
        this.accountId = accountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.debit = debit;
        this.credit = credit;
        this.memo = memo;
    }

    public static JournalEntryLineDTO from(JournalEntryLine line) {
        return new JournalEntryLineDTO(
                line.getAccount() != null ? line.getAccount().getId() : null,
                line.getAccount() != null ? line.getAccount().getCode() : null,
                line.getAccount() != null ? line.getAccount().getName() : null,
                line.getDebit(),
                line.getCredit(),
                line.getMemo());
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getMemo() {
        return memo;
    }
}
