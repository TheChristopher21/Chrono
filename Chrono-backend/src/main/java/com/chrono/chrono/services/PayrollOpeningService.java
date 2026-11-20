package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class PayrollOpeningService {
    private final PayrollOpeningRepository versions;
    private final UserRepository users;
    private final PayslipRepository slips;
    private final PayrollTermsService terms;
    private final ObjectMapper json;
    private final EntityManager entities;
    public PayrollOpeningService(PayrollOpeningRepository versions, UserRepository users, PayslipRepository slips,
            PayrollTermsService terms, ObjectMapper json, EntityManager entities) {
        this.versions=versions; this.users=users; this.slips=slips; this.terms=terms; this.json=json; this.entities=entities;
    }
    public record Period(LocalDate start, LocalDate end, BigDecimal base, BigDecimal premium, BigDecimal overtimePayout,
            BigDecimal thirteenth, BigDecimal withholding, Integer insuranceDays, BigDecimal alvUvgBase, BigDecimal ktgBase,
            BigDecimal ktgAnnualCap, BigDecimal pensionBase, BigDecimal healthBase,BigDecimal thirteenthEligibleBase,
            BigDecimal solidarityTax,BigDecimal churchTax,PayrollKtgPool ktgPool,BigDecimal swissOtherAperiodicGross,List<TaxAllocation> taxAllocations) {
        public Period(LocalDate start,LocalDate end,BigDecimal base,BigDecimal premium,BigDecimal overtimePayout,BigDecimal thirteenth,
                BigDecimal withholding,Integer insuranceDays,BigDecimal alvUvgBase,BigDecimal ktgBase,BigDecimal ktgAnnualCap,BigDecimal pensionBase,BigDecimal healthBase) {
            this(start,end,base,premium,overtimePayout,thirteenth,withholding,insuranceDays,alvUvgBase,ktgBase,ktgAnnualCap,pensionBase,healthBase,null,null,null,null,null,null);
        }
    }
    public record TaxAllocation(String canton,LocalDate from,BigDecimal amount) {}
    public record Input(Long userId, LocalDate payrollStart, String source, Boolean confirmed, List<Period> periods) {}
    public record Opening(PayrollOpeningVersion version, List<PayrollCalculationService.Calculation> periods) {}
    @Transactional(readOnly=true)
    public List<PayrollOpeningVersion> list(Long companyId) { return versions.findByCompanyIdOrderByIdDesc(companyId); }

    @Transactional
    public PayrollOpeningVersion add(Long companyId, Long actorId, Input input) {
        return save(companyId,actorId,input,false);
    }
    @Transactional
    public PayrollOpeningVersion stageCorrection(Long companyId,Long actorId,Input input) {return save(companyId,actorId,input,true);}
    private PayrollOpeningVersion save(Long companyId,Long actorId,Input input,boolean staging) {
        if (input==null || input.userId()==null || input.payrollStart()==null || input.payrollStart().getYear()!=2026
                || input.source()==null || input.source().isBlank() || input.source().strip().length()>250
                || !Boolean.TRUE.equals(input.confirmed()) || input.periods()==null || input.periods().isEmpty() || input.periods().size()>12)
            throw bad("Eröffnungsdatum 2026, Grundlage, bestätigte vollständige Vorperioden und Mitarbeitende sind erforderlich.");
        User user=users.findByIdForUpdate(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entities.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if (user.isDeleted() || user.getCompany()==null || !Objects.equals(user.getCompany().getId(),companyId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        // Once used, an opening cannot silently change the basis of approved payroll.
        boolean used=slips.findApprovedForUpdate(user).stream().anyMatch(p -> p.getPeriodStart()!=null
                && p.getPeriodStart().getYear()==input.payrollStart().getYear() && p.getCalculationSnapshot()!=null);
        var previous=year(user,input.payrollStart());
        if(used&&!staging)throw bad("Für dieses Jahr bestehen freigegebene Lohnberechnungen. Eine Eröffnungsberichtigung mit gemeinsamem Korrekturlauf ist erforderlich.");
        if(staging&&(!used||previous.isEmpty()||!previous.get().version().getPayrollStart().equals(input.payrollStart())))
            throw bad("Die Eröffnungsberichtigung muss eine bereits verwendete Eröffnung mit demselben Lohnstart ersetzen.");
        var nextTerms=terms.resolve(user,input.payrollStart(),input.payrollStart()).terms();
        LocalDate cursor=nextTerms.entryDate().isAfter(input.payrollStart().withDayOfYear(1))?nextTerms.entryDate():input.payrollStart().withDayOfYear(1);
        if (!input.payrollStart().isAfter(cursor) || input.payrollStart().getDayOfMonth()!=1) throw bad("Der neue Lohnstart muss ein Monatsanfang nach den übernommenen Vorperioden sein.");
        List<PayrollCalculationService.Calculation> calculations=new ArrayList<>();
        Set<String> knownTaxPhases=new HashSet<>(); String currentTaxPhase=null;
        PayrollTerms previousTerms=null;
        for (Period p:input.periods()) {
            if (p==null || !Objects.equals(cursor,p.start()) || p.end()==null || !p.end().isBefore(input.payrollStart()) || !p.end().isBefore(LocalDate.now(ZoneId.of("Europe/Zurich")))) throw bad("Abgeschlossene Vorperioden müssen chronologisch und lückenlos bis zum neuen Lohnstart reichen.");
            var resolved=terms.resolve(user,p.start(),p.end()); var t=resolved.terms();
            TaxCalculationService.validateSupportedPeriod(t,p.start(),p.end());
            if (!Objects.equals(t.country(),nextTerms.country()) || !t.entryDate().equals(nextTerms.entryDate())) throw bad("Land oder Beschäftigungsbeginn der Vorperioden ist widersprüchlich.");
            for (BigDecimal amount:Arrays.asList(p.base(),p.premium(),p.overtimePayout(),p.thirteenth())) money(amount);
            signedMoney(p.withholding());
            BigDecimal total=p.base().add(p.premium()).add(p.overtimePayout()).add(p.thirteenth());
            BigDecimal eligible=p.thirteenthEligibleBase();
            if(eligible==null && t.thirteenthMonths()>0) throw bad("Die für den 13. Monatslohn anspruchsberechtigte Vorperiodenbasis ausdrücklich erfassen.");
            if(eligible==null) eligible=p.base(); else money(eligible);
            if(eligible.compareTo(p.base())>0) throw bad("Die anspruchsberechtigte Basis übersteigt den periodischen Grundlohn der Vorperiode.");
            if (t.thirteenthMonths()==0 && p.thirteenth().signum()>0) throw bad("Für bezahlten 13. Monatslohn fehlen die historischen Anspruchsregeln.");
            int expectedDays="CH".equals(t.country())?PayrollPeriod.swissDays(p.start(),p.end()):PayrollPeriod.fullMonth(p.start(),p.end())?30:Math.min(30,(int)java.time.temporal.ChronoUnit.DAYS.between(p.start(),p.end())+1);
            if (p.insuranceDays()==null || p.insuranceDays()!=expectedDays) throw bad("Versicherungstage der Vorperiode stimmen nicht mit dem Beschäftigungszeitraum überein.");
            SwissPayrollSettlement.Basis ch=null; GermanPayrollTax.Basis de=null;
            if ("CH".equals(t.country())) {
                money(p.alvUvgBase()); money(p.ktgBase()); money(p.ktgAnnualCap());
                if("CH_SOURCE".equals(t.taxMode())) {
                    if(previousTerms==null || !"CH_SOURCE".equals(previousTerms.taxMode()) || !Objects.equals(t.canton(),previousTerms.canton())) currentTaxPhase="CH_SOURCE:"+t.canton()+":"+p.start();
                    knownTaxPhases.add(currentTaxPhase);
                } else currentTaxPhase=null;
                List<SwissWithholdingSettlement.Allocation> allocations=new ArrayList<>();
                if(p.taxAllocations()!=null&&!p.taxAllocations().isEmpty()) {
                    BigDecimal sum=BigDecimal.ZERO;Set<String> uniqueAllocations=new HashSet<>();
                    for(var allocation:p.taxAllocations()) {
                        if(allocation==null || allocation.from()==null || allocation.canton()==null) throw bad("Quellensteuer-Zuordnung ist unvollständig.");
                        signedMoney(allocation.amount());String key="CH_SOURCE:"+allocation.canton()+":"+allocation.from();
                        if(!knownTaxPhases.contains(key)||!uniqueAllocations.add(key)) throw bad("Quellensteuer-Zuordnung verweist auf keinen eindeutigen bisherigen Kantonsabschnitt.");
                        allocations.add(new SwissWithholdingSettlement.Allocation(key,allocation.canton(),allocation.from(),allocation.amount(),null,null));sum=sum.add(allocation.amount());
                    }
                    if(sum.compareTo(p.withholding())!=0)throw bad("Die Steuerzuordnungen ergeben nicht den übernommenen gesamten Quellensteuerbetrag.");
                } else if(currentTaxPhase!=null) allocations.add(new SwissWithholdingSettlement.Allocation(currentTaxPhase,t.canton(),LocalDate.parse(currentTaxPhase.substring(currentTaxPhase.lastIndexOf(':')+1)),p.withholding(),null,null));
                else if(p.withholding().signum()!=0)throw bad("Für Quellensteuer aus einem früheren Kantonsabschnitt die Zuordnung ausdrücklich erfassen.");
                if(p.ktgPool()!=null && (p.ktgPool().key()==null || p.ktgPool().key().isBlank() || p.ktgPool().key().length()>100 || p.ktgPool().from()==null || p.ktgPool().through()==null
                        || p.start().isBefore(p.ktgPool().from()) || p.end().isAfter(p.ktgPool().through()))) throw bad("KTG-Abrechnungskreis der Eröffnungsperiode ist unvollständig.");
                ch=new SwissPayrollSettlement.Basis(expectedDays,p.alvUvgBase(),p.ktgBase(),null,null,p.ktgAnnualCap(),p.ktgPool(),List.copyOf(allocations));
                validateSwissBases(calculations,p,total,expectedDays);
            } else {
                money(p.pensionBase()); money(p.healthBase());
                signedMoney(p.solidarityTax()); signedMoney(p.churchTax());
                if (p.pensionBase().compareTo(total)>0 || p.healthBase().compareTo(total)>0) throw bad("SV-Basen dürfen den Bruttolohn nicht übersteigen.");
                de=new GermanPayrollTax.Basis(expectedDays,p.pensionBase(),p.healthBase(),null);
            }
            var gross=new PayrollGrossCalculator.Result(p.base(),p.premium(),p.overtimePayout(),total,0,p.thirteenth(),null,eligible);
            Map<String,Double> employee="CH".equals(t.country())?Map.of("Withholding tax",p.withholding().doubleValue()):Map.of("Income tax",p.withholding().doubleValue(),"Solidarity surcharge",p.solidarityTax().doubleValue(),"Church tax",p.churchTax().doubleValue());
            PayrollPeriodInputService.Reference extra=null;
            if(t.multipleEmployment()!=null && "KNOWN_OTHER_INCOME".equals(t.multipleEmployment().method())) {
                money(p.swissOtherAperiodicGross());
                extra=new PayrollPeriodInputService.Reference(null,input.source().strip(),new PayrollPeriodData(List.of(),null,p.swissOtherAperiodicGross()));
            }
            calculations.add(new PayrollCalculationService.Calculation(resolved.versionId(),t,p.start(),p.end(),List.of(),gross,employee,Map.of(),
                    "Eröffnungswerte: "+input.source().strip(),ch,de,List.of(),null,extra));
            cursor=p.end().plusDays(1);
            previousTerms=t;
        }
        var expectedEnd=nextTerms.exitDate()!=null&&nextTerms.exitDate().isBefore(input.payrollStart())?nextTerms.exitDate().plusDays(1):input.payrollStart();
        if (!cursor.equals(expectedEnd)) throw bad("Die Eröffnungswerte müssen lückenlos bis zum neuen Lohnstart bzw. bis zum tatsächlichen Austritt reichen.");
        var v=new PayrollOpeningVersion(); v.setCompanyId(companyId); v.setUserId(user.getId()); v.setPayrollStart(input.payrollStart());
        v.setActive(!staging);if(staging)v.setSupersedesOpeningId(previous.orElseThrow().version().getId());
        v.setSource(input.source().strip()); v.setCreatedBy(actorId); v.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try { v.setParameters(json.writeValueAsString(calculations)); } catch (Exception e) { throw new IllegalStateException("Eröffnungswerte konnten nicht gesichert werden.",e); }
        return versions.save(v);
    }
    public Optional<Opening> year(User user, LocalDate period) {
        return year(user,period,null);
    }
    public Optional<Opening> year(User user,LocalDate period,Long selectedVersion) {
        if (user.getCompany()==null) return Optional.empty();
        LocalDate first=period.withDayOfYear(1),last=first.withMonth(12).withDayOfMonth(31);
        boolean writing=TransactionSynchronizationManager.isActualTransactionActive()&&!TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var rows=writing?versions.yearForUpdate(user.getCompany().getId(),user.getId(),first,last):versions.year(user.getCompany().getId(),user.getId(),first,last);
        if (rows.isEmpty()) return Optional.empty();
        if(writing)rows.forEach(v->entities.refresh(v,LockModeType.PESSIMISTIC_WRITE));
        var active=rows.stream().filter(PayrollOpeningVersion::isActive).findFirst();
        var selected=selectedVersion==null?active:rows.stream().filter(v->v.getId().equals(selectedVersion)).findFirst();
        if(selectedVersion!=null&&(selected.isEmpty()||active.isEmpty()||selected.get().isDiscarded()||(!selected.get().isActive()&&!Objects.equals(selected.get().getSupersedesOpeningId(),active.get().getId()))
                ||selected.get().isActive()&&!selected.get().getId().equals(active.get().getId())))throw new IllegalStateException("Die Eröffnungskorrektur basiert auf einer überholten Version. Neu erstellen.");
        if(selected.isEmpty())return Optional.empty();
        try { var v=selected.get();return Optional.of(new Opening(v,json.readValue(v.getParameters(),new TypeReference<>() {}))); }
        catch (Exception e) { throw new IllegalStateException("Gespeicherte Eröffnungswerte sind nicht lesbar.",e); }
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void bindCorrectionBatch(PayrollOpeningVersion version,String batch){version.setCorrectionBatch(batch);versions.saveAndFlush(version);}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void discardBatch(String batch) {
        for(var version:versions.findByCorrectionBatch(batch)) {
            entities.refresh(version,LockModeType.PESSIMISTIC_WRITE);
            if(version.isActive())throw new IllegalStateException("Die freigegebene Eröffnungsgrundlage bleibt erhalten.");
            version.setDiscarded(true);versions.save(version);
        }
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void activateFor(Payslip slip) {
        if(!slip.isCorrection()||slip.getCalculationSnapshot()==null)return;
        Long id;
        try {var node=json.readTree(slip.getCalculationSnapshot()).path("correction").path("openingVersionId");if(node.isNull()||node.isMissingNode())return;id=node.longValue();}
        catch(Exception e){throw new IllegalStateException(e);}
        var opening=year(slip.getUser(),slip.getPeriodStart(),id).orElseThrow();var version=opening.version();
        if(!Objects.equals(version.getCorrectionBatch(),slip.getCorrectionBatch()))throw new IllegalStateException("Eröffnung und Korrekturlauf gehören nicht zusammen.");
        if(!version.isActive()){version.setActive(true);versions.saveAndFlush(version);}
    }
    private static void money(BigDecimal v) {
        if (v==null || v.signum()<0 || v.compareTo(new BigDecimal("999999999.99"))>0 || v.stripTrailingZeros().scale()>2) throw bad("Alle Beträge der Vorperioden ausdrücklich in Rappen/Cent erfassen, auch Nullwerte.");
    }
    private static void signedMoney(BigDecimal v) {
        if(v==null || v.abs().compareTo(new BigDecimal("999999999.99"))>0 || v.stripTrailingZeros().scale()>2) throw bad("Steuerbeträge ausdrücklich in Rappen/Cent erfassen; Rückerstattungen mit negativem Vorzeichen.");
    }
    private static void validateSwissBases(List<PayrollCalculationService.Calculation> history,Period current,BigDecimal gross,int days) {
        BigDecimal yearGross=gross,alv=current.alvUvgBase(),ktg=current.ktgBase(),poolGross=gross;
        int yearDays=days,poolDays=days;
        for(var c:history) {
            yearGross=yearGross.add(c.gross().total());alv=alv.add(c.swissBasis().alvUvgBase());yearDays+=c.swissBasis().insuranceDays();
            if(current.ktgPool()!=null&&c.periodEnd().isBefore(current.ktgPool().from()))continue;
            if(!Objects.equals(current.ktgPool(),c.swissBasis().ktgPool())||current.ktgAnnualCap().compareTo(c.swissBasis().ktgAnnualSalaryCap())!=0) throw bad("KTG-Vorperioden mit Vertragswechsel benötigen getrennte Abrechnungskreise.");
            ktg=ktg.add(c.swissBasis().ktgBase());poolGross=poolGross.add(c.gross().total());poolDays+=c.swissBasis().insuranceDays();
        }
        BigDecimal alvCap=new BigDecimal("148200").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP);
        BigDecimal ktgCap=current.ktgAnnualCap().signum()==0?poolGross:current.ktgAnnualCap().multiply(BigDecimal.valueOf(poolDays)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP);
        if(alv.compareTo(yearGross.min(alvCap))>0 || ktg.compareTo(poolGross.min(ktgCap))>0) throw bad("Die kumulierten übernommenen Versicherungsbasen übersteigen Jahreslohn oder zeitanteilige Vertragsgrenze.");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
