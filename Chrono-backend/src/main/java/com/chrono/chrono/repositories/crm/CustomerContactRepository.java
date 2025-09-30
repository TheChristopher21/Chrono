package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {
    List<CustomerContact> findByCustomer(Customer customer);
}
