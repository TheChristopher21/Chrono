package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.CrmActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long> {
    List<CrmActivity> findByCustomerOrderByTimestampDesc(Customer customer);
}
