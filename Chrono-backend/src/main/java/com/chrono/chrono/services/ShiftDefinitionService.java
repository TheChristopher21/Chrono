package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.security.CompanyScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ShiftDefinitionService {
    private final ScheduleRuleRepository repository;

    public ShiftDefinitionService(ScheduleRuleRepository repository) { this.repository = repository; }

    public Company requireActorCompany(User actor) {
        Company company = CompanyScope.companyFor(actor);
        if (company == null || company.getId() == null) {
            throw new AccessDeniedException("Select a company before managing shift definitions");
        }
        return company;
    }

    @Transactional(readOnly = true)
    public List<ScheduleRule> effectiveRules(Company company, boolean activeOnly) {
        requireCompany(company);
        Map<String, ScheduleRule> effective = new LinkedHashMap<>();
        repository.findByCompanyIsNullOrderByStartTime().forEach(rule -> effective.put(rule.getShiftKey(), rule));
        repository.findByCompany_IdOrderByStartTime(company.getId()).forEach(rule -> effective.put(rule.getShiftKey(), rule));
        return effective.values().stream().filter(rule -> !rule.isSuppressed())
                .filter(rule -> !activeOnly || rule.getIsActive())
                .sorted(Comparator.comparing(ScheduleRule::getStartTime).thenComparing(ScheduleRule::getShiftKey)).toList();
    }

    /** Includes retired definitions so existing scheduled entries retain their duration. */
    @Transactional(readOnly = true)
    public Optional<ScheduleRule> resolveForUser(User target, String key) {
        if (target == null || target.getCompany() == null || target.getCompany().getId() == null) {
            return Optional.empty();
        }
        return resolve(target.getCompany(), key);
    }

    private Optional<ScheduleRule> resolve(Company company, String key) {
        return repository.findByCompany_IdAndShiftKey(company.getId(), key)
                .or(() -> repository.findByCompanyIsNullAndShiftKey(key));
    }

    public boolean isAssignable(User target, String key) {
        return resolveForUser(target, key).filter(rule -> !rule.isSuppressed()).isPresent();
    }

    @Transactional
    public ScheduleRule save(Company company, ScheduleRule input) {
        requireCompany(company);
        if (input == null) throw badRequest("Shift definition is required");
        ScheduleRule source = input.getId() == null ? null : visibleById(company, input.getId());
        String key = input.getShiftKey();
        if ((key == null || key.isBlank()) && source != null) key = source.getShiftKey();
        if (key == null || key.isBlank() || key.length() > 255 || input.getLabel() == null || input.getLabel().isBlank()) {
            throw badRequest("Shift key and label are required");
        }
        boolean renamed = source != null && !Objects.equals(source.getShiftKey(), key);
        if (renamed && resolve(company, key).filter(rule -> !rule.isSuppressed()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A definition with this shift key already exists for this company");
        }
        try {
            LocalTime.parse(input.getStartTime());
            LocalTime.parse(input.getEndTime());
        } catch (RuntimeException ex) {
            throw badRequest("Shift start and end must be valid times");
        }
        ScheduleRule owned = repository.findByCompany_IdAndShiftKey(company.getId(), key).orElseGet(ScheduleRule::new);
        owned.setCompany(company);
        owned.setShiftKey(key);
        owned.setLabel(input.getLabel());
        owned.setStartTime(input.getStartTime());
        owned.setEndTime(input.getEndTime());
        owned.setActive(input.getIsActive());
        owned.setSuppressed(false);
        ScheduleRule saved = repository.save(owned);
        // Existing entries reference the old key. Retain its definition privately
        // while publishing the new key, without renaming any shared/default row.
        if (renamed) delete(company, source.getId());
        return saved;
    }

    @Transactional
    public void delete(Company company, Long id) {
        requireCompany(company);
        ScheduleRule source = visibleById(company, id);
        // Keep an owned tombstone (including its times) instead of exposing the default again.
        ScheduleRule owned = repository.findByCompany_IdAndShiftKey(company.getId(), source.getShiftKey())
                .orElseGet(() -> copyForCompany(source, company));
        owned.setSuppressed(true);
        owned.setActive(false);
        repository.save(owned);
    }

    private ScheduleRule visibleById(Company company, Long id) {
        return repository.findById(id).filter(rule -> rule.getCompany() == null
                        || Objects.equals(company.getId(), rule.getCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift definition not found"));
    }

    private ScheduleRule copyForCompany(ScheduleRule source, Company company) {
        ScheduleRule copy = new ScheduleRule();
        copy.setCompany(company);
        copy.setShiftKey(source.getShiftKey());
        copy.setLabel(source.getLabel());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setActive(source.getIsActive());
        return copy;
    }

    private void requireCompany(Company company) {
        if (company == null || company.getId() == null) throw new AccessDeniedException("Company scope required");
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
