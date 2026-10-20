package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Reads effective approved calculations without rewriting the original or any correction. */
@Service
public class PayrollLedgerService {
    private final PayslipRepository repository;
    private final ObjectMapper json;
    public PayrollLedgerService(PayslipRepository repository, ObjectMapper json) { this.repository = repository; this.json = json; }
    public record Reference(Long originalId, Long revisionId, String hash) {}
    public record Entry(Payslip original, Payslip revision, PayrollCalculationService.Calculation calculation) {}
    public record History(List<Entry> entries, List<Reference> references, boolean complete) {
        public void requireComplete() { if (!complete) throw new IllegalStateException("Für die Jahresberechnung fehlen lückenlose freigegebene Berechnungsgrundlagen seit Jahresbeginn/Eintritt. Fehlende Monate zuerst abrechnen; Altbelege chronologisch über Korrekturbelege übernehmen."); }
    }
    public List<Payslip> originals(User user) {
        return repository.findByUserAndApproved(user, true).stream().filter(p -> !p.isCorrection()).sorted(Comparator.comparing(Payslip::getPeriodStart).thenComparing(Payslip::getId)).toList();
    }
    public Payslip latest(Payslip original) {
        return repository.findByUserAndApproved(original.getUser(), true).stream()
                .filter(p -> Objects.equals(p.getCorrectionOfId(), original.getId())).max(Comparator.comparing(Payslip::getId)).orElse(original);
    }
    public PayrollCalculationService.Calculation read(Payslip slip) {
        if (slip.getCalculationSnapshot() == null) throw new IllegalStateException("Altbeleg #" + slip.getId() + " hat keine gesicherten Jahresgrundlagen. Zuerst einen Korrekturbeleg für diesen Monat erstellen.");
        try { return json.readValue(slip.getCalculationSnapshot(), PayrollCalculationService.Calculation.class); }
        catch (Exception e) { throw new IllegalStateException("Berechnungsnachweis von Beleg #" + slip.getId() + " ist nicht lesbar.", e); }
    }
    public Reference reference(Payslip original, Payslip revision) {
        try {
            String payload = original.getId() + ":" + revision.getId() + ":" + revision.getCalculationSnapshot() + ":" + revision.getGrossSalary() + ":" + revision.getDeductions() + ":" + revision.getNetSalary();
            return new Reference(original.getId(), revision.getId(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public History before(User user, PayrollTerms terms, LocalDate start, Map<Long, Payslip> provisional) {
        LocalDate beginning = terms.entryDate().isAfter(start.withDayOfYear(1)) ? terms.entryDate() : start.withDayOfYear(1);
        LocalDate cursor = beginning;
        boolean complete = true;
        List<Entry> entries = new ArrayList<>(); List<Reference> refs = new ArrayList<>();
        for (Payslip original : originals(user)) {
            if (original.getPeriodEnd().isBefore(beginning) || !original.getPeriodStart().isBefore(start)) continue;
            if (!original.getPeriodStart().equals(cursor) || !original.getPeriodEnd().isBefore(start)) complete = false;
            Payslip revision = provisional.getOrDefault(original.getId(), latest(original));
            refs.add(reference(original, revision));
            try {
                var calculation = read(revision);
                if (!Objects.equals(calculation.terms().country(), terms.country())) complete = false;
                entries.add(new Entry(original, revision, calculation));
            } catch (IllegalStateException missing) { complete = false; }
            cursor = original.getPeriodEnd().plusDays(1);
        }
        if (!cursor.equals(start)) complete = false;
        return new History(List.copyOf(entries), List.copyOf(refs), complete);
    }
    public BigDecimal thirteenth(PayrollTerms terms, LocalDate start, LocalDate end, PayrollGrossCalculator.Result gross, History history) {
        int months = terms.thirteenthMonths();
        if (months == 0) return BigDecimal.ZERO;
        history.requireComplete();
        boolean due = end.equals(terms.exitDate()) || end.getMonthValue() % months == 0 && end.getDayOfMonth() == end.lengthOfMonth();
        if (!due) return BigDecimal.ZERO;
        BigDecimal eligible = gross.base(), paid = BigDecimal.ZERO;
        for (Entry entry : history.entries()) {
            if (entry.calculation().terms().thirteenthMonths() > 0) eligible = eligible.add(entry.calculation().gross().base());
            paid = paid.add(entry.calculation().gross().thirteenthAmount());
        }
        BigDecimal step = new BigDecimal("CH".equals(terms.country()) ? "0.05" : "0.01");
        BigDecimal result = eligible.divide(BigDecimal.valueOf(12).multiply(step), 0, RoundingMode.HALF_UP).multiply(step).subtract(paid);
        if (result.signum() < 0) throw new IllegalStateException("Bereits bezahlter 13. Monatslohn übersteigt den Anspruch; vorherige Monate rückrechnen.");
        return result;
    }
}
