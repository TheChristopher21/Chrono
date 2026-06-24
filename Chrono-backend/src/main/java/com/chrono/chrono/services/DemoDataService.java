package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.entities.inventory.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.repositories.inventory.*;
import com.chrono.chrono.services.inventory.SupplyChainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DemoDataService {

    private static final String DEMO_DEFAULT_PASSWORD = "demo";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Autowired
    private VacationRequestRepository vacationRequestRepository;

    @Autowired
    private CorrectionRequestRepository correctionRequestRepository;

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired
    private ComplianceAuditLogRepository complianceAuditLogRepository;

    @Autowired
    private UserAuditRepository userAuditRepository;

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private ScheduleRuleRepository scheduleRuleRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private PayslipAuditRepository payslipAuditRepository;

    @Autowired
    private PayslipScheduleRepository payslipScheduleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private CycleCountRepository cycleCountRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private ProductionOrderRepository productionOrderRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private SupplyChainService supplyChainService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final int DEMO_DATA_REFRESH_INTERVAL_DAYS = 7;

    @Transactional
    public void resetDemoData(User user) {
        Company company = ensureCompany(user);
        String sessionSuffix = demoSessionSuffix(user);

        Role userRole = getOrCreateRole("ROLE_USER");
        Role adminRole = getOrCreateRole("ROLE_ADMIN");

        LocalDate today = LocalDate.now();

        User demoAdmin = ensureTeamMember(
                company,
                user.getUsername(),
                "Demo",
                "Manager",
                demoEmail("demo", sessionSuffix),
                "D-1000",
                "#6366F1",
                today.minusYears(3),
                30,
                8.5,
                5,
                135,
                false,
                false,
                100,
                null,
                6900.0,
                "Operations",
                userRole,
                adminRole,
                true
        );

        User anna = ensureTeamMember(
                company,
                demoUsername(user, "anna"),
                "Anna",
                "Fischer",
                demoEmail("anna.fischer", sessionSuffix),
                "D-1001",
                "#F97316",
                today.minusYears(2),
                28,
                8.0,
                5,
                90,
                false,
                false,
                100,
                null,
                6200.0,
                "Product Design",
                userRole,
                adminRole,
                false
        );

        User ben = ensureTeamMember(
                company,
                demoUsername(user, "ben"),
                "Ben",
                "Keller",
                demoEmail("ben.keller", sessionSuffix),
                "D-1002",
                "#10B981",
                today.minusYears(1).minusMonths(3),
                25,
                6.5,
                4,
                -45,
                false,
                true,
                80,
                null,
                4400.0,
                "Customer Success",
                userRole,
                adminRole,
                false
        );

        User carla = ensureTeamMember(
                company,
                demoUsername(user, "carla"),
                "Carla",
                "Meier",
                demoEmail("carla.meier", sessionSuffix),
                "D-1003",
                "#F59E0B",
                today.minusMonths(9),
                22,
                5.5,
                5,
                25,
                true,
                false,
                100,
                32.5,
                null,
                "Warehouse",
                userRole,
                adminRole,
                false
        );

        User david = ensureTeamMember(
                company,
                demoUsername(user, "david"),
                "David",
                "Lenz",
                demoEmail("david.lenz", sessionSuffix),
                "D-1004",
                "#3B82F6",
                today.minusYears(4),
                26,
                7.5,
                4,
                60,
                false,
                true,
                60,
                null,
                5100.0,
                "Engineering",
                userRole,
                adminRole,
                false
        );

        List<User> teamMembers = List.of(demoAdmin, anna, ben, carla, david);

        teamMembers.forEach(member -> {
            userAuditRepository.deleteByUser(member);
            deletePayrollData(member);
            scheduleEntryRepository.deleteByUser(member);
            correctionRequestRepository.deleteByUser(member);
            vacationRequestRepository.deleteByUser(member);
            sickLeaveRepository.deleteByUser(member);
            timeTrackingEntryRepository.deleteByUser(member);
        });

        if (company.getId() != null) {
            resetDemoSupplyChainData(company.getId());
            projectRepository.deleteByCustomerCompanyId(company.getId());
            customerRepository.deleteByCompanyId(company.getId());
        }

        Customer alpine = createCustomer(company, "Alpine Solutions GmbH");
        Customer nordwind = createCustomer(company, "Nordwind AG");
        Customer helvetic = createCustomer(company, "Helvetic Manufacturing AG");

        Project mobileApp = createProject(alpine, "Mobile App Pilot");
        Project serviceDesk = createProject(alpine, "Service & Wartung");
        Project website = createProject(nordwind, "Website Relaunch");
        Project erpRollout = createProject(helvetic, "ERP Einführung");

        TimeTrackingEntry[] demoDay1Morning = createWorkBlock(demoAdmin, mobileApp, today.minusDays(1), 8, 30, 240, "Onboarding Journeys vorbereiten");
        createWorkBlock(demoAdmin, website, today.minusDays(1), 13, 15, 210, "Sprint-Review koordinieren");
        createWorkBlock(demoAdmin, mobileApp, today.minusDays(2), 8, 45, 480, "Team Coaching & Strategie");
        createWorkBlock(demoAdmin, serviceDesk, today.minusDays(3), 9, 0, 360, "Supportkoordination");

        createWorkBlock(anna, mobileApp, today.minusDays(1), 9, 0, 300, "UX-Workshop mit Kunden");
        createWorkBlock(anna, serviceDesk, today.minusDays(1), 14, 0, 180, "Design QA & Komponentenpflege");
        TimeTrackingEntry[] annaPast = createWorkBlock(anna, website, today.minusDays(5), 9, 30, 420, "UI Komponenten Sprint");

        TimeTrackingEntry[] benBlock = createWorkBlock(ben, serviceDesk, today.minusDays(1), 7, 30, 330, "Frühschicht Hotline");
        createWorkBlock(ben, serviceDesk, today.minusDays(2), 7, 30, 300, "Frühschicht Hotline");
        createWorkBlock(ben, erpRollout, today.minusDays(3), 8, 0, 360, "ERP Datenmigration vorbereiten");

        createWorkBlock(carla, erpRollout, today.minusDays(1), 12, 0, 270, "Schicht Rückmeldung Lager");
        TimeTrackingEntry[] carlaPast = createWorkBlock(carla, erpRollout, today.minusDays(2), 12, 30, 240, "Inventur Vorbereitungen");

        createWorkBlock(david, mobileApp, today.minusDays(1), 10, 0, 360, "Backend Schnittstellen erweitern");
        createWorkBlock(david, website, today.minusDays(2), 10, 0, 360, "CMS Anpassungen");
        createWorkBlock(david, erpRollout, today.minusDays(4), 9, 30, 300, "Reporting Automatisierung");

        createCorrection(
                demoAdmin,
                today.minusDays(1),
                demoDay1Morning[0],
                demoDay1Morning[0].getEntryTimestamp().minusMinutes(15),
                TimeTrackingEntry.PunchType.START,
                "Kundencall begann früher als geplant.",
                false,
                false,
                null
        );

        createCorrection(
                anna,
                today.minusDays(5),
                annaPast[1],
                annaPast[1].getEntryTimestamp().plusMinutes(30),
                TimeTrackingEntry.PunchType.ENDE,
                "Abstimmung mit Entwicklung dauerte länger.",
                true,
                false,
                "Zeit angepasst, danke für die Info!"
        );

        createCorrection(
                ben,
                today.minusDays(2),
                benBlock[0],
                benBlock[0].getEntryTimestamp().minusMinutes(20),
                TimeTrackingEntry.PunchType.START,
                "Vorbereitung auf Hotline-Übergabe vergessen zu stempeln.",
                false,
                true,
                "Vorbereitung zählt leider nicht zur Arbeitszeit."
        );

        createCorrection(
                carla,
                today.minusDays(2),
                carlaPast[1],
                carlaPast[1].getEntryTimestamp().plusMinutes(15),
                TimeTrackingEntry.PunchType.ENDE,
                "Schicht konnte erst nach Übergabe beendet werden.",
                false,
                false,
                null
        );

        createVacation(demoAdmin, today.minusDays(20), today.minusDays(18), false, true, 240, true, false);
        createVacation(demoAdmin, today.plusDays(10), today.plusDays(12), false, false, null, false, false);

        createVacation(anna, today.minusDays(30), today.minusDays(27), false, false, null, true, false);
        createVacation(anna, today.plusDays(7), today.plusDays(9), false, false, null, false, false);

        createVacation(ben, today.minusDays(14), today.minusDays(12), true, true, 180, true, false);

        createVacation(carla, today.plusDays(1), today.plusDays(1), true, false, null, false, true);

        createVacation(david, today.plusDays(20), today.plusDays(24), false, false, null, false, false);

        seedDemoScheduleData(teamMembers, today);
        seedDemoPayrollData(teamMembers, today);
        seedDemoSupplyChainData(company, today);

        teamMembers.forEach(member ->
                member.setPagePermissions(userPermissionService.buildDefaultPagePermissions(member)));

        company.setDemoDataLastReset(today);
        companyRepository.save(company);
        userRepository.saveAll(teamMembers);
    }

    @Transactional
    public void refreshDemoDataIfOutdated(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        User managedUser = userRepository.findById(user.getId()).orElse(null);
        if (managedUser == null || !managedUser.isDemo()) {
            return;
        }

        Company company = managedUser.getCompany();
        LocalDate today = LocalDate.now();
        LocalDate lastReset = company != null ? company.getDemoDataLastReset() : null;

        if (lastReset == null || lastReset.isBefore(today.minusDays(DEMO_DATA_REFRESH_INTERVAL_DAYS - 1))) {
            resetDemoData(managedUser);
        }
    }

    @Transactional
    public int cleanupExpiredDemoTenants() {
        LocalDateTime now = LocalDateTime.now();
        List<Company> expiredDemoCompanies = companyRepository.findByDemoTrueAndDemoExpiresAtBefore(now);
        expiredDemoCompanies.forEach(this::deleteDemoCompany);
        return expiredDemoCompanies.size();
    }

    private void deleteDemoCompany(Company company) {
        if (company == null || company.getId() == null || !company.isDemo()) {
            return;
        }

        Long companyId = company.getId();
        List<User> teamMembers = userRepository.findByCompany_Id(companyId);
        resetDemoSupplyChainData(companyId);
        teamMembers.forEach(member -> {
            userAuditRepository.deleteByUser(member);
            deletePayrollData(member);
            scheduleEntryRepository.deleteByUser(member);
            correctionRequestRepository.deleteByUser(member);
            vacationRequestRepository.deleteByUser(member);
            sickLeaveRepository.deleteByUser(member);
            timeTrackingEntryRepository.deleteByUser(member);
            member.setLastCustomer(null);
            member.setCompany(null);
            userRepository.save(member);
        });

        complianceAuditLogRepository.deleteByCompanyId(companyId);
        projectRepository.deleteByCustomerCompanyId(companyId);
        customerRepository.deleteByCompanyId(companyId);
        userRepository.deleteAll(teamMembers);
        companyRepository.delete(company);
    }

    private Company ensureCompany(User demoUser) {
        Company company = demoUser.getCompany();
        if (company == null) {
            company = new Company("Chrono Demo GmbH");
        }
        company.setName(demoUser.isDemo() ? "Chrono Demo " + demoSessionSuffix(demoUser).toUpperCase() : "Chrono Demo GmbH");
        company.setAddressLine1("Musterstrasse 5");
        company.setPostalCode("8000");
        company.setCity("Zürich");
        company.setPaid(true);
        company.setPaymentMethod("Invoice");
        company.setNotifyVacation(true);
        company.setNotifyOvertime(true);
        company.setCustomerTrackingEnabled(true);
        company.setCantonAbbreviation("ZH");
        if (demoUser.isDemo()) {
            company.setDemo(true);
            company.setDemoSessionId(demoUser.getDemoSessionId());
            company.setDemoExpiresAt(demoUser.getDemoExpiresAt());
            company.setEnabledFeatures(new LinkedHashSet<>(
                    List.of("projects", "analytics", "supplyChain", "payroll", "roster", "accounting", "crm", "banking")));
        }
        Company saved = companyRepository.save(company);
        demoUser.setCompany(saved);
        return saved;
    }

    private Role getOrCreateRole(String name) {
        return roleRepository.findByRoleName(name).orElseGet(() -> roleRepository.save(new Role(name)));
    }

    private User ensureTeamMember(
            Company company,
            String username,
            String firstName,
            String lastName,
            String email,
            String personnelNumber,
            String color,
            LocalDate entryDate,
            int annualVacationDays,
            double dailyHours,
            int expectedWorkDays,
            int trackingBalanceMinutes,
            boolean hourly,
            boolean percentage,
            int workPercentage,
            Double hourlyRate,
            Double monthlySalary,
            String department,
            Role userRole,
            Role adminRole,
            boolean grantAdmin
    ) {
        User member = userRepository.findByUsername(username).orElseGet(() -> {
            User created = new User();
            created.setUsername(username);
            created.setPassword(passwordEncoder.encode(DEMO_DEFAULT_PASSWORD));
            created.setPersonnelNumber(personnelNumber);
            created.setCountry("DE");
            created.setDemo(true);
            return created;
        });

        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setEmail(email);
        member.setPersonnelNumber(personnelNumber);
        member.setDepartment(department);
        member.setCompany(company);
        member.setDemo(true);
        member.setDemoSessionId(company.getDemoSessionId());
        member.setDemoExpiresAt(company.getDemoExpiresAt());
        member.setIncludeInTimeTracking(true);
        member.setDeleted(false);
        member.setOptOut(false);
        member.setEntryDate(entryDate);
        member.setAnnualVacationDays(annualVacationDays);
        member.setDailyWorkHours(dailyHours);
        member.setExpectedWorkDays(expectedWorkDays);
        member.setBreakDuration(30);
        member.setColor(color);
        member.setIsHourly(hourly);
        member.setIsPercentage(percentage);
        member.setWorkPercentage(percentage ? workPercentage : 100);
        member.setHourlyRate(hourly ? hourlyRate : null);
        member.setMonthlySalary(!hourly ? monthlySalary : null);
        member.setTrackingBalanceInMinutes(trackingBalanceMinutes);
        member.setScheduleCycle(1);
        member.setScheduleEffectiveDate(entryDate);

        List<java.util.Map<String, Double>> weeklySchedule = new ArrayList<>();
        weeklySchedule.add(User.getDefaultWeeklyScheduleMap());
        member.setWeeklySchedule(weeklySchedule);

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        if (grantAdmin) {
            roles.add(adminRole);
        }
        member.setRoles(roles);
        member.setBankAccount("CH93 0076 2011 6238 5295 7");
        member.setSocialSecurityNumber("756.1234.5678.90");
        member.setPagePermissions(userPermissionService.buildDefaultPagePermissions(member));

        return userRepository.save(member);
    }

    private void deletePayrollData(User user) {
        payslipScheduleRepository.deleteByUser(user);
        List<Payslip> payslips = payslipRepository.findByUser(user);
        payslips.forEach(payslipAuditRepository::deleteByPayslip);
        payslipRepository.deleteAll(payslips);
    }

    private void resetDemoSupplyChainData(Long companyId) {
        if (companyId == null) {
            return;
        }

        cycleCountRepository.deleteAll(cycleCountRepository.findAllByCompany_Id(companyId));
        purchaseOrderRepository.deleteAll(purchaseOrderRepository.findAllByCompany_Id(companyId));
        salesOrderRepository.deleteAll(salesOrderRepository.findAllByCompany_Id(companyId));
        productionOrderRepository.deleteAll(productionOrderRepository.findAllByCompany_Id(companyId));
        serviceRequestRepository.deleteAll(serviceRequestRepository.findAllByCompany_Id(companyId));
        stockMovementRepository.deleteAll(stockMovementRepository.findAllByProduct_Company_Id(companyId));
        stockLevelRepository.deleteAll(stockLevelRepository.findAllByProduct_Company_Id(companyId));
        productRepository.deleteAll(productRepository.findAllByCompany_Id(companyId));
        warehouseRepository.deleteAll(warehouseRepository.findAllByCompany_Id(companyId));
    }

    private void seedDemoScheduleData(List<User> teamMembers, LocalDate today) {
        ensureShiftRule("EARLY", "Fruehschicht", "07:00", "15:00");
        ensureShiftRule("LATE", "Spaetschicht", "12:00", "20:00");
        ensureShiftRule("FULFILLMENT", "Fulfillment", "09:00", "17:30");

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        User demoAdmin = teamMembers.get(0);
        User anna = teamMembers.get(1);
        User ben = teamMembers.get(2);
        User carla = teamMembers.get(3);
        User david = teamMembers.get(4);

        createScheduleEntry(ben, weekStart, "EARLY", "Demo Hotline-Fruehstart");
        createScheduleEntry(carla, weekStart, "FULFILLMENT", "Wareneingang und Inventur");
        createScheduleEntry(david, weekStart, "LATE", "Release-Support");

        createScheduleEntry(demoAdmin, weekStart.plusDays(1), "FULFILLMENT", "Teamkoordination");
        createScheduleEntry(anna, weekStart.plusDays(1), "EARLY", "Kundenworkshop");
        createScheduleEntry(carla, weekStart.plusDays(1), "LATE", "Pick & Pack Abendslot");

        createScheduleEntry(ben, weekStart.plusDays(2), "EARLY", "Support Queue");
        createScheduleEntry(david, weekStart.plusDays(2), "FULFILLMENT", "ERP Datenabgleich");
        createScheduleEntry(anna, weekStart.plusDays(2), "LATE", "Design Review");

        createScheduleEntry(demoAdmin, weekStart.plusDays(3), "EARLY", "Operatives Standup");
        createScheduleEntry(carla, weekStart.plusDays(3), "FULFILLMENT", "Cycle Count");
        createScheduleEntry(david, weekStart.plusDays(3), "LATE", "Schnittstellen-Monitoring");

        createScheduleEntry(anna, weekStart.plusDays(4), "EARLY", "UX QA");
        createScheduleEntry(ben, weekStart.plusDays(4), "FULFILLMENT", "Customer Success Uebergabe");
        createScheduleEntry(carla, weekStart.plusDays(4), "LATE", "Speditionsfenster");
    }

    private void ensureShiftRule(String shiftKey, String label, String startTime, String endTime) {
        ScheduleRule rule = scheduleRuleRepository.findByShiftKey(shiftKey).orElseGet(ScheduleRule::new);
        if (rule.getId() == null) {
            rule.setShiftKey(shiftKey);
        }
        rule.setLabel(label);
        rule.setStartTime(startTime);
        rule.setEndTime(endTime);
        rule.setActive(true);
        scheduleRuleRepository.save(rule);
    }

    private ScheduleEntry createScheduleEntry(User user, LocalDate date, String shift, String note) {
        ScheduleEntry entry = new ScheduleEntry();
        entry.setUser(user);
        entry.setDate(date);
        entry.setShift(shift);
        entry.setNote(note);
        return scheduleEntryRepository.save(entry);
    }

    private void seedDemoPayrollData(List<User> teamMembers, LocalDate today) {
        LocalDate previousMonthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate previousMonthEnd = previousMonthStart.withDayOfMonth(previousMonthStart.lengthOfMonth());
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate currentMonthEnd = today.withDayOfMonth(today.lengthOfMonth());

        createDemoPayslip(teamMembers.get(0), previousMonthStart, previousMonthEnd,
                previousMonthEnd.plusDays(5), 6900.00, 1269.60, 1028.10,
                true, "salary", 0.0, false, "Monatslohn");
        createDemoPayslip(teamMembers.get(1), previousMonthStart, previousMonthEnd,
                previousMonthEnd.plusDays(5), 6200.00, 1128.40, 923.80,
                true, "salary", 0.0, false, "Monatslohn");
        createDemoPayslip(teamMembers.get(2), previousMonthStart, previousMonthEnd,
                previousMonthEnd.plusDays(5), 3520.00, 568.20, 512.50,
                true, "salary", 0.0, false, "Teilzeitlohn 80%");
        createDemoPayslip(teamMembers.get(3), previousMonthStart, previousMonthEnd,
                previousMonthEnd.plusDays(5), 3380.00, 524.90, 486.70,
                true, "hourly", 6.5, true, "Stundenlohn Lager");
        createDemoPayslip(teamMembers.get(4), previousMonthStart, previousMonthEnd,
                previousMonthEnd.plusDays(5), 3060.00, 481.60, 438.30,
                true, "salary", 0.0, false, "Teilzeitlohn 60%");

        createDemoPayslip(teamMembers.get(0), currentMonthStart, currentMonthEnd,
                currentMonthEnd.plusDays(5), 6900.00, 1269.60, 1028.10,
                false, "salary", 0.0, false, "Abrechnungslauf laufender Monat");
        createDemoPayslip(teamMembers.get(2), currentMonthStart, currentMonthEnd,
                currentMonthEnd.plusDays(5), 3520.00, 568.20, 512.50,
                false, "salary", 2.0, true, "Teilzeit mit Ueberstundenauszahlung");
        createDemoPayslip(teamMembers.get(3), currentMonthStart, currentMonthEnd,
                currentMonthEnd.plusDays(5), 3380.00, 524.90, 486.70,
                false, "hourly", 4.0, true, "Stundenlohn Vorschau");

        teamMembers.forEach(user -> {
            PayslipSchedule schedule = new PayslipSchedule();
            schedule.setUser(user);
            schedule.setDayOfMonth(25);
            LocalDate nextRun = today.withDayOfMonth(Math.min(25, today.lengthOfMonth()));
            if (!nextRun.isAfter(today)) {
                LocalDate nextMonth = today.plusMonths(1);
                nextRun = nextMonth.withDayOfMonth(Math.min(25, nextMonth.lengthOfMonth()));
            }
            schedule.setNextRun(nextRun);
            payslipScheduleRepository.save(schedule);
        });
    }

    private Payslip createDemoPayslip(User user,
                                      LocalDate periodStart,
                                      LocalDate periodEnd,
                                      LocalDate payoutDate,
                                      double gross,
                                      double deductions,
                                      double employerContributions,
                                      boolean approved,
                                      String payType,
                                      double overtimeHours,
                                      boolean payoutOvertime,
                                      String baseComponentName) {
        Payslip payslip = new Payslip();
        payslip.setUser(user);
        payslip.setPeriodStart(periodStart);
        payslip.setPeriodEnd(periodEnd);
        payslip.setPayoutDate(payoutDate);
        payslip.setGrossSalary(gross);
        payslip.setDeductions(deductions);
        payslip.setNetSalary(roundCurrency(gross - deductions));
        payslip.setAllowances(0.0);
        payslip.setBonuses(overtimeHours > 0 ? roundCurrency(overtimeHours * 12.5) : 0.0);
        payslip.setOneTimePayments(0.0);
        payslip.setTaxFreeAllowances(0.0);
        payslip.setEmployerContributions(employerContributions);
        payslip.setBankAccount(user.getBankAccount());
        payslip.setSocialSecurityNumber(user.getSocialSecurityNumber());
        payslip.setPayType(payType);
        payslip.setApproved(approved);
        payslip.setLocked(approved);
        payslip.setPayoutOvertime(payoutOvertime);
        payslip.setOvertimeHours(overtimeHours > 0 ? overtimeHours : null);

        double baseAmount = payoutOvertime && overtimeHours > 0
                ? roundCurrency(gross - overtimeHours * 12.5)
                : gross;
        payslip.getEarnings().add(new PayComponent(baseComponentName, baseAmount));
        if (payoutOvertime && overtimeHours > 0) {
            payslip.getEarnings().add(new PayComponent("Ueberstundenauszahlung", roundCurrency(overtimeHours * 12.5)));
        }
        payslip.getDeductionsList().add(new PayComponent("AHV/IV/EO", roundCurrency(gross * 0.053)));
        payslip.getDeductionsList().add(new PayComponent("ALV", roundCurrency(gross * 0.011)));
        payslip.getDeductionsList().add(new PayComponent("BVG", roundCurrency(deductions - gross * 0.064)));
        payslip.getEmployerContribList().add(new PayComponent("AG Sozialbeitraege", employerContributions));

        Payslip saved = payslipRepository.save(payslip);
        payslipAuditRepository.save(new PayslipAudit(
                saved,
                approved ? "APPROVED" : "GENERATED",
                "Demo Manager",
                approved ? "Demo-Freigabe" : "Demo-Beispieldaten"
        ));
        return saved;
    }

    private double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void seedDemoSupplyChainData(Company company, LocalDate today) {
        Warehouse main = createWarehouse(company, "ZRH-MAIN", "Hauptlager Zuerich", "Zuerich Altstetten, Zone A");
        Warehouse service = createWarehouse(company, "ZRH-SVC", "Service Hub", "Zuerich Oerlikon, Retouren");

        Product headset = createProduct(company, "SC-HEADSET-01", "Service Headset Pro",
                "Bluetooth Headset fuer Support-Teams", "pcs", "72.00", "129.00");
        Product sensor = createProduct(company, "SC-SENSOR-07", "IoT Temperatur Sensor",
                "Sensor fuer Kuehlketten- und Lagerueberwachung", "pcs", "48.50", "89.00");
        Product kit = createProduct(company, "SC-KIT-24", "Field Service Kit",
                "Vorkommissioniertes Wartungsset fuer Aussendienst", "set", "118.00", "219.00");

        supplyChainService.adjustStock(headset, main, bd("42"), StockMovementType.RECEIPT,
                "DEMO-INIT-HEADSET", "LOT-HS-" + today.getYear(), null, today.plusMonths(18));
        supplyChainService.adjustStock(sensor, main, bd("18"), StockMovementType.RECEIPT,
                "DEMO-INIT-SENSOR", "LOT-SN-" + today.getYear(), null, today.plusMonths(12));
        supplyChainService.adjustStock(kit, service, bd("9"), StockMovementType.RECEIPT,
                "DEMO-INIT-KIT", "LOT-KIT-" + today.getYear(), null, today.plusMonths(9));
        supplyChainService.adjustStock(sensor, service, bd("-3"), StockMovementType.ISSUE,
                "DEMO-SO-1007", "LOT-SN-" + today.getYear(), null, today.plusMonths(12));

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setOrderNumber("DEMO-PO-" + today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        purchaseOrder.setVendorName("Helvetic Components AG");
        purchaseOrder.setOrderDate(today.minusDays(2));
        purchaseOrder.setExpectedDate(today.plusDays(4));
        purchaseOrder.setStatus(PurchaseOrderStatus.APPROVED);
        purchaseOrder.setLines(new ArrayList<>(List.of(
                purchaseLine(headset, "24", "72.00"),
                purchaseLine(sensor, "36", "48.50")
        )));
        supplyChainService.createPurchaseOrder(purchaseOrder, company);

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setOrderNumber("DEMO-SO-" + today.minusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        salesOrder.setCustomerName("Alpine Solutions GmbH");
        salesOrder.setOrderDate(today.minusDays(1));
        salesOrder.setDueDate(today.plusDays(2));
        salesOrder.setStatus(SalesOrderStatus.CONFIRMED);
        salesOrder.setLines(new ArrayList<>(List.of(
                salesLine(headset, "6", "129.00"),
                salesLine(kit, "2", "219.00")
        )));
        supplyChainService.createSalesOrder(salesOrder, company);

        ProductionOrder productionOrder = new ProductionOrder();
        productionOrder.setOrderNumber("DEMO-MO-" + today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        productionOrder.setProduct(kit);
        productionOrder.setQuantity(bd("8"));
        productionOrder.setStatus(ProductionOrderStatus.IN_PROGRESS);
        productionOrder.setStartDate(today.minusDays(1));
        supplyChainService.saveProductionOrder(productionOrder, company);

        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("Nordwind AG");
        request.setSubject("Sensoren fuer Pilotstandort kalibrieren");
        request.setDescription("Demo-Ticket: Techniker prueft Sensorwerte und ersetzt zwei Geraete im Servicefenster.");
        request.setOpenedDate(today.minusDays(3));
        request.setStatus(ServiceRequestStatus.IN_PROGRESS);
        supplyChainService.logServiceRequest(request, company);

        ServiceRequest resolvedRequest = new ServiceRequest();
        resolvedRequest.setCustomerName("Helvetic Manufacturing AG");
        resolvedRequest.setSubject("Ersatzteile ausgeliefert");
        resolvedRequest.setDescription("Demo-Ticket: Field Service Kit kommissioniert und an Standort Basel geliefert.");
        resolvedRequest.setOpenedDate(today.minusDays(12));
        resolvedRequest.setClosedDate(today.minusDays(8));
        resolvedRequest.setStatus(ServiceRequestStatus.RESOLVED);
        supplyChainService.logServiceRequest(resolvedRequest, company);

        CycleCount cycleCount = supplyChainService.createCycleCount(company.getId(), sensor, main, "Demo Manager");
        supplyChainService.submitCycleCount(company.getId(), cycleCount.getId(), bd("16"), "Carla Meier");
    }

    private Product createProduct(Company company,
                                  String sku,
                                  String name,
                                  String description,
                                  String unitOfMeasure,
                                  String unitCost,
                                  String unitPrice) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setUnitOfMeasure(unitOfMeasure);
        product.setUnitCost(bd(unitCost));
        product.setUnitPrice(bd(unitPrice));
        return supplyChainService.saveProduct(product, company);
    }

    private Warehouse createWarehouse(Company company, String code, String name, String location) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setLocation(location);
        return supplyChainService.saveWarehouse(warehouse, company);
    }

    private PurchaseOrderLine purchaseLine(Product product, String quantity, String unitCost) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(product);
        line.setQuantity(bd(quantity));
        line.setUnitCost(bd(unitCost));
        return line;
    }

    private SalesOrderLine salesLine(Product product, String quantity, String unitPrice) {
        SalesOrderLine line = new SalesOrderLine();
        line.setProduct(product);
        line.setQuantity(bd(quantity));
        line.setUnitPrice(bd(unitPrice));
        return line;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private String demoSessionSuffix(User user) {
        String sessionId = user != null ? user.getDemoSessionId() : null;
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.substring(0, Math.min(12, sessionId.length())).toLowerCase();
        }
        String username = user != null ? user.getUsername() : null;
        if (username != null && username.startsWith("demo_") && username.length() > 5) {
            return username.substring(5, Math.min(username.length(), 17)).toLowerCase();
        }
        return "shared";
    }

    private String demoUsername(User rootUser, String name) {
        String rootUsername = rootUser != null ? rootUser.getUsername() : null;
        if (rootUsername != null && rootUsername.startsWith("demo_")) {
            return rootUsername + "_" + name;
        }
        return name;
    }

    private String demoEmail(String localPart, String sessionSuffix) {
        return localPart + "+" + sessionSuffix + "@chrono-demo.ch";
    }

    private Customer createCustomer(Company company, String name) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setCompany(company);
        return customerRepository.save(customer);
    }

    private Project createProject(Customer customer, String name) {
        Project project = new Project();
        project.setName(name);
        project.setCustomer(customer);
        return projectRepository.save(project);
    }

    private TimeTrackingEntry[] createWorkBlock(User user, Project project, LocalDate day, int startHour, int startMinute, int durationMinutes, String description) {
        LocalDateTime start = day.atTime(startHour, startMinute);
        LocalDateTime end = start.plusMinutes(durationMinutes);

        TimeTrackingEntry startEntry = new TimeTrackingEntry(user, project.getCustomer(), project, start, TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        startEntry.setDescription(description + " (Start)");
        startEntry.setApproved(true);
        timeTrackingEntryRepository.save(startEntry);

        TimeTrackingEntry endEntry = new TimeTrackingEntry(user, project.getCustomer(), project, end, TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        endEntry.setDurationMinutes(durationMinutes);
        endEntry.setDescription(description + " (Ende)");
        endEntry.setApproved(true);
        timeTrackingEntryRepository.save(endEntry);

        return new TimeTrackingEntry[]{startEntry, endEntry};
    }

    private CorrectionRequest createCorrection(
            User user,
            LocalDate requestDate,
            TimeTrackingEntry targetEntry,
            LocalDateTime desiredTimestamp,
            TimeTrackingEntry.PunchType desiredPunchType,
            String reason,
            boolean approved,
            boolean denied,
            String adminComment
    ) {
        CorrectionRequest request = new CorrectionRequest();
        request.setUser(user);
        request.setRequestDate(requestDate);
        request.setTargetEntry(targetEntry);
        request.setDesiredTimestamp(desiredTimestamp);
        request.setDesiredPunchType(desiredPunchType);
        request.setReason(reason);
        request.setApproved(approved);
        request.setDenied(denied);
        request.setAdminComment(adminComment);
        return correctionRequestRepository.save(request);
    }

    private VacationRequest createVacation(
            User user,
            LocalDate start,
            LocalDate end,
            boolean halfDay,
            boolean usesOvertime,
            Integer overtimeMinutes,
            boolean approved,
            boolean denied
    ) {
        VacationRequest request = new VacationRequest();
        request.setUser(user);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setHalfDay(halfDay);
        request.setUsesOvertime(usesOvertime);
        request.setApproved(approved);
        request.setDenied(denied);
        request.setCompanyVacation(false);
        request.setOvertimeDeductionMinutes(overtimeMinutes);
        return vacationRequestRepository.save(request);
    }
}
