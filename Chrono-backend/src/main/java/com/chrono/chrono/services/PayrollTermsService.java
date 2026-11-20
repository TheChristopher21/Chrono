package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.PayrollTerms;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class PayrollTermsService {
    private final PayrollTermsRepository versions;
    private final UserRepository users;
    private final ObjectMapper json;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    public PayrollTermsService(PayrollTermsRepository versions, UserRepository users, ObjectMapper json) {
        this.versions = versions; this.users = users; this.json = json;
    }
    public record Input(Long userId, LocalDate effectiveFrom, String source, PayrollTerms terms) {}
    public record Resolved(Long versionId, PayrollTerms terms) {}
    @Transactional(readOnly = true)
    public List<PayrollTermsVersion> list(Long companyId) { return versions.findByCompanyIdOrderByEffectiveFromDescIdDesc(companyId); }
    @Transactional
    public PayrollTermsVersion add(Long companyId, Long actorId, Input input) {
        if (input == null || input.userId() == null) throw bad("Mitarbeitende fehlen.");
        User user = users.findByIdForUpdate(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(entities!=null)entities.refresh(user,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user.isDeleted() || user.getCompany() == null || !Objects.equals(companyId, user.getCompany().getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (input.effectiveFrom() == null || input.effectiveFrom().getYear() < 1900 || input.effectiveFrom().getYear() > 9999
                || input.source() == null || input.source().isBlank() || input.source().strip().length() > 250) throw bad("Gültigkeitsdatum und Grundlage (maximal 250 Zeichen) sind erforderlich.");
        validate(input.terms());
        var v = new PayrollTermsVersion(); v.setCompanyId(companyId); v.setUserId(user.getId());
        v.setEffectiveFrom(input.effectiveFrom()); v.setSource(input.source().strip()); v.setCreatedBy(actorId);
        v.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try { v.setParameters(json.writeValueAsString(input.terms())); }
        catch (Exception e) { throw new IllegalStateException("Lohngrundlagen konnten nicht gespeichert werden.", e); }
        return versions.save(v);
    }
    @Transactional(readOnly = true)
    public Resolved resolve(User user, LocalDate start, LocalDate end) {
        PayrollReviewService.validatePeriod(start, end);
        if (user.getCompany() == null) throw new IllegalStateException("Firma fehlt.");
        var first = version(user,start)
                .orElseThrow(() -> new IllegalStateException("Für diesen Zeitraum fehlen datierte Lohn-, Steuer- und Überstundenregeln. Bitte in den Firmenparametern erfassen."));
        var last = version(user,end).orElseThrow();
        if (!first.getId().equals(last.getId())) throw new IllegalStateException("Lohn- oder Steuerregeln ändern innerhalb des Monats. Eine getrennte Berechnung ist erforderlich.");
        try { return new Resolved(first.getId(), json.readValue(first.getParameters(), PayrollTerms.class)); }
        catch (Exception e) { throw new IllegalStateException("Gespeicherte Lohngrundlagen sind nicht lesbar.", e); }
    }
    private Optional<PayrollTermsVersion> version(User user,LocalDate date) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return versions.currentVersions(user.getCompany().getId(),user.getId(),date).stream().findFirst();
        return versions.findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(user.getCompany().getId(),user.getId(),date);
    }
    public static void validate(PayrollTerms t) {
        if (t == null || !Set.of("CH", "DE").contains(Objects.toString(t.country(), "")) || !Set.of("SALARY", "HOURLY").contains(Objects.toString(t.payType(), ""))) throw bad("Land oder Lohnart fehlt.");
        number(t.amount(), "Lohnbetrag", "1000000", 2, true);
        number(t.weeklyHours(), "Wochenstunden", "168", 4, true);
        if (t.hourly() && t.weeklyHours().multiply(BigDecimal.valueOf(60)).stripTrailingZeros().scale() > 0) throw bad("Die Wochenstundengrenze im Stundenlohn muss ganzen Minuten entsprechen (z. B. 40.5).");
        number(t.overtimeHourlyRate(), "Überstunden-Grundansatz", "100000", 4, true);
        number(t.overtimePremiumRate(), "Überstundenzuschlag", "5", 6, false);
        if (t.birthDate() == null || t.entryDate() == null || t.birthDate().isAfter(t.entryDate())) throw bad("Geburts- und Eintrittsdatum fehlen oder sind ungültig.");
        if (!Boolean.TRUE.equals(t.monthlySingleEmployment()) && t.multipleEmployment()==null) throw bad("Monatliche Einzelbeschäftigung bestätigen oder die Grundlagen der Mehrfachbeschäftigung erfassen.");
        if (Boolean.TRUE.equals(t.monthlySingleEmployment()) && t.multipleEmployment()!=null) throw bad("Einzel- und Mehrfachbeschäftigung dürfen nicht gleichzeitig bestätigt sein.");
        validateSpecialTerms(t);
        if (t.contract() != null) {
            var c = t.contract();
            if (c.exitDate() != null && c.exitDate().isBefore(t.entryDate())) throw bad("Austritt liegt vor dem Eintritt.");
            if (!Set.of("THIRTY_DAYS", "CALENDAR_DAYS").contains(Objects.toString(c.salaryProration(), ""))) throw bad("Vertragliche Teilmonatsmethode auswählen.");
            if (!Set.of("FULL", "THIRTY_DAYS", "CALENDAR_DAYS").contains(Objects.toString(c.pensionProration(), ""))) throw bad("BVG-Behandlung im Teilmonat gemäss Pensionskasse auswählen.");
            if (!Set.of("NONE", "MONTHLY", "QUARTERLY", "SEMIANNUAL", "ANNUAL").contains(Objects.toString(c.thirteenthFrequency(), ""))) throw bad("Vertraglichen Auszahlungsrhythmus des 13. Monatslohns auswählen.");
            if ("DE".equals(t.country()) && c.paymentMonths() > 1) number(c.germanPreviousEmploymentGross(), "Vorarbeitgeber-Jahresbrutto (0 bei nachgewiesen keiner Vorbeschäftigung)", "10000000", 2, false);
        }
        if ("CH".equals(t.country())) {
            if (!Set.of("CH_ORDINARY", "CH_SOURCE").contains(Objects.toString(t.taxMode(), ""))) throw bad("Schweizer Steuerpflicht ausdrücklich auswählen.");
            if (t.weeklyLimit() == null || (t.weeklyLimit() != 45 && t.weeklyLimit() != 50) || t.weeklyHours().compareTo(BigDecimal.valueOf(t.weeklyLimit())) > 0) throw bad("Die gesetzliche Wochenhöchstzeit muss 45 oder 50 Stunden betragen und mindestens den Vertragsstunden entsprechen.");
            if ("CH_SOURCE".equals(t.taxMode()) && (t.canton() == null || !t.canton().matches("[A-Z]{2}") || t.tariffCode() == null || !t.tariffCode().matches("[ABCHLMNPRSTU][0-9][NY]"))) throw bad("Gültigen Kanton und persönlichen Quellensteuertarif angeben (z. B. ZH, A0N).");
        } else {
            if (!"DE_STANDARD".equals(t.taxMode()) || t.taxClass() == null || t.taxClass() < 1 || t.taxClass() > 6) throw bad("Deutsche Steuerklasse 1 bis 6 und reguläre Sozialversicherung angeben.");
            number(t.childAllowance(), "ELStAM-Kinderfreibeträge", "20", 1, false);
            if (t.childAllowance().remainder(new BigDecimal("0.5")).signum() != 0) throw bad("ELStAM-Kinderfreibeträge müssen in halben Zählern angegeben werden.");
            number(t.churchRate(), "Kirchensteuer", "0.09", 2, false);
            if (!Set.of(new BigDecimal("0"), new BigDecimal("0.08"), new BigDecimal("0.09")).contains(t.churchRate().stripTrailingZeros())) throw bad("Kirchensteuersatz muss 0, 8 oder 9 % sein.");
            number(t.healthAdditionalRate(), "Voller Kassen-Zusatzbeitrag", "0.20", 4, false);
            number(t.taxAllowance(), "Monatlicher ELStAM-Freibetrag", "1000000", 2, false);
            number(t.taxAddition(), "Monatlicher ELStAM-Hinzurechnungsbetrag", "1000000", 2, false);
            number(t.taxFactor(), "ELStAM-Faktor", "1", 3, true);
            if (t.taxClass() != 4 && t.taxFactor().compareTo(BigDecimal.ONE) != 0) throw bad("Faktorverfahren ist nur in Steuerklasse IV zulässig.");
            number(t.u1Rate(), "U1-Umlage", "0.20", 6, false); number(t.u2Rate(), "U2-Umlage", "0.20", 6, false);
            number(t.insolvencyRate(), "Insolvenzgeldumlage", "0.20", 6, false);
            if (t.parent() == null || t.saxony() == null || t.childrenUnder25() == null || t.childrenUnder25() < 0 || t.childrenUnder25() > 20 || (!t.parent() && t.childrenUnder25() > 0)) throw bad("Elterneigenschaft, Kinder unter 25 und Sachsen-Regelung vollständig angeben.");
        }
    }
    private static void validateSpecialTerms(PayrollTerms t) {
        var m=t.multipleEmployment();
        if (m!=null) {
            if ("CH".equals(t.country())) {
                if (!Set.of("TOTAL_PERCENT","UNKNOWN_TOTAL_PERCENT","KNOWN_OTHER_INCOME").contains(Objects.toString(m.method(),""))) throw bad("Methode zur Satzbestimmung bei Mehrfachbeschäftigung fehlt.");
                if ("KNOWN_OTHER_INCOME".equals(m.method())) {
                    number(m.otherPeriodicMonthlyGross(),"Bekanntes weiteres periodisches Monatseinkommen","10000000",4,false);
                } else {
                    number(m.ownPercent(),"Eigenes Pensum","100",4,true);
                    if ("TOTAL_PERCENT".equals(m.method())) {
                        number(m.totalPercent(),"Gesamtpensum","300",4,true);
                        if(m.totalPercent().compareTo(m.ownPercent())<0) throw bad("Gesamtpensum ist kleiner als das eigene Pensum.");
                    }
                    if (m.otherAperiodicGross()!=null && m.otherAperiodicGross().signum()!=0) throw bad("Weitere Einkünfte nur bei offengelegtem Gesamteinkommen erfassen.");
                }
                if(m.otherAperiodicGross()!=null && m.otherAperiodicGross().signum()!=0) throw bad("Aperiodische weitere Einkünfte gehören in die datierten Monatsgrundlagen, nicht in fortgeltende Vertragsregeln.");
            } else if (!"DE_OTHER_EMPLOYER".equals(m.method()) || m.mainGermanEmployment()==null
                    || !m.mainGermanEmployment() && !Integer.valueOf(6).equals(t.taxClass())
                    || m.mainGermanEmployment() && Integer.valueOf(6).equals(t.taxClass())) throw bad("Haupt-/Nebenarbeitsverhältnis und ELStAM-Steuerklasse (Nebenarbeitgeber: VI) sind widersprüchlich.");
        }
        var a=t.hourlyAllowance();
        if(a!=null) {
            if(!t.hourly() || !Set.of("PAID_ABSENCE","CASH_ALLOWANCE").contains(Objects.toString(a.vacationMode(),""))
                    || !Set.of("BASE_ONLY","BASE_AND_PREMIUM").contains(Objects.toString(a.allowanceBase(),""))
                    || a.allowancesInThirteenth()==null || a.paidAbsencesInThirteenth()==null) throw bad("Vollständige vertragliche Stundenlohn-Entschädigungsregeln erfassen.");
            number(a.vacationRate(),"Ferienzuschlag","1",6,false); number(a.holidayRate(),"Feiertagszuschlag","1",6,false);
            if("PAID_ABSENCE".equals(a.vacationMode()) && a.vacationRate().signum()!=0) throw bad("Ferienbezahlung bei Bezug darf nicht zugleich einen laufenden Ferienzuschlag enthalten.");
            if("CASH_ALLOWANCE".equals(a.vacationMode()) && (!"CH".equals(t.country()) || !Boolean.TRUE.equals(a.cashVacationPermitted()))) throw bad("Laufende Ferienabgeltung benötigt eine ausdrücklich bestätigte zulässige Schweizer Vertragsgrundlage.");
            if("CASH_ALLOWANCE".equals(a.vacationMode()) && a.vacationRate().compareTo(new BigDecimal("0.0833"))<0) throw bad("Die laufende Ferienentschädigung darf den Mindestanspruch von vier Ferienwochen (8,33 %) nicht unterschreiten; höhere Alters-/Vertragsansprüche zusätzlich prüfen.");
            if("DE".equals(t.country()) && a.holidayRate().signum()!=0) throw bad("Deutsche Feiertags-Lohnfortzahlung als konkrete Abwesenheitszahlung erfassen.");
        }
    }
    private static void number(BigDecimal v, String label, String max, int scale, boolean positive) {
        if (v == null || v.signum() < 0 || (positive && v.signum() == 0) || v.compareTo(new BigDecimal(max)) > 0 || v.stripTrailingZeros().scale() > scale) throw bad(label + " fehlt oder ist ungültig.");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
