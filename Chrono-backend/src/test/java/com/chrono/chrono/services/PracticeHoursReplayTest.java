package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline replay of a minimal, pseudonymized export. Every repository is an in-memory mock;
 * no Spring application, datasource, mailer, scheduler or network client is started. */
@EnabledIfSystemProperty(named = "chrono.audit.hours.input", matches = ".+")
class PracticeHoursReplayTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final Map<Long, User> users = new LinkedHashMap<>();
    JsonNode input;
    LocalDate cutoff;
    User user(JsonNode n) { return users.get(n.get("userId").asLong()); }
    static LocalDate date(JsonNode n, String field) { return n.path(field).isNull() || n.path(field).isMissingNode() ? null : LocalDate.parse(n.get(field).asText()); }
    static Integer number(JsonNode n, String field) { return n.path(field).isNull() || n.path(field).isMissingNode() ? null : n.get(field).asInt(); }
    static Double decimal(JsonNode n, String field) { return n.path(field).isNull() || n.path(field).isMissingNode() ? null : n.get(field).asDouble(); }
    static boolean flag(JsonNode n, String field) { return n.path(field).asInt() == 1; }
    <T> List<T> rows(String name, Function<JsonNode, T> mapper) {
        List<T> result = new ArrayList<>(); input.path(name).forEach(n -> result.add(mapper.apply(n))); return result;
    }
    static <T> List<T> owned(List<T> values, Function<T, User> owner, User user) {
        return values.stream().filter(v -> owner.apply(v).getId().equals(user.getId())).toList();
    }
    List<Map<String, Double>> schedule(JsonNode n) {
        try { return n.path("schedule").isNull() ? List.of() : json.readValue(n.get("schedule").asText(), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid saved work schedule", e); }
    }
    @Test void replayActualServicesWithoutAnyDatabaseConnection() throws Exception {
        Path file = Path.of(System.getProperty("chrono.audit.hours.input")).toAbsolutePath();
        input = json.readTree(Files.readString(file).replace("\uFEFF", ""));
        cutoff = LocalDate.parse(input.path("cutoff").asText());
        var company = new Company(); company.setId(input.path("companyId").asLong());
        company.setName("Offline comparison"); company.setCantonAbbreviation(input.path("company").path("canton").asText());
        company.setCustomHolidaySelectionEnabled(flag(input.path("company"), "customHolidays"));
        for (JsonNode n : input.path("users")) {
            var u = new User(); u.setId(n.get("id").asLong()); u.setUsername("account-" + u.getId()); u.setCompany(company);
            u.setIsHourly(flag(n,"hourly")); u.setIsPercentage(flag(n,"percentage")); u.setWorkPercentage(number(n,"percent"));
            u.setExpectedWorkDays(number(n,"days")); u.setDailyWorkHours(decimal(n,"dailyHours")); u.setEntryDate(date(n,"entry"));
            u.setScheduleCycle(number(n,"cycle")); u.setWeeklySchedule(schedule(n)); u.setScheduleEffectiveDate(date(n,"scheduleFrom"));
            u.setTrackingBalanceInMinutes(number(n,"balance")); users.put(u.getId(),u);
        }
        var entries = rows("entries", n -> { var e = new TimeTrackingEntry(); e.setId(n.get("id").asLong()); e.setUser(user(n));
            e.setEntryTimestamp(LocalDateTime.parse(n.get("at").asText().replace(' ','T'))); e.setPunchType(TimeTrackingEntry.PunchType.valueOf(n.get("type").asText()));
            if (!n.path("source").isNull()) e.setSource(TimeTrackingEntry.PunchSource.valueOf(n.get("source").asText()));
            e.setCorrectedByUser(flag(n,"corrected")); return e; });
        entries.sort(Comparator.comparing(TimeTrackingEntry::getEntryTimestamp).thenComparing(TimeTrackingEntry::getId));
        var slips = rows("payslips", n -> { var p = new Payslip(); p.setId(n.get("id").asLong()); p.setUser(user(n)); p.setApproved(flag(n,"approved"));
            p.setPeriodStart(date(n,"start")); p.setPeriodEnd(date(n,"end")); p.setPayoutDate(date(n,"payoutDate")); p.setPayoutOvertime(flag(n,"payout")); p.setOvertimeHours(decimal(n,"hours")); return p; });
        var history = rows("history", n -> { var h = new UserEmploymentModelHistory(); h.setId(n.get("id").asLong()); h.setUser(user(n)); h.setEffectiveFrom(date(n,"from"));
            h.setModelType(EmploymentModelType.valueOf(n.get("type").asText())); h.setWorkPercentage(number(n,"percent")); h.setExpectedWorkDays(number(n,"days"));
            h.setDailyWorkHours(decimal(n,"dailyHours")); h.setScheduleCycle(number(n,"cycle")); h.setWeeklySchedule(schedule(n)); h.setScheduleEffectiveDate(date(n,"scheduleFrom")); return h; });
        history.sort(Comparator.comparing(UserEmploymentModelHistory::getEffectiveFrom));
        var vacations = rows("vacations", n -> { var v = new VacationRequest(); v.setId(n.get("id").asLong()); v.setUser(user(n)); v.setStartDate(date(n,"start")); v.setEndDate(date(n,"end"));
            v.setApproved(flag(n,"approved")); v.setHalfDay(flag(n,"half")); v.setUsesOvertime(flag(n,"overtime")); v.setOvertimeDeductionMinutes(number(n,"minutes")); return v; });
        var sickness = rows("sickness", n -> { var s = new SickLeave(); s.setId(n.get("id").asLong()); s.setUser(user(n)); s.setStartDate(date(n,"start")); s.setEndDate(date(n,"end")); s.setHalfDay(flag(n,"half")); return s; });
        var rules = rows("rules", n -> { var r = new UserScheduleRule(); r.setId(n.get("id").asLong()); r.setUser(user(n)); r.setRuleType(n.path("type").asText(null));
            r.setStartDate(date(n,"start")); r.setRepeatIntervalDays(number(n,"interval")); r.setDayOfWeek(number(n,"weekday")); r.setDayMode(n.path("mode").asText(null)); return r; });
        var options = rows("holidayOptions", n -> { var h = new UserHolidayOption(); h.setId(n.get("id").asLong()); h.setUser(user(n)); h.setHolidayDate(date(n,"date"));
            h.setHolidayHandlingOption(UserHolidayOption.HolidayHandlingOption.valueOf(n.get("option").asText())); return h; });
        var holidays = rows("holidays", n -> { var h = new CompanyHolidayPreference(); h.setId(n.get("id").asLong()); h.setCompany(company); h.setHolidayCode(n.get("code").asText()); h.setHalfDay(flag(n,"half")); return h; });
        var swaps = rows("swaps", n -> { var s = new WorkdaySwap(); s.setId(n.get("id").asLong()); s.setUser(user(n)); s.setOriginalWorkDate(date(n,"original")); s.setReplacementWorkDate(date(n,"replacement")); s.setTransferredMinutes(number(n,"minutes")); return s; });

        var userRepo = mock(UserRepository.class); when(userRepo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(users.get(i.getArgument(0))));
        when(userRepo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        var entryRepo = mock(TimeTrackingEntryRepository.class);
        when(entryRepo.findByUserOrderByEntryTimestampAsc(any())).thenAnswer(i -> owned(entries,TimeTrackingEntry::getUser,i.getArgument(0)));
        when(entryRepo.findByUserOrderByEntryTimestampDesc(any())).thenAnswer(i -> { var list = new ArrayList<>(owned(entries,TimeTrackingEntry::getUser,i.getArgument(0))); Collections.reverse(list); return list; });
        when(entryRepo.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(any(),any(),any())).thenAnswer(i -> owned(entries,TimeTrackingEntry::getUser,i.getArgument(0)).stream()
                .filter(e -> !e.getEntryTimestamp().isBefore(i.getArgument(1)) && e.getEntryTimestamp().isBefore(i.getArgument(2))).toList());
        var slipRepo = mock(PayslipRepository.class); when(slipRepo.findByUser(any())).thenAnswer(i -> owned(slips,Payslip::getUser,i.getArgument(0)));
        when(slipRepo.findById(anyLong())).thenAnswer(i -> slips.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        var vacationRepo = mock(VacationRequestRepository.class); when(vacationRepo.findByUserAndApprovedTrue(any())).thenAnswer(i -> owned(vacations,VacationRequest::getUser,i.getArgument(0)).stream().filter(VacationRequest::isApproved).toList());
        var sickRepo = mock(SickLeaveRepository.class); when(sickRepo.findByUser(any())).thenAnswer(i -> owned(sickness,SickLeave::getUser,i.getArgument(0)));
        var ruleRepo = mock(UserScheduleRuleRepository.class); when(ruleRepo.findByUser(any())).thenAnswer(i -> owned(rules,UserScheduleRule::getUser,i.getArgument(0)));
        var historyRepo = mock(UserEmploymentModelHistoryRepository.class); when(historyRepo.findByUserOrderByEffectiveFromAsc(any())).thenAnswer(i -> owned(history,UserEmploymentModelHistory::getUser,i.getArgument(0)));
        when(historyRepo.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(),any())).thenAnswer(i -> owned(history,UserEmploymentModelHistory::getUser,i.getArgument(0)).stream().filter(h -> !h.getEffectiveFrom().isAfter(i.getArgument(1))).max(Comparator.comparing(UserEmploymentModelHistory::getEffectiveFrom)));
        when(historyRepo.findFirstByUserOrderByEffectiveFromAsc(any())).thenAnswer(i -> owned(history,UserEmploymentModelHistory::getUser,i.getArgument(0)).stream().findFirst());
        var holidayRepo = mock(CompanyHolidayPreferenceRepository.class); when(holidayRepo.findByCompany_Id(company.getId())).thenReturn(holidays);
        var optionRepo = mock(UserHolidayOptionRepository.class); when(optionRepo.findByUserAndHolidayDateBetween(any(),any(),any())).thenAnswer(i -> owned(options,UserHolidayOption::getUser,i.getArgument(0)).stream().filter(h -> !h.getHolidayDate().isBefore(i.getArgument(1)) && !h.getHolidayDate().isAfter(i.getArgument(2))).toList());
        var swapRepo = mock(WorkdaySwapRepository.class); when(swapRepo.findByUserAndDateRange(any(),any(),any())).thenAnswer(i -> owned(swaps,WorkdaySwap::getUser,i.getArgument(0)));
        var holidayService = new HolidayService(); ReflectionTestUtils.setField(holidayService,"companyHolidayPreferenceRepository",holidayRepo);
        var work = new WorkScheduleService(); ReflectionTestUtils.setField(work,"ruleRepo",ruleRepo); ReflectionTestUtils.setField(work,"holidayService",holidayService);
        ReflectionTestUtils.setField(work,"sickLeaveRepository",sickRepo); ReflectionTestUtils.setField(work,"userHolidayOptionRepository",optionRepo); ReflectionTestUtils.setField(work,"workdaySwapRepository",swapRepo);
        var employment = spy(new EmploymentModelHistoryService(historyRepo)); doReturn(cutoff).when(employment).currentBerlinDate();
        // An optional locally compiled production revision permits an identical-input comparison.
        // The class must extend the service; neither replay starts a Spring application.
        var trackingType = Class.forName(System.getProperty("chrono.audit.tracking.class", TimeTrackingService.class.getName()))
                .asSubclass(TimeTrackingService.class);
        var tracking = spy(trackingType.getDeclaredConstructor().newInstance()); doReturn(cutoff).when(tracking).getCurrentBerlinDate();
        Map.of("timeTrackingEntryRepository",entryRepo,"workScheduleService",work,"userRepository",userRepo,"vacationRequestRepository",vacationRepo,
                "sickLeaveRepository",sickRepo,"payslipRepository",slipRepo,"employmentModelHistoryService",employment,"dailyNoteRepository",mock(DailyNoteRepository.class))
                .forEach((name,value) -> ReflectionTestUtils.setField(tracking,name,value));

        List<Map<String,Object>> report = new ArrayList<>();
        for (JsonNode n : input.path("users")) {
            User u = users.get(n.get("id").asLong()); int saved = Objects.requireNonNullElse(u.getTrackingBalanceInMinutes(),0);
            tracking.rebuildUserBalance(u);
            var ownEntries = owned(entries,TimeTrackingEntry::getUser,u);
            LocalDate first = ownEntries.stream().map(TimeTrackingEntry::getEntryDate).min(LocalDate::compareTo).orElse(cutoff);
            List<DailyTimeSummaryDTO> daily = new ArrayList<>();
            for (LocalDate from=first; !from.isAfter(cutoff); from=from.plusDays(366)) {
                LocalDate until=from.plusDays(365).isBefore(cutoff)?from.plusDays(365):cutoff;
                daily.addAll(tracking.getUserHistory(u,from,until));
            }
            Map<String,Object> row = new LinkedHashMap<>(); row.put("userId",u.getId()); row.put("deleted",flag(n,"deleted")); row.put("hourly",u.getIsHourly()); row.put("percentage",u.getIsPercentage());
            row.put("storedMinutes",saved); row.put("recomputedMinutes",u.getTrackingBalanceInMinutes()); row.put("differenceMinutes",u.getTrackingBalanceInMinutes()-saved);
            row.put("workedMinutes",daily.stream().mapToInt(DailyTimeSummaryDTO::getWorkedMinutes).sum());
            row.put("expectedMinutes",daily.stream().mapToInt(d -> Objects.requireNonNullElse(d.getExpectedMinutes(),0)).sum());
            row.put("paidMinutes",owned(slips,Payslip::getUser,u).stream().filter(p -> p.isApproved() && p.isPayoutOvertime()).mapToInt(p -> (int)Math.round(Objects.requireNonNullElse(p.getOvertimeHours(),0.0)*60)).sum());
            row.put("problemDays",daily.stream().filter(d -> d.isNeedsCorrection() || d.getPrimaryTimes()!=null && d.getPrimaryTimes().isOpen()).map(d -> d.getDate().toString()).toList());
            row.put("firstDate",first.toString()); row.put("lastPunch",ownEntries.isEmpty()?null:ownEntries.get(ownEntries.size()-1).getEntryTimestamp().toString()); report.add(row);
        }
        assertEquals(input.path("users").size(),report.size());
        Path out=file.resolveSibling(System.getProperty("chrono.audit.hours.output","comparison.json"));
        String inputHash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        var result = Map.of("snapshotUtc",input.path("snapshotUtc").asText(),"cutoff",cutoff.toString(),"results",report,
                "inputSha256",inputHash,"service",trackingType.getName(),"payrollVersion",PayrollCalculationService.VERSION);
        json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),result);
        String referenceName = System.getProperty("chrono.audit.hours.reference");
        if (referenceName != null) {
            var reference = json.readTree(Files.readString(file.resolveSibling(referenceName)));
            assertEquals(reference.path("snapshotUtc"), input.path("snapshotUtc"));
            assertEquals(reference.path("cutoff").asText(), cutoff.toString());
            // Includes every stored/rebuilt balance, worked/expected total and flagged day.
            assertEquals(reference.path("results"), json.readTree(json.writeValueAsBytes(report)), "Hours differ from the production revision on the identical fixture");
        }
        System.out.println("Offline hours replay completed for "+report.size()+" accounts; report in the input directory. No database was connected.");
    }
}
