package com.chrono.chrono.services;

import com.chrono.chrono.dto.WorkdaySwapDTO;
import com.chrono.chrono.dto.WorkdaySwapRequestDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.WorkdaySwap;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.repositories.WorkdaySwapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdaySwapServiceTest {
    @Mock private WorkdaySwapRepository workdaySwapRepository;
    @Mock private UserRepository userRepository;
    @Mock private VacationRequestRepository vacationRequestRepository;
    @Mock private WorkScheduleService workScheduleService;
    @Mock private EmploymentModelHistoryService employmentModelHistoryService;
    @Mock private TimeTrackingService timeTrackingService;

    @InjectMocks private WorkdaySwapService workdaySwapService;

    @Test
    void create_movesTheOriginalTargetAndRebuildsTheBalance() {
        Company company = new Company("Chrono");
        company.setId(1L);
        User admin = new User();
        admin.setUsername("admin");
        admin.setCompany(company);
        admin.setRoles(Set.of(new Role("ROLE_ADMIN")));

        User gabriela = new User();
        gabriela.setId(17L);
        gabriela.setUsername("gabriela");
        gabriela.setCompany(company);
        gabriela.setRoles(Set.of(new Role("ROLE_USER")));
        gabriela.setIsHourly(false);
        gabriela.setIsPercentage(false);

        LocalDate replacementTuesday = LocalDate.of(2026, 7, 14);
        LocalDate originalThursday = LocalDate.of(2026, 7, 16);
        WorkdaySwapRequestDTO request = new WorkdaySwapRequestDTO();
        request.setUsername("gabriela");
        request.setOriginalWorkDate(originalThursday);
        request.setReplacementWorkDate(replacementTuesday);
        request.setNote("Dienstag statt Donnerstag");

        when(userRepository.findByUsername("gabriela")).thenReturn(Optional.of(gabriela));
        when(workdaySwapRepository.findConflicts(eq(gabriela), any())).thenReturn(Collections.emptyList());
        when(vacationRequestRepository.findByUserAndApprovedTrue(gabriela)).thenReturn(Collections.emptyList());
        when(employmentModelHistoryService.resolveUserSnapshotForDate(gabriela, originalThursday)).thenReturn(gabriela);
        when(employmentModelHistoryService.resolveUserSnapshotForDate(gabriela, replacementTuesday)).thenReturn(gabriela);
        when(workScheduleService.computeExpectedWorkMinutes(gabriela, originalThursday, Collections.emptyList())).thenReturn(510);
        when(workScheduleService.computeExpectedWorkMinutes(gabriela, replacementTuesday, Collections.emptyList())).thenReturn(0);
        when(workdaySwapRepository.save(any(WorkdaySwap.class))).thenAnswer(invocation -> {
            WorkdaySwap swap = invocation.getArgument(0);
            swap.setId(99L);
            return swap;
        });

        WorkdaySwapDTO result = workdaySwapService.create(admin, request);

        assertEquals(510, result.transferredMinutes());
        assertEquals(originalThursday, result.originalWorkDate());
        assertEquals(replacementTuesday, result.replacementWorkDate());
        verify(timeTrackingService).rebuildUserBalance(gabriela);
    }
}
