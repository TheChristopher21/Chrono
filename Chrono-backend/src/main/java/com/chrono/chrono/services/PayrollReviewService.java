package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.PayrollReviewDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PayrollReviewService {
    private final TimeTrackingService tracking;
    private final PayslipRepository payslips;
    private final PayrollConfigurationService configuration;
    private final PayrollCalculationService calculations;

    public PayrollReviewService(TimeTrackingService tracking, PayslipRepository payslips, PayrollConfigurationService configuration, PayrollCalculationService calculations) {
        this.tracking = tracking;
        this.payslips = payslips;
        this.configuration = configuration;
        this.calculations = calculations;
    }

    public static void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new IllegalArgumentException("Bitte einen gültigen Abrechnungszeitraum von höchstens 366 Tagen angeben.");
        }
    }

    @Transactional(readOnly = true)
    public PayrollReviewDTO review(User user, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        List<PayrollReviewDTO.Issue> issues = new ArrayList<>();
        try {
            calculations.calculate(user, start, end, null, false);
            add(issues, "CALCULATION_AVAILABLE", "WARNING", "Die Berechnung mit datierten Regeln ist verfügbar. Vertragswerte, Sonderleistungen und die Steuerpflicht müssen zur Person passen; die Freigabe prüft den Entwurf erneut.", null, null);
        } catch (IllegalArgumentException | IllegalStateException | org.springframework.web.server.ResponseStatusException exception) {
            String reason = exception instanceof org.springframework.web.server.ResponseStatusException response ? response.getReason() : exception.getMessage();
            add(issues, "CALCULATION_BLOCKED", "BLOCKER", reason, null, null);
        }
        if (!start.withDayOfMonth(1).equals(end.withDayOfMonth(1))) {
            add(issues, "PARTIAL_PERIOD", "BLOCKER", "Mehrere Monate müssen getrennt abgerechnet werden.", null, null);
        }
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Zurich"));
        if (!end.isBefore(today)) add(issues, "FUTURE_PERIOD", "BLOCKER", "Der Zeitraum enthält noch nicht abgeschlossene Tage.", null, null);
        var summary = tracking.getUserPeriodSummary(user, start, end);
        for (DailyTimeSummaryDTO day : summary.getDailySummaries()) {
            if (day.getDate().isAfter(today)) continue;
            if (day.isNeedsCorrection()) add(issues, "CORRECTION_REQUIRED", "BLOCKER", "Automatisch beendete Arbeitszeit muss geprüft werden.", day.getDate(), null);
            if (day.getPrimaryTimes() != null && day.getPrimaryTimes().isOpen()) add(issues, "OPEN_PUNCH", "BLOCKER", "Ein Endstempel fehlt.", day.getDate(), null);
            if (day.getExpectedMinutes() != null && day.getExpectedMinutes() > 0 && !Boolean.TRUE.equals(day.getHasTrackedEntries())) {
                add(issues, "MISSING_TIME", "BLOCKER", "Sollzeit ohne Zeiterfassung oder erfasste Abwesenheit.", day.getDate(), null);
            }
        }
        for (Payslip slip : payslips.findByUserAndApproved(user, true)) {
            if (overlaps(slip, start, end)) add(issues, "APPROVED_OVERLAP", "BLOCKER", "Für diesen Zeitraum besteht bereits eine freigegebene Abrechnung. Eine Korrektur muss ausdrücklich zugeordnet werden.", null, slip.getId());
        }
        return new PayrollReviewDTO(user.getId(), start, end, summary.getWorkedMinutes(), summary.getExpectedMinutes(),
                summary.getDifferenceMinutes(), summary.getDailySummaries(), List.copyOf(issues),
                issues.stream().noneMatch(issue -> "BLOCKER".equals(issue.severity())), false);
    }

    public static boolean overlaps(Payslip slip, LocalDate start, LocalDate end) {
        return slip.getPeriodStart() != null && slip.getPeriodEnd() != null
                && !slip.getPeriodStart().isAfter(end) && !slip.getPeriodEnd().isBefore(start);
    }

    private static boolean positive(Double value) { return value != null && Double.isFinite(value) && value > 0; }
    private static void add(List<PayrollReviewDTO.Issue> issues, String code, String severity, String message, LocalDate date, Long id) {
        issues.add(new PayrollReviewDTO.Issue(code, severity, message, date, id));
    }
}
