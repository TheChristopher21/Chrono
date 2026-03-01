package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.SecureMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecureMessageRepository extends JpaRepository<SecureMessage, Long> {
    List<SecureMessage> findByCompanyOrderBySentAtDesc(Company company);
}
