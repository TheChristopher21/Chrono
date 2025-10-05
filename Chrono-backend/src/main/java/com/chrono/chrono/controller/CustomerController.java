package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    @Autowired
    private TimeTrackingService timeTrackingService;

    /**
     * Helper-Methode, um den User aus dem Principal-Objekt zu extrahieren.
     * Macht den Code lesbarer und vermeidet Wiederholungen.
     */
    private User getPrincipalUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.getUserByUsername(principal.getName());
    }

    /**
     * Prüft, ob das Feature für den gegebenen User aktiviert ist.
     * Akzeptiert jetzt ein User-Objekt direkt.
     */
    private boolean featureEnabled(User user) {
        if (user == null || user.getCompany() == null) {
            return false;
        }

        if (Boolean.TRUE.equals(user.getCompany().getCustomerTrackingEnabled())) {
            return true;
        }

        return user.getCompany().getEnabledFeatures() != null
                && user.getCompany().getEnabledFeatures().contains("crm");
    }

    /**
     * GEÄNDERT: Holt nur die Kunden, die zur Firma des angemeldeten Benutzers gehören.
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAll(Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Ruft die neue Service-Methode auf, um nach der Firmen-ID zu filtern
        List<Customer> customers = customerService.findAllByCompanyId(user.getCompany().getId());
        return ResponseEntity.ok(customers);
    }

    /**
     * Holt die zuletzt verwendeten Kunden für einen Benutzer. Diese Methode ist korrekt.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Customer>> getRecent(Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(timeTrackingService.getRecentCustomers(user.getUsername()));
    }

    /**
     * GEÄNDERT: Fügt eine Sicherheitsprüfung hinzu, um sicherzustellen, dass der Benutzer
     * nur Kunden seiner eigenen Firma abrufen kann.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id, Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return customerService.findById(id)
                .map(customer -> {
                    // Sicherheitsprüfung: Gehört der Kunde zur Firma des Benutzers?
                    if (!Objects.equals(customer.getCompany().getId(), user.getCompany().getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Customer>build();
                    }
                    return ResponseEntity.ok(customer);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GEÄNDERT: Stellt sicher, dass der neue Kunde der Firma des Erstellers zugeordnet wird.
     */
    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer customer, Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // WICHTIG: Setzt die Firma für den neuen Kunden
        customer.setCompany(user.getCompany());
        Customer savedCustomer = customerService.save(customer);
        return new ResponseEntity<>(savedCustomer, HttpStatus.CREATED);
    }

    /**
     * GEÄNDERT: Fügt eine Sicherheitsprüfung vor dem Update hinzu.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customerDetails, Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return customerService.findById(id)
                .map(existingCustomer -> {
                    // Sicherheitsprüfung: Darf der User diesen Kunden bearbeiten?
                    if (!Objects.equals(existingCustomer.getCompany().getId(), user.getCompany().getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Customer>build();
                    }
                    existingCustomer.setName(customerDetails.getName());
                    Customer updatedCustomer = customerService.save(existingCustomer);
                    return ResponseEntity.ok(updatedCustomer);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GEÄNDERT: Fügt eine Sicherheitsprüfung vor dem Löschen hinzu.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        User user = getPrincipalUser(principal);
        if (!featureEnabled(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return customerService.findById(id)
                .map(customer -> {
                    // Sicherheitsprüfung: Darf der User diesen Kunden löschen?
                    if (!Objects.equals(customer.getCompany().getId(), user.getCompany().getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build();
                    }
                    customerService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}