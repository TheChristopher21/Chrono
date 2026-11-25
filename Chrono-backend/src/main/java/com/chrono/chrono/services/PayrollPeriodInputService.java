package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class PayrollPeriodInputService {
    private final PayrollPeriodInputRepository versions;
    private final UserRepository users;
    private final PayrollTermsService terms;
    private final EntityManager entities;
    private final ObjectMapper json;
    private final VacationRequestRepository vacations;
    private final SickLeaveRepository sickness;
    private final HolidayService holidays;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PayrollPaymentLedgerService payments;
    public PayrollPeriodInputService(PayrollPeriodInputRepository versions,UserRepository users,PayrollTermsService terms,EntityManager entities,ObjectMapper json,
            VacationRequestRepository vacations,SickLeaveRepository sickness,HolidayService holidays) {
        this.versions=versions; this.users=users; this.terms=terms; this.entities=entities; this.json=json;this.vacations=vacations;this.sickness=sickness;this.holidays=holidays;
    }
    public record Input(Long userId,LocalDate start,LocalDate end,String source,Boolean confirmed,PayrollPeriodData data) {}
    public record Reference(Long versionId,String source,PayrollPeriodData data) {}
    @Transactional(readOnly=true)
    public List<PayrollPeriodInputVersion> list(Long companyId) {return versions.findByCompanyIdOrderByIdDesc(companyId);}
    @Transactional
    public PayrollPeriodInputVersion add(Long companyId,Long actorId,Input input) {
        if(input==null || input.userId()==null || input.source()==null || input.source().isBlank() || input.source().strip().length()>250
                || !Boolean.TRUE.equals(input.confirmed()) || input.data()==null) throw bad("Person, bestätigte Monatsgrundlagen und eine nachvollziehbare Quelle sind erforderlich.");
        User user=users.findByIdForUpdate(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entities.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if(user.isDeleted() || user.getCompany()==null || !Objects.equals(companyId,user.getCompany().getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var t=terms.resolve(user,input.start(),input.end()).terms();
        if(input.start().getYear()==2025) {
            TaxCalculationService.validateEmploymentPeriod(t,input.start(),input.end(),2025);
            if(input.data().historicalBaseline()==null)throw bad("Für 2025 die tatsächlich abgerechneten Monatsgrundlagen als bestätigte Altbasis erfassen.");
        } else if(t.exitDate()!=null&&input.start().isAfter(t.exitDate()))TaxCalculationService.validatePostEmploymentPeriod(t,input.start(),input.end());
        else TaxCalculationService.validateSupportedPeriod(t,input.start(),input.end());
        validate(input.data(),t,input.start(),input.end());
        var v=new PayrollPeriodInputVersion(); v.setCompanyId(companyId); v.setUserId(user.getId()); v.setPeriodStart(input.start()); v.setPeriodEnd(input.end());
        v.setSource(input.source().strip()); v.setCreatedBy(actorId); v.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {v.setParameters(json.writeValueAsString(input.data()));} catch(Exception e){throw new IllegalStateException(e);}
        var saved=versions.saveAndFlush(v);if(payments!=null)payments.year(user,input.start());return saved;
    }
    public Optional<Reference> resolve(User user,LocalDate start,LocalDate end) {
        if(user.getCompany()==null) return Optional.empty();
        boolean writing=TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows=writing?versions.periodForUpdate(user.getCompany().getId(),user.getId(),start,end):versions.period(user.getCompany().getId(),user.getId(),start,end);
        if(rows.isEmpty()) return Optional.empty();
        try {var v=rows.get(0); return Optional.of(new Reference(v.getId(),v.getSource(),json.readValue(v.getParameters(),PayrollPeriodData.class)));}
        catch(Exception e){throw new IllegalStateException("Monatsgrundlagen sind nicht lesbar.",e);}
    }
    public void requireHourlyAbsenceReview(User user,LocalDate start,LocalDate end,PayrollTerms t,Reference reference) {
        if(!t.hourly() || reference!=null)return;
        boolean leave=vacations.findByUserAndApprovedTrue(user).stream().anyMatch(v->!v.getStartDate().isAfter(end)&&!v.getEndDate().isBefore(start))
                || sickness.findByUser(user).stream().anyMatch(s->!s.getStartDate().isAfter(end)&&!s.getEndDate().isBefore(start));
        boolean holiday=user.getCompany()!=null&&holidays.getCompanyHolidays(start.getYear(),user.getCompany()).stream().anyMatch(d->!d.isBefore(start)&&!d.isAfter(end));
        if(leave||holiday)throw new IllegalStateException("Stundenlohn mit Abwesenheit/Feiertag: Die zusätzlichen Monatsgrundlagen samt Lohnfortzahlung oder belegtem fehlendem Lohnanspruch müssen ausdrücklich geprüft sein.");
    }
    public static void validate(PayrollPeriodData data,PayrollTerms t,LocalDate start,LocalDate end) {
        if(data.historicalInsuranceAllocations()!=null)validate(new PayrollPeriodData(List.of(),null,null,data.historicalInsuranceAllocations()),t,start,end);
        if(data.historicalBaseline()!=null) {
            var b=data.historicalBaseline();
            if(start.getYear()!=2025||!start.equals(b.start())||!end.equals(b.end())||data.payments()!=null||!data.absencePayments().isEmpty()||data.germanPriorYearSocial()!=null)throw bad("Altbasis 2025 und neue Zahlungen getrennt erfassen; die historischen Daten müssen genau zur Arbeitsperiode passen.");
            for(var v:Arrays.asList(b.base(),b.premium(),b.overtimePayout(),b.thirteenth()))amount(v,"10000000",2,true);
            if(b.insuranceDays()==null||b.insuranceDays()<1||b.insuranceDays()>30||b.withholding()==null||b.withholding().abs().compareTo(new BigDecimal("10000000"))>0||b.withholding().stripTrailingZeros().scale()>2)throw bad("Belegte Versicherungstage und tatsächlich abgezogene historische Steuer fehlen.");
            if("CH".equals(t.country())) {amount(b.alvUvgBase(),"148200",2,true);amount(b.ktgBase(),"10000000",2,true);amount(b.ktgAnnualCap(),"10000000",2,true);}
            else {amount(b.pensionBase(),"96600",2,true);amount(b.healthBase(),"66150",2,true);signedAmount(b.solidarityTax());signedAmount(b.churchTax());}
            var total=b.base().add(b.premium()).add(b.overtimePayout()).add(b.thirteenth());
            if(b.thirteenthEligibleBase()!=null)amount(b.thirteenthEligibleBase(),b.base().toPlainString(),2,true);
            else if(t.thirteenthMonths()>0)throw bad("Historische Anspruchsbasis für den 13. Monatslohn ausdrücklich erfassen.");
            if(t.thirteenthMonths()==0&&b.thirteenth().signum()!=0)throw bad("Für historischen 13. Monatslohn fehlen die damaligen Anspruchsregeln.");
            if("CH".equals(t.country())?(b.alvUvgBase().compareTo(total)>0||b.ktgBase().compareTo(total)>0):(b.pensionBase().compareTo(total)>0||b.healthBase().compareTo(total)>0))throw bad("Historische SV-Basen dürfen den tatsächlich abgerechneten Bruttolohn nicht übersteigen.");
            if(b.ktgPool()!=null&&(b.ktgPool().key()==null||b.ktgPool().key().isBlank()||b.ktgPool().key().length()>100||b.ktgPool().from()==null||b.ktgPool().through()==null||start.isBefore(b.ktgPool().from())||end.isAfter(b.ktgPool().through())))throw bad("Historischer KTG-Abrechnungskreis ist unvollständig.");
            if(b.taxAllocations()!=null){BigDecimal sum=BigDecimal.ZERO;Set<String> seen=new HashSet<>();for(var a:b.taxAllocations()){
                if(!"CH".equals(t.country())||a==null||a.canton()==null||!a.canton().matches("[A-Z]{2}")||a.from()==null||a.from().getYear()!=2025||a.from().isAfter(end)||!seen.add(a.canton()+":"+a.from()))throw bad("Historische Quellensteuer-Zuordnung ist ungültig/doppelt.");signedAmount(a.amount());sum=sum.add(a.amount());
            }if(sum.compareTo(b.withholding())!=0)throw bad("Historische Steuerzuordnungen stimmen nicht mit der tatsächlichen Quellensteuer überein.");}
        }
        if(t.exitDate()!=null&&start.isAfter(t.exitDate())&&(!data.absencePayments().isEmpty()||data.swissInsuranceAllocations()!=null))throw bad("Nach Austritt Zahlungen mit Arbeits- und Anspruchsdatum erfassen. Abwesenheits-/Versicherungsgrundlagen gehören in die ursprüngliche Arbeitsperiode.");
        if(data.germanPriorYearSocial()!=null) {
            var p=data.germanPriorYearSocial();
            if(!"DE".equals(t.country())||start.getMonthValue()>3||p.assignmentDate()==null||p.assignmentDate().getYear()!=2025||p.insuranceDays()==null||p.insuranceDays()<1||p.insuranceDays()>360
                    ||p.parent()==null||p.childrenUnder25()==null||p.childrenUnder25()<0||p.childrenUnder25()>20||!p.parent()&&p.childrenUnder25()>0||p.saxony()==null
                    ||p.source()==null||p.source().isBlank()||p.source().length()>250||!Boolean.TRUE.equals(p.confirmed()))throw bad("Märzklausel benötigt belegte reguläre SV-Merkmale, Zuordnungsdatum 2025 und vollständige Vorjahresbasen.");
            amount(p.pensionAssessed(),"96600",2,true);amount(p.healthAssessed(),"66150",2,true);amount(p.healthAdditionalRate(),"1",8,true);
            if(p.pensionAllocatedBase()!=null)amount(p.pensionAllocatedBase(),"96600",2,true);if(p.healthAllocatedBase()!=null)amount(p.healthAllocatedBase(),"66150",2,true);
            if(t.multipleEmployment()!=null&&(p.pensionAllocatedBase()==null||p.healthAllocatedBase()==null))throw bad("Mehrfachbeschäftigung: Vorjahres-Einmalzahlungsbasen der Einzugsstelle ausdrücklich erfassen.");
        }
        if(data.payments()!=null) {
            if(data.payments().size()>62)throw bad("Zu viele Nachzahlungspositionen.");Set<String> references=new HashSet<>();
            for(var p:data.payments()) {
                if(p==null||p.reference()==null||p.reference().isBlank()||!p.reference().equals(p.reference().strip())||p.reference().length()>100||!references.add(p.reference())||!Set.of("REGULAR_ARREARS","ONE_OFF","RETROACTIVE_RAISE").contains(Objects.toString(p.kind(),""))
                        ||p.earnedFrom()==null||p.earnedThrough()==null||p.receivedOn()==null||p.claimEstablishedOn()==null||!Boolean.TRUE.equals(p.thirteenthSettled())
                        ||p.earnedFrom().getYear()<2025||p.earnedThrough().getYear()>2026||p.receivedOn().getYear()!=2026||p.earnedThrough().isBefore(p.earnedFrom())
                        ||!YearMonth.from(p.earnedFrom()).equals(YearMonth.from(p.earnedThrough()))||p.receivedOn().isBefore(start)||p.receivedOn().isAfter(end)
                        ||p.earnedThrough().isAfter(p.receivedOn())||p.claimEstablishedOn().isAfter(p.receivedOn())||p.earnedFrom().isBefore(t.entryDate())||t.exitDate()!=null&&p.earnedThrough().isAfter(t.exitDate()))throw bad("Nachzahlung: eindeutige Referenz, Arbeitsperiode 2025/2026, tatsächlicher Anspruch/Zufluss 2026 und vollständig berücksichtigte 13.-Monatslohnansprüche sind erforderlich.");
                amount(p.gross(),"10000000",2,false);
                if(p.bvgEmployeeExtra()!=null)amount(p.bvgEmployeeExtra(),"10000000",2,true);
                if(p.bvgEmployerExtra()!=null)amount(p.bvgEmployerExtra(),"10000000",2,true);
                if("REGULAR_ARREARS".equals(p.kind())&&p.claimEstablishedOn().isAfter(p.earnedThrough()))throw bad("Bereits geschuldeter laufender Lohn setzt einen Anspruch in der Arbeitsperiode voraus. Eine später vereinbarte rückwirkende DE-Lohnerhöhung gesondert erfassen.");
                if("RETROACTIVE_RAISE".equals(p.kind())&&(!"DE".equals(t.country())||p.earnedFrom().getYear()!=2025||p.claimEstablishedOn().getYear()!=2026||!p.claimEstablishedOn().isAfter(p.earnedThrough())))throw bad("Rückwirkende DE-Lohnerhöhung: Arbeit 2025, tatsächlicher Vereinbarungsabschluss 2026 und Anspruch nach der Arbeitsperiode sind erforderlich.");
            }
        }
        if(data.swissInsuranceAllocations()!=null) {
            if(!"CH".equals(t.country())||data.swissInsuranceAllocations().isEmpty()||data.swissInsuranceAllocations().size()>31)throw bad("Versicherungsaufteilungen benötigen Schweizer Monatsgrundlagen.");
            LocalDate cursor=start;
            for(var a:data.swissInsuranceAllocations()) {
                if(a==null||a.from()==null||a.through()==null||!a.from().equals(cursor)||a.through().isBefore(a.from())||a.through().isAfter(end))throw bad("Versicherungsabschnitte müssen chronologisch, lückenlos und überschneidungsfrei sein.");
                amount(a.gross(),"10000000",2,true);amount(a.pensionEmployee(),"10000000",2,true);amount(a.pensionEmployer(),"10000000",2,true);cursor=a.through().plusDays(1);
            }
            if(!cursor.equals(end.plusDays(1)))throw bad("Versicherungsabschnitte decken den Lohnmonat nicht vollständig ab.");
        }
        if(data.absencePayments().size()>62) throw bad("Zu viele Abwesenheitspositionen für einen Monat.");
        if(!data.absencePayments().isEmpty() && (!t.hourly() || t.hourlyAllowance()==null)) throw bad("Abwesenheitszahlungen benötigen die vertraglichen Stundenlohnregeln.");
        Set<String> unique=new HashSet<>(); Map<LocalDate,Integer> minutes=new HashMap<>();
        for(var a:data.absencePayments()) {
            if(a==null || a.date()==null || a.date().isBefore(start) || a.date().isAfter(end) || a.minutes()==null || a.minutes()<=0 || a.minutes()>1440
                    || !Set.of("VACATION","HOLIDAY","EMPLOYER_SICK_PAY").contains(Objects.toString(a.type(),""))
                    || !unique.add(a.date()+":"+a.type())) throw bad("Abwesenheitstage, Arten oder Minuten sind ungültig/doppelt.");
            amount(a.hourlyRate(),"100000",4,false); amount(a.payRate(),"1",6,false);
            if(!"EMPLOYER_SICK_PAY".equals(a.type()) && a.payRate().compareTo(BigDecimal.ONE)!=0) throw bad("Ferien und bezahlte Feiertage benötigen den vollen ausgewiesenen Durchschnittsansatz.");
            if(minutes.merge(a.date(),a.minutes(),Math::addExact)>1440) throw bad("Abwesenheiten übersteigen einen Kalendertag.");
            if("VACATION".equals(a.type()) && "CASH_ALLOWANCE".equals(t.hourlyAllowance().vacationMode())) throw bad("Laufender Ferienzuschlag und erneute Bezahlung derselben Ferien sind nicht kombinierbar.");
            if("HOLIDAY".equals(a.type()) && t.hourlyAllowance().holidayRate().signum()>0) throw bad("Feiertagszuschlag und erneute Feiertagsbezahlung sind nicht kombinierbar.");
        }
        var concurrent=data.germanConcurrentIncome();
        if(data.swissOtherAperiodicGross()!=null) {
            if(!"CH".equals(t.country()) || t.multipleEmployment()==null || !"KNOWN_OTHER_INCOME".equals(t.multipleEmployment().method())) throw bad("Weitere aperiodische Einkünfte benötigen die Schweizer Satzbestimmung nach bekanntem Gesamteinkommen.");
            amount(data.swissOtherAperiodicGross(),"10000000",2,true);
        }
        if(concurrent!=null) {
            if(!"DE".equals(t.country()) || t.multipleEmployment()==null || !Boolean.TRUE.equals(concurrent.fundAllocationConfirmed())) throw bad("Deutsche SV-Aufteilung benötigt die bestätigten Grundlagen der Einzugsstelle für Mehrfachbeschäftigung.");
            if(concurrent.employments()==null||concurrent.employments().isEmpty()) {
                amount(concurrent.otherPensionGross(),"10000000",2,true); amount(concurrent.otherHealthGross(),"10000000",2,true);
            } else {
                if(concurrent.employments().size()>31||concurrent.otherPensionGross()!=null||concurrent.otherHealthGross()!=null)throw bad("Datierte Fremdbeschäftigungen und pauschale Fremdentgelte dürfen nicht doppelt erfasst sein.");
                Set<String> employers=new HashSet<>();
                for(var e:concurrent.employments()) {
                    if(e==null||e.employer()==null||e.employer().isBlank()||e.employer().length()>100||!employers.add(e.employer().strip())||e.from()==null||e.through()==null||e.from().isAfter(e.through())||!YearMonth.from(e.from()).equals(YearMonth.from(start))||!YearMonth.from(e.through()).equals(YearMonth.from(start)))throw bad("Fremdbeschäftigung: eindeutige Bezeichnung und tatsächliche SV-Daten innerhalb des Monats angeben.");
                    amount(e.pensionGross(),"10000000",2,true);amount(e.healthGross(),"10000000",2,true);
                }
            }
            if(concurrent.pensionOneOffBase()!=null) amount(concurrent.pensionOneOffBase(),"10000000",2,true);
            if(concurrent.healthOneOffBase()!=null) amount(concurrent.healthOneOffBase(),"10000000",2,true);
        }
    }
    private static void amount(BigDecimal n,String max,int scale,boolean zero) {
        if(n==null || (zero?n.signum()<0:n.signum()<=0) || n.compareTo(new BigDecimal(max))>0 || n.stripTrailingZeros().scale()>scale) throw bad("Ungültiger Betrag/Satz in den Monatsgrundlagen.");
    }
    private static void signedAmount(BigDecimal n){if(n==null||n.abs().compareTo(new BigDecimal("10000000"))>0||n.stripTrailingZeros().scale()>2)throw bad("Belegter historischer Steuerbetrag fehlt oder ist ungültig.");}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public record SavedPeriod(LocalDate start,LocalDate end,Reference reference) {}
    public List<SavedPeriod> historicalYear(User user,int year) {
        var first=LocalDate.of(year,1,1);var last=first.withMonth(12).withDayOfMonth(31);
        boolean writing=TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows=writing?versions.yearForUpdate(user.getCompany().getId(),user.getId(),first,last):versions.year(user.getCompany().getId(),user.getId(),first,last);
        Set<String> seen=new HashSet<>();List<SavedPeriod> result=new ArrayList<>();
        for(var row:rows)if(seen.add(row.getPeriodStart()+":"+row.getPeriodEnd())) {
            try{result.add(new SavedPeriod(row.getPeriodStart(),row.getPeriodEnd(),new Reference(row.getId(),row.getSource(),json.readValue(row.getParameters(),PayrollPeriodData.class))));}
            catch(Exception e){throw new IllegalStateException("Altgrundlagen sind nicht lesbar.",e);}
        }
        result.sort(Comparator.comparing(SavedPeriod::start));return List.copyOf(result);
    }
}
