package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private UserService userService;
    @Autowired
    private TimeTrackingService timeTrackingService;

    private boolean featureEnabled(Principal principal) {
        if (principal == null) return false;
        User u = userService.getUserByUsername(principal.getName());
        return u.getCompany() != null && Boolean.TRUE.equals(u.getCompany().getCustomerTrackingEnabled());
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAll(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(customerService.findAll());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Customer>> getRecent(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(timeTrackingService.getRecentCustomers(principal.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer customer, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(customerService.save(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customer, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return customerService.findById(id)
                .map(existing -> {
                    existing.setName(customer.getName());
                    return ResponseEntity.ok(customerService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        customerService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
