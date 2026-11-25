package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByCompany(Company company);
    Optional<BankAccount> findByIdAndCompany(Long id, Company company);
}
