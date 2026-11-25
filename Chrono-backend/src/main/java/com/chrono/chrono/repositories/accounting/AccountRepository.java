package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCode(String code);

    List<Account> findByActiveTrueOrderByCodeAsc();

    Optional<Account> findByCompany_IdAndCode(Long companyId, String code);
    Optional<Account> findByIdAndCompany_Id(Long id, Long companyId);
    List<Account> findByCompany_IdAndActiveTrueOrderByCodeAsc(Long companyId);
    List<Account> findByCompanyIsNullOrderByIdAsc();
}
