package com.chrono.chrono.services;

import com.chrono.chrono.entities.PayrollConfigurationVersion;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.PayrollConfigurationRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
public class PayrollConfigurationService {
    private final PayrollConfigurationRepository versions;
    private final UserRepository users;

    public PayrollConfigurationService(PayrollConfigurationRepository versions, UserRepository users) {
        this.versions = versions;
        this.users = users;
    }

    public record InsuranceInput(LocalDate effectiveFrom, String source, BigDecimal uvgBuRate,
            BigDecimal uvgNbuRate, BigDecimal ktgRateEmployee, BigDecimal ktgRateEmployer, BigDecimal fakRate,
            BigDecimal fakEmployeeRate, BigDecimal ahvAdminRate, BigDecimal ktgAnnualSalaryCap) {}
    public record PensionInput(Long userId, LocalDate effectiveFrom, String source,
            BigDecimal bvgEmployee, BigDecimal bvgEmployer) {}
    public record Employee(Long id, String name) {}
    public record Configuration(List<PayrollConfigurationVersion> versions, List<Employee> employees) {}
    public record Coverage(PayrollConfigurationVersion insurance, PayrollConfigurationVersion pension,
            boolean insuranceChangesWithinPeriod, boolean pensionChangesWithinPeriod) {
        public boolean insuranceComplete() {
            return insurance != null && insurance.getUvgBuRate() != null && insurance.getUvgNbuRate() != null
                    && insurance.getKtgRateEmployee() != null && insurance.getKtgRateEmployer() != null
                    && insurance.getFakRate() != null && insurance.getFakEmployeeRate() != null
                    && insurance.getAhvAdminRate() != null && insurance.getKtgAnnualSalaryCap() != null;
        }
        public boolean pensionComplete() {
            return pension != null && pension.getBvgEmployee() != null && pension.getBvgEmployer() != null;
        }
    }

    @Transactional(readOnly = true)
    public Configuration list(Long companyId) {
        var employees = users.findByCompany_IdAndDeletedFalse(companyId).stream()
                .map(user -> new Employee(user.getId(), displayName(user))).toList();
        return new Configuration(versions.findByCompanyIdOrderByEffectiveFromDescIdDesc(companyId), employees);
    }

    @Transactional
    public PayrollConfigurationVersion addInsurance(Long companyId, Long actorId, InsuranceInput input) {
        if (input == null) throw bad("Versicherungswerte fehlen.");
        checkNumber(input.uvgBuRate(), BigDecimal.ONE, 8, "BU-Satz");
        checkNumber(input.uvgNbuRate(), BigDecimal.ONE, 8, "NBU-Satz");
        checkNumber(input.ktgRateEmployee(), BigDecimal.ONE, 8, "KTG Arbeitnehmer");
        checkNumber(input.ktgRateEmployer(), BigDecimal.ONE, 8, "KTG Arbeitgeber");
        checkNumber(input.fakRate(), BigDecimal.ONE, 8, "FAK-Satz");
        checkNumber(input.fakEmployeeRate(), BigDecimal.ONE, 8, "FAK Arbeitnehmer");
        checkNumber(input.ahvAdminRate(), BigDecimal.ONE, 8, "AHV-Verwaltungskosten");
        checkNumber(input.ktgAnnualSalaryCap(), new BigDecimal("9999999999.99"), 2, "KTG-Jahreslohngrenze");
        var version = base(companyId, actorId, input.effectiveFrom(), input.source());
        version.setUvgBuRate(input.uvgBuRate()); version.setUvgNbuRate(input.uvgNbuRate());
        version.setKtgRateEmployee(input.ktgRateEmployee()); version.setKtgRateEmployer(input.ktgRateEmployer());
        version.setFakRate(input.fakRate());
        version.setFakEmployeeRate(input.fakEmployeeRate()); version.setAhvAdminRate(input.ahvAdminRate());
        version.setKtgAnnualSalaryCap(input.ktgAnnualSalaryCap());
        return versions.save(version);
    }

    @Transactional
    public PayrollConfigurationVersion addPension(Long companyId, Long actorId, PensionInput input) {
        if (input == null || input.userId() == null) throw bad("Bitte Mitarbeitende auswählen.");
        // Lock and re-read membership so a concurrent company transfer cannot mix tenant records.
        User user = users.findByIdForUpdate(input.userId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeitende nicht gefunden."));
        if (user.getCompany() == null || !Objects.equals(user.getCompany().getId(), companyId) || user.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mitarbeitende nicht gefunden.");
        }
        checkNumber(input.bvgEmployee(), new BigDecimal("9999999999.99"), 2, "BVG Arbeitnehmer");
        checkNumber(input.bvgEmployer(), new BigDecimal("9999999999.99"), 2, "BVG Arbeitgeber");
        var version = base(companyId, actorId, input.effectiveFrom(), input.source());
        version.setUserId(user.getId()); version.setBvgEmployee(input.bvgEmployee()); version.setBvgEmployer(input.bvgEmployer());
        return versions.save(version);
    }

    @Transactional(readOnly = true)
    public Coverage resolve(User user, LocalDate start, LocalDate end) {
        PayrollReviewService.validatePeriod(start, end);
        if (user.getCompany() == null) return new Coverage(null, null, false, false);
        Long companyId = user.getCompany().getId();
        var insurance = versions.findFirstByCompanyIdAndUserIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(companyId, start).orElse(null);
        var insuranceEnd = versions.findFirstByCompanyIdAndUserIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(companyId, end).orElse(null);
        var pension = versions.findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(companyId, user.getId(), start).orElse(null);
        var pensionEnd = versions.findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(companyId, user.getId(), end).orElse(null);
        return new Coverage(insurance, pension, !sameVersion(insurance, insuranceEnd), !sameVersion(pension, pensionEnd));
    }

    private static boolean sameVersion(PayrollConfigurationVersion a, PayrollConfigurationVersion b) {
        return Objects.equals(a == null ? null : a.getId(), b == null ? null : b.getId());
    }
    private static PayrollConfigurationVersion base(Long companyId, Long actorId, LocalDate date, String source) {
        if (date == null || date.getYear() < 1900 || date.getYear() > 9999) throw bad("Ein gültiges Datum «Gültig ab» ist erforderlich.");
        if (source == null || source.isBlank() || source.strip().length() > 250) throw bad("Bitte eine Grundlage oder Begründung mit höchstens 250 Zeichen angeben.");
        var version = new PayrollConfigurationVersion();
        version.setCompanyId(companyId); version.setCreatedBy(actorId);
        version.setEffectiveFrom(date); version.setSource(source.strip()); version.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return version;
    }
    private static void checkNumber(BigDecimal value, BigDecimal max, int scale, String name) {
        if (value != null && (value.signum() < 0 || value.compareTo(max) > 0 || value.stripTrailingZeros().scale() > scale)) {
            throw bad(name + ": Wert ausserhalb des erlaubten Bereichs oder zu viele Nachkommastellen.");
        }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).strip();
        return name.isEmpty() ? user.getUsername() : name;
    }
}

