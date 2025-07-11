package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.UserService;
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

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer customer, Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(customerService.save(customer));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Customer>> recent(Principal principal) {
        if (!featureEnabled(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(List.of());
    }
}
