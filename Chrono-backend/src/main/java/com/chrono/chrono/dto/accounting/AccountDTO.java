package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.Account;
import com.chrono.chrono.entities.accounting.AccountType;

public class AccountDTO {
    private final Long id;
    private final String code;
    private final String name;
    private final AccountType type;
    private final boolean active;

    public AccountDTO(Long id, String code, String name, AccountType type, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.active = active;
    }

    public static AccountDTO from(Account account) {
        return new AccountDTO(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.isActive());
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }
}
