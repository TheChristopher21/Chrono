package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsCreditAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PmsCreditAccountRepository extends JpaRepository<PmsCreditAccount,Long> {
    Optional<PmsCreditAccount> findByProperty_IdAndOrganization_Id(Long propertyId,Long organizationId);
    List<PmsCreditAccount> findAllByProperty_IdOrderByOrganization_NameAsc(Long propertyId);
}
