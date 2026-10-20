package com.chrono.chrono.services;

import com.chrono.chrono.dto.WorkdaySwapDTO;
import com.chrono.chrono.dto.WorkdaySwapRequestDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.entities.WorkdaySwap;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.chrono.chrono.repositories.WorkdaySwapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class WorkdaySwapService {
    private final WorkdaySwapRepository workdaySwapRepository;
    private final UserRepository userRepository;
    private final VacationRequestRepository vacationRequestRepository;
    private final WorkScheduleService workScheduleService;
    private final EmploymentModelHistoryService employmentModelHistoryService;
    private final TimeTrackingService timeTrackingService;

    public WorkdaySwapService(
            WorkdaySwapRepository workdaySwapRepository,
            UserRepository userRepository,
            VacationRequestRepository vacationRequestRepository,
            WorkScheduleService workScheduleService,
            EmploymentModelHistoryService employmentModelHistoryService,
            TimeTrackingService timeTrackingService
    ) {
        this.workdaySwapRepository = workdaySwapRepository;
        this.userRepository = userRepository;
        this.vacationRequestRepository = vacationRequestRepository;
        this.workScheduleService = workScheduleService;
        this.employmentModelHistoryService = employmentModelHistoryService;
        this.timeTrackingService = timeTrackingService;
    }

    @Transactional(readOnly = true)
    public List<WorkdaySwapDTO> list(User requestingAdmin, String username, LocalDate startDate, LocalDate endDate) {
        User target = loadAccessibleTarget(requestingAdmin, username);
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start- und Enddatum sind erforderlich.");
        }
        LocalDate start = startDate.isAfter(endDate) ? endDate : startDate;
        LocalDate end = endDate.isBefore(startDate) ? startDate : endDate;
        return workdaySwapRepository.findByUserAndDateRange(target, start, end).stream()
                .map(WorkdaySwapDTO::fromEntity)
                .toList();
    }

    @Transactional
    public WorkdaySwapDTO create(User requestingAdmin, WorkdaySwapRequestDTO request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()
                || request.getOriginalWorkDate() == null || request.getReplacementWorkDate() == null) {
            throw new IllegalArgumentException("Benutzer, ursprünglicher Arbeitstag und Ersatztag sind erforderlich.");
        }

        User target = loadAccessibleTarget(requestingAdmin, request.getUsername());
        if (Boolean.TRUE.equals(target.getIsHourly()) || Boolean.TRUE.equals(target.getIsPercentage())) {
            throw new IllegalArgumentException("Arbeitstagtausch ist nur für Mitarbeitende mit festem Wochenplan verfügbar.");
        }

        LocalDate originalDate = request.getOriginalWorkDate();
        LocalDate replacementDate = request.getReplacementWorkDate();
        if (originalDate.equals(replacementDate)) {
            throw new IllegalArgumentException("Arbeitstag und Ersatztag müssen verschieden sein.");
        }
        LocalDate originalMonday = originalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate replacementMonday = replacementDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!originalMonday.equals(replacementMonday)) {
            throw new IllegalArgumentException("Der Tausch muss innerhalb derselben Kalenderwoche liegen.");
        }
        if (!workdaySwapRepository.findConflicts(target, List.of(originalDate, replacementDate)).isEmpty()) {
            throw new IllegalArgumentException("Für einen der ausgewählten Tage besteht bereits ein Arbeitstagtausch.");
        }

        List<VacationRequest> approvedVacations = vacationRequestRepository.findByUserAndApprovedTrue(target);
        User originalSchedule = employmentModelHistoryService.resolveUserSnapshotForDate(target, originalDate);
        User replacementSchedule = employmentModelHistoryService.resolveUserSnapshotForDate(target, replacementDate);
        int originalMinutes = workScheduleService.computeExpectedWorkMinutes(
                originalSchedule != null ? originalSchedule : target,
                originalDate,
                approvedVacations
        );
        int replacementMinutes = workScheduleService.computeExpectedWorkMinutes(
                replacementSchedule != null ? replacementSchedule : target,
                replacementDate,
                approvedVacations
        );
        if (originalMinutes <= 0) {
            throw new IllegalArgumentException("Der ursprüngliche Tag hat keine Sollzeit und kann nicht verschoben werden.");
        }
        if (replacementMinutes > 0) {
            throw new IllegalArgumentException("Der Ersatztag hat bereits Sollzeit. Bitte einen regulär freien Tag wählen.");
        }

        WorkdaySwap swap = new WorkdaySwap();
        swap.setUser(target);
        swap.setOriginalWorkDate(originalDate);
        swap.setReplacementWorkDate(replacementDate);
        swap.setTransferredMinutes(originalMinutes);
        swap.setNote(normalizeNote(request.getNote()));
        swap.setCreatedBy(requestingAdmin.getUsername());
        swap.setCreatedAt(LocalDateTime.now());
        WorkdaySwap saved = workdaySwapRepository.save(swap);
        workdaySwapRepository.flush();
        timeTrackingService.rebuildUserBalance(target);
        return WorkdaySwapDTO.fromEntity(saved);
    }

    @Transactional
    public void delete(User requestingAdmin, Long swapId) {
        WorkdaySwap swap = workdaySwapRepository.findById(swapId)
                .orElseThrow(() -> new IllegalArgumentException("Arbeitstagtausch wurde nicht gefunden."));
        User target = loadAccessibleTarget(requestingAdmin, swap.getUser().getUsername());
        workdaySwapRepository.delete(swap);
        workdaySwapRepository.flush();
        timeTrackingService.rebuildUserBalance(target);
    }

    private User loadAccessibleTarget(User requestingAdmin, String username) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer wurde nicht gefunden."));
        boolean superAdmin = requestingAdmin.getRoles().stream()
                .anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));
        if (!superAdmin) {
            if (requestingAdmin.getCompany() == null || target.getCompany() == null
                    || !requestingAdmin.getCompany().getId().equals(target.getCompany().getId())) {
                throw new SecurityException("Kein Zugriff auf Mitarbeitende anderer Firmen.");
            }
        }
        boolean targetIsSuperAdmin = target.getRoles().stream()
                .anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));
        if (targetIsSuperAdmin) {
            throw new SecurityException("Superadmin-Konten können keinen Arbeitstagtausch erhalten.");
        }
        return target;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
