package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShiftDefinitionServiceTest {
    private final ScheduleRuleRepository repository = mock(ScheduleRuleRepository.class);
    private final List<ScheduleRule> rows = new ArrayList<>();
    private final Company first = company(17L, "Praxis");
    private final Company second = company(29L, "Second");
    private ShiftDefinitionService service;
    private ScheduleRule inherited;

    @BeforeEach
    void setUp() {
        service = new ShiftDefinitionService(repository);
        inherited = rule("EARLY", "Existing shared label", "07:00", "15:00");
        inherited.setId(1L); rows.add(inherited);
        when(repository.findById(anyLong())).thenAnswer(call -> rows.stream().filter(row -> Objects.equals(row.getId(), call.getArgument(0))).findFirst());
        when(repository.findByCompanyIsNullAndShiftKey(anyString())).thenAnswer(call -> rows.stream()
                .filter(row -> row.getCompany() == null && Objects.equals(row.getShiftKey(), call.getArgument(0))).findFirst());
        when(repository.findByCompany_IdAndShiftKey(anyLong(), anyString())).thenAnswer(call -> rows.stream()
                .filter(row -> row.getCompany() != null && Objects.equals(row.getCompany().getId(), call.getArgument(0))
                        && Objects.equals(row.getShiftKey(), call.getArgument(1))).findFirst());
        when(repository.findByCompanyIsNullOrderByStartTime()).thenAnswer(call -> rows.stream().filter(row -> row.getCompany() == null).toList());
        when(repository.findByCompany_IdOrderByStartTime(anyLong())).thenAnswer(call -> rows.stream()
                .filter(row -> row.getCompany() != null && Objects.equals(row.getCompany().getId(), call.getArgument(0))).toList());
        when(repository.save(any())).thenAnswer(call -> {
            ScheduleRule rule = call.getArgument(0);
            if (rule.getId() == null) { rule.setId(100L + rows.size()); rows.add(rule); }
            return rule;
        });
    }

    @Test
    void migrationDefaultsKeepIdsKeysLabelsAndTimesForBothCompaniesUntilAnExplicitEdit() {
        assertEquals(List.of(inherited), service.effectiveRules(first, true));
        assertEquals(List.of(inherited), service.effectiveRules(second, true));
        assertSame(inherited, service.resolveForUser(user(first), "EARLY").orElseThrow());
        assertSame(inherited, service.resolveForUser(user(second), "EARLY").orElseThrow());
        assertEquals(1L, inherited.getId());
        assertEquals("Existing shared label", inherited.getLabel());
        assertEquals("07:00", inherited.getStartTime());
        assertEquals("15:00", inherited.getEndTime());
        verify(repository, never()).save(any());
    }

    @Test
    void editingInheritedIdCreatesOnlyAnOwnedOverrideAndRejectsForgedOwnership() {
        ScheduleRule request = rule("EARLY", "Praxis early", "08:00", "12:00");
        request.setId(inherited.getId()); request.setCompany(second);
        ScheduleRule saved = service.save(first, request);
        assertNotEquals(inherited.getId(), saved.getId());
        assertSame(first, saved.getCompany());
        assertEquals("08:00", service.resolveForUser(user(first), "EARLY").orElseThrow().getStartTime());
        assertSame(inherited, service.resolveForUser(user(second), "EARLY").orElseThrow());
        assertEquals("Existing shared label", inherited.getLabel());
        assertNull(inherited.getCompany());
        assertEquals(List.of(saved), service.effectiveRules(first, true));
        assertEquals(List.of(inherited), service.effectiveRules(second, true));
    }

    @Test
    void deleteInheritedDefaultSuppressesItOnlyLocallyAndPreservesExistingEntryDuration() {
        service.delete(first, inherited.getId());
        assertTrue(service.effectiveRules(first, false).isEmpty());
        assertEquals(List.of(inherited), service.effectiveRules(second, false));
        assertFalse(service.isAssignable(user(first), "EARLY"));
        ScheduleRule retired = service.resolveForUser(user(first), "EARLY").orElseThrow();
        assertEquals("07:00", retired.getStartTime());
        assertEquals("15:00", retired.getEndTime());
        assertTrue(retired.isSuppressed());
        assertTrue(inherited.getIsActive());
        assertFalse(inherited.isSuppressed());
        verify(repository, never()).deleteById(anyLong());
        service.save(first, rule("EARLY", "Restored locally", "07:00", "15:00"));
        assertTrue(service.isAssignable(user(first), "EARLY"));
    }

    @Test
    void ownedDeleteDoesNotFallBackToSharedDefaultAndForeignIdsCannotBeChanged() {
        ScheduleRule local = service.save(first, rule("EARLY", "Owned", "08:00", "12:00"));
        ScheduleRule foreignRequest = rule("EARLY", "Foreign edit", "09:00", "13:00");
        foreignRequest.setId(local.getId());
        assertThrows(ResponseStatusException.class, () -> service.save(second, foreignRequest));
        assertThrows(ResponseStatusException.class, () -> service.delete(second, local.getId()));
        service.delete(first, local.getId());
        assertTrue(service.effectiveRules(first, false).isEmpty());
        assertEquals("08:00", service.resolveForUser(user(first), "EARLY").orElseThrow().getStartTime());
        assertSame(inherited, service.resolveForUser(user(second), "EARLY").orElseThrow());
    }

    @Test
    void bothCompaniesCanDefineTheSameNewKeyIndependently() {
        ScheduleRule a = service.save(first, rule("NEW", "A", "08:00", "12:00"));
        ScheduleRule b = service.save(second, rule("NEW", "B", "12:00", "16:00"));
        assertNotEquals(a.getId(), b.getId());
        assertSame(a, service.resolveForUser(user(first), "NEW").orElseThrow());
        assertSame(b, service.resolveForUser(user(second), "NEW").orElseThrow());
    }

    @Test
    void renamingAnInheritedKeyPublishesOnlyLocallyAndKeepsOldScheduleDurations() {
        ScheduleRule request = rule("PRAXIS_EARLY", "Renamed locally", "08:00", "12:00");
        request.setId(inherited.getId());
        ScheduleRule saved = service.save(first, request);
        assertEquals(List.of(saved), service.effectiveRules(first, true));
        assertEquals("PRAXIS_EARLY", saved.getShiftKey());
        assertEquals("07:00", service.resolveForUser(user(first), "EARLY").orElseThrow().getStartTime());
        assertFalse(service.isAssignable(user(first), "EARLY"));
        assertEquals(List.of(inherited), service.effectiveRules(second, true));
        assertEquals("EARLY", inherited.getShiftKey());
    }

    @Test
    void demoShiftSeedingNeverMutatesSharedOrOtherCompanyDefinitions() {
        ScheduleRule own = service.save(first, rule("EARLY", "Praxis custom", "08:00", "12:00"));
        DemoDataService demo = new DemoDataService();
        ReflectionTestUtils.setField(demo, "scheduleRuleRepository", repository);
        ReflectionTestUtils.invokeMethod(demo, "ensureShiftRule", second, "EARLY", "Demo early", "09:00", "17:00");
        assertEquals("Praxis custom", own.getLabel());
        assertEquals("08:00", own.getStartTime());
        assertEquals("Existing shared label", inherited.getLabel());
        assertEquals("07:00", inherited.getStartTime());
        assertEquals("Demo early", service.resolveForUser(user(second), "EARLY").orElseThrow().getLabel());
    }

    private static ScheduleRule rule(String key, String label, String start, String end) {
        ScheduleRule rule = new ScheduleRule(); rule.setShiftKey(key); rule.setLabel(label); rule.setStartTime(start); rule.setEndTime(end); return rule;
    }
    private static Company company(Long id, String name) { Company company = new Company(name); company.setId(id); return company; }
    private static User user(Company company) { User user = new User(); user.setCompany(company); return user; }
}
