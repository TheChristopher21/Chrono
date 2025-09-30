package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.banking.PaymentInstruction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, Long> {
}
