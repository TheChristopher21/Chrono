package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.BankAccount;

public class BankAccountDTO {
    private final Long id;
    private final String iban;
    private final String bic;
    private final String name;
    private final String clearingNumber;

    public BankAccountDTO(Long id, String iban, String bic, String name, String clearingNumber) {
        this.id = id;
        this.iban = iban;
        this.bic = bic;
        this.name = name;
        this.clearingNumber = clearingNumber;
    }

    public static BankAccountDTO from(BankAccount account) {
        return new BankAccountDTO(
                account.getId(),
                account.getIban(),
                account.getBic(),
                account.getName(),
                account.getClearingNumber());
    }

    public Long getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public String getBic() {
        return bic;
    }

    public String getName() {
        return name;
    }

    public String getClearingNumber() {
        return clearingNumber;
    }
}
