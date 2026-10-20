package com.chrono.chrono.controller;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.PayrollConfigurationVersion;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.security.CompanyScope;
import com.chrono.chrono.services.PayrollConfigurationService;
import com.chrono.chrono.services.PayrollTermsService;
import com.chrono.chrono.entities.PayrollTermsVersion;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/company/payroll-configuration")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
public class PayrollConfigurationController {
    private final UserRepository users;
    private final PayrollConfigurationService configuration;
    private final PayrollTermsService terms;
    public PayrollConfigurationController(UserRepository users, PayrollConfigurationService configuration, PayrollTermsService terms) {
        this.users = users; this.configuration = configuration; this.terms = terms;
    }
    @GetMapping("/terms")
    public List<PayrollTermsVersion> terms(Principal principal) { return terms.list(companyId(actor(principal))); }
    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollTermsVersion addTerms(@RequestBody PayrollTermsService.Input input, Principal principal) {
        User actor = actor(principal); return terms.add(companyId(actor), actor.getId(), input);
    }
    @GetMapping
    public PayrollConfigurationService.Configuration list(Principal principal) {
        User actor = actor(principal);
        return configuration.list(companyId(actor));
    }
    @PostMapping("/insurance")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollConfigurationVersion insurance(@RequestBody PayrollConfigurationService.InsuranceInput input, Principal principal) {
        User actor = actor(principal);
        return configuration.addInsurance(companyId(actor), actor.getId(), input);
    }
    @PostMapping("/pension")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollConfigurationVersion pension(@RequestBody PayrollConfigurationService.PensionInput input, Principal principal) {
        User actor = actor(principal);
        return configuration.addPension(companyId(actor), actor.getId(), input);
    }
    private User actor(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByUsername(principal.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private Long companyId(User actor) {
        var company = CompanyScope.companyFor(actor);
        if (company == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Firma auswählen.");
        return company.getId();
    }
}

