package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsBillingSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface PmsBillingSettingsRepository extends JpaRepository<PmsBillingSettings,Long> {
    @Query("select s.propertyId from PmsBillingSettings s where s.automaticReminders=true")
    List<Long> automaticReminderProperties();
}
