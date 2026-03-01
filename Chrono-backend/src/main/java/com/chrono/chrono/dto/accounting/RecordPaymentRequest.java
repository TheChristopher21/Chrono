package com.chrono.chrono.dto.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RecordPaymentRequest {
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String memo;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
