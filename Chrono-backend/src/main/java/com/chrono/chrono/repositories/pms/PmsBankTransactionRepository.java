package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsBankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PmsBankTransactionRepository extends JpaRepository<PmsBankTransaction,Long> {
 Optional<PmsBankTransaction> findByProperty_IdAndBankAccountAndExternalId(Long propertyId,String account,String externalId);
 Optional<PmsBankTransaction> findByIdAndProperty_Id(Long id,Long propertyId);
 List<PmsBankTransaction> findByBankImport_IdOrderByIdAsc(Long importId);
}
