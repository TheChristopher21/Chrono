package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final Optional<PayrollOpeningService> openings;
    public PayrollLedgerService(PayslipRepository repository, ObjectMapper json) { this(repository,json,Optional.empty()); }
    @org.springframework.beans.factory.annotation.Autowired
    public PayrollLedgerService(PayslipRepository repository, ObjectMapper json, Optional<PayrollOpeningService> openings) {
        this.repository = repository; this.json = json; this.openings=openings;
    }
    public record Reference(Long originalId, Long revisionId, String hash) {}
    public record Entry(Payslip original, Payslip revision, PayrollCalculationService.Calculation calculation) {}
    public record History(List<Entry> entries, List<Reference> references, boolean complete) {
        public void requireComplete() { if (!complete) throw new IllegalStateException("Für die Jahresberechnung fehlen lückenlose Grundlagen seit Jahresbeginn/Eintritt. Vorperioden als bestätigte Eröffnungswerte erfassen oder fehlende neue Lohnmonate abrechnen."); }
    }
    public List<Payslip> originals(User user) {
        return approved(user).stream().filter(p -> !p.isCorrection()).sorted(Comparator.comparing(Payslip::getPeriodStart).thenComparing(Payslip::getId)).toList();
    }
    public Payslip latest(Payslip original) {
        return approved(original.getUser()).stream()
                .filter(p -> Objects.equals(p.getCorrectionOfId(), original.getId())).max(Comparator.comparing(Payslip::getId)).orElse(original);
    }
    private List<Payslip> approved(User user) {
        // MySQL REPEATABLE READ retains the snapshot opened before waiting for the employee lock.
        // A locking/current read must therefore also be used for the history that validates a write.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return repository.findApprovedForUpdate(user);
        }
        return repository.findByUserAndApproved(user, true);
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
        return before(user,terms,start,provisional,null);
    }
    public History before(User user,PayrollTerms terms,LocalDate start,Map<Long,Payslip> provisional,Long openingVersionId) {
        LocalDate beginning = terms.entryDate().isAfter(start.withDayOfYear(1)) ? terms.entryDate() : start.withDayOfYear(1);
        LocalDate cursor = beginning;
        boolean complete = true;
        List<Entry> entries = new ArrayList<>(); List<Reference> refs = new ArrayList<>();
        var opening=openings.flatMap(service -> service.year(user,start,openingVersionId));
        if(openingVersionId!=null&&opening.isEmpty())throw new IllegalStateException("Eröffnungsberichtigung ist nicht verfügbar.");
        if (opening.isPresent()) {
            var imported=opening.get();
            if (start.isBefore(imported.version().getPayrollStart())) throw new IllegalStateException("Dieser Zeitraum gehört zu den unveränderten Vorperioden vor dem neuen Lohnstart.");
            int index=0;
            for (var calculation:imported.periods()) {
                if (!cursor.equals(calculation.periodStart()) || !Objects.equals(calculation.terms().country(),terms.country())) complete=false;
                Payslip basis=new Payslip(); basis.setId(-Math.addExact(Math.multiplyExact(imported.version().getId(),16L),++index));
                basis.setUser(user); basis.setApproved(true); basis.setPeriodStart(calculation.periodStart()); basis.setPeriodEnd(calculation.periodEnd());
                basis.setGrossSalary(calculation.gross().total().doubleValue()); basis.setDeductions(calculation.employee().values().stream().mapToDouble(Double::doubleValue).sum());
                basis.setNetSalary(BigDecimal.valueOf(basis.getGrossSalary()).subtract(BigDecimal.valueOf(basis.getDeductions())).doubleValue());
                try { basis.setCalculationSnapshot(json.writeValueAsString(calculation)); } catch(Exception e) { throw new IllegalStateException(e); }
                entries.add(new Entry(basis,basis,calculation)); refs.add(reference(basis,basis)); cursor=calculation.periodEnd().plusDays(1);
            }
            var openingEnd=terms.exitDate()!=null&&terms.exitDate().isBefore(imported.version().getPayrollStart())?terms.exitDate().plusDays(1):imported.version().getPayrollStart();
            if (!cursor.equals(openingEnd)) complete=false;
        }
        for (Payslip original : originals(user)) {
            if (original.getPeriodEnd().isBefore(beginning) || !original.getPeriodStart().isBefore(start)) continue;
            if (opening.isPresent() && original.getPeriodEnd().isBefore(opening.get().version().getPayrollStart())) continue;
            Payslip revision = provisional.getOrDefault(original.getId(), latest(original));
            refs.add(reference(original, revision));
            try {
                var calculation = read(revision);
                boolean cashOnly=calculation.remuneration()!=null&&calculation.remuneration().cashOnly();
                if(!original.getPeriodEnd().isBefore(start)||!cashOnly&&!original.getPeriodStart().equals(cursor))complete=false;
                if(!cashOnly)cursor=original.getPeriodEnd().plusDays(1);
                if (!Objects.equals(calculation.terms().country(), terms.country())) complete = false;
                entries.add(new Entry(original, revision, calculation));
            } catch (IllegalStateException missing) { complete = false; }
        }
        LocalDate expected=terms.exitDate()!=null&&start.isAfter(terms.exitDate())?terms.exitDate().getYear()<start.getYear()?start.withDayOfYear(1):terms.exitDate().plusDays(1):start;
        if (!cursor.equals(expected)) complete = false;
        return new History(List.copyOf(entries), List.copyOf(refs), complete);
    }
    public BigDecimal thirteenth(PayrollTerms terms, LocalDate start, LocalDate end, PayrollGrossCalculator.Result gross, History history) {
        int months = terms.thirteenthMonths();
        if (months == 0) {
            BigDecimal earned = BigDecimal.ZERO, paid = BigDecimal.ZERO;
            boolean previousEntitlement = false;
            for (Entry entry : history.entries()) {
                if (entry.calculation().terms().thirteenthMonths() > 0) {
                    previousEntitlement = true;
                    earned = earned.add(entry.calculation().gross().eligibleThirteenthBase());
                }
                paid = paid.add(entry.calculation().gross().thirteenthAmount());
            }
            if (previousEntitlement || paid.signum() != 0) {
                history.requireComplete();
                BigDecimal step = new BigDecimal("CH".equals(terms.country()) ? "0.05" : "0.01");
                BigDecimal outstanding = earned.divide(BigDecimal.valueOf(12).multiply(step), 0, RoundingMode.HALF_UP).multiply(step).subtract(paid);
                if (outstanding.signum() != 0) throw new IllegalStateException("Der bisherige Anspruch auf den 13. Monatslohn ist noch nicht ausgeglichen. Vor dem Wechsel auf keinen 13. Monatslohn die bisherigen Ansprüche mit dem vereinbarten Auszahlungsrhythmus abrechnen oder rückrechnen.");
            }
            return BigDecimal.ZERO;
        }
        history.requireComplete();
        boolean due = end.equals(terms.exitDate()) || end.getMonthValue() % months == 0 && end.getDayOfMonth() == end.lengthOfMonth();
        if (!due) return BigDecimal.ZERO;
        BigDecimal eligible = gross.eligibleThirteenthBase(), paid = BigDecimal.ZERO;
        for (Entry entry : history.entries()) {
            if (entry.calculation().terms().thirteenthMonths() > 0) eligible = eligible.add(entry.calculation().gross().eligibleThirteenthBase());
            paid = paid.add(entry.calculation().gross().thirteenthAmount());
        }
        BigDecimal step = new BigDecimal("CH".equals(terms.country()) ? "0.05" : "0.01");
        BigDecimal result = eligible.divide(BigDecimal.valueOf(12).multiply(step), 0, RoundingMode.HALF_UP).multiply(step).subtract(paid);
        if (result.signum() < 0) throw new IllegalStateException("Bereits bezahlter 13. Monatslohn übersteigt den Anspruch; vorherige Monate rückrechnen.");
        return result;
    }
}
