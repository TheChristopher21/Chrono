package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsPaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PmsPaymentSettingsRepository extends JpaRepository<PmsPaymentSettings,Long> {
    List<PmsPaymentSettings> findAllByAutomaticDepositLinksTrue();
}
