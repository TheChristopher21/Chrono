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
import com.chrono.chrono.services.PayrollOpeningService;
import com.chrono.chrono.entities.PayrollOpeningVersion;
import com.chrono.chrono.services.PayrollPeriodInputService;
import com.chrono.chrono.entities.PayrollPeriodInputVersion;
import com.chrono.chrono.services.PayrollOpeningCorrectionService;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/company/payroll-configuration")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
public class PayrollConfigurationController {
    private final UserRepository users;
    private final PayrollConfigurationService configuration;
    private final PayrollTermsService terms;
    private final PayrollOpeningService openings;
    private final PayrollPeriodInputService periodInputs;
    private final PayrollOpeningCorrectionService openingCorrections;
    public PayrollConfigurationController(UserRepository users, PayrollConfigurationService configuration, PayrollTermsService terms, PayrollOpeningService openings,PayrollPeriodInputService periodInputs,PayrollOpeningCorrectionService openingCorrections) {
        this.users = users; this.configuration = configuration; this.terms = terms; this.openings=openings; this.periodInputs=periodInputs;this.openingCorrections=openingCorrections;
    }
    @GetMapping("/openings")
    public List<PayrollOpeningVersion> openings(Principal principal) { return openings.list(companyId(actor(principal))); }
    @PostMapping("/openings")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollOpeningVersion addOpening(@RequestBody PayrollOpeningService.Input input, Principal principal) {
        User actor=actor(principal); return openings.add(companyId(actor),actor.getId(),input);
    }
    @PostMapping("/openings/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollOpeningCorrectionService.Result correctOpening(@RequestBody PayrollOpeningCorrectionService.Input input,Principal principal) {
        User actor=actor(principal);return openingCorrections.stage(companyId(actor),actor.getId(),input,actor.getUsername());
    }
    @GetMapping("/period-inputs")
    public List<PayrollPeriodInputVersion> periodInputs(Principal principal) {return periodInputs.list(companyId(actor(principal)));}
    @PostMapping("/period-inputs")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollPeriodInputVersion addPeriodInput(@RequestBody PayrollPeriodInputService.Input input,Principal principal) {
        User actor=actor(principal);return periodInputs.add(companyId(actor),actor.getId(),input);
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

