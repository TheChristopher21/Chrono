package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
