package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByCompany(Company company);
}
