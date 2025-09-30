package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;

public class CreateAccountRequest {
    private String code;
    private String name;
    private AccountType type;
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Account toEntity() {
        Account account = new Account();
        account.setCode(code);
        account.setName(name);
        account.setType(type);
        account.setActive(active);
        return account;
    }
}
