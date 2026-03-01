package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final int DEMO_DATA_REFRESH_INTERVAL_DAYS = 7;

    @Transactional
    public void resetDemoData(User user) {
        Company company = ensureCompany(user);

        Role userRole = getOrCreateRole("ROLE_USER");
        Role adminRole = getOrCreateRole("ROLE_ADMIN");

        LocalDate today = LocalDate.now();

        User demoAdmin = ensureTeamMember(
                company,
                user.getUsername(),
                "Demo",
                "Manager",
                "demo@chrono-demo.ch",
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
                "anna",
                "Anna",
                "Fischer",
                "anna.fischer@chrono-demo.ch",
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
                "ben",
                "Ben",
                "Keller",
                "ben.keller@chrono-demo.ch",
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
                "carla",
                "Carla",
                "Meier",
                "carla.meier@chrono-demo.ch",
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
                "david",
                "David",
                "Lenz",
                "david.lenz@chrono-demo.ch",
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
            correctionRequestRepository.deleteByUser(member);
            vacationRequestRepository.deleteByUser(member);
            timeTrackingEntryRepository.deleteByUser(member);
        });

        if (company.getId() != null) {
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

    private Company ensureCompany(User demoUser) {
        Company company = demoUser.getCompany();
        if (company == null) {
            company = new Company("Chrono Demo GmbH");
        }
        company.setName("Chrono Demo GmbH");
        company.setAddressLine1("Musterstrasse 5");
        company.setPostalCode("8000");
        company.setCity("Zürich");
        company.setPaid(true);
        company.setPaymentMethod("Invoice");
        company.setNotifyVacation(true);
        company.setNotifyOvertime(true);
        company.setCustomerTrackingEnabled(true);
        company.setCantonAbbreviation("ZH");
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

        return userRepository.save(member);
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
