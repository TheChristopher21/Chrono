package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomer(Customer customer);
}
