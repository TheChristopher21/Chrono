package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.BankAccount;

public class CreateBankAccountRequest {
    private String iban;
    private String bic;
    private String name;
    private String clearingNumber;

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClearingNumber() {
        return clearingNumber;
    }

    public void setClearingNumber(String clearingNumber) {
        this.clearingNumber = clearingNumber;
    }

    public BankAccount toEntity(Company company) {
        BankAccount account = new BankAccount();
        account.setCompany(company);
        account.setIban(iban);
        account.setBic(bic);
        account.setName(name);
        account.setClearingNumber(clearingNumber);
        return account;
    }
}
