package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

/** Explicit 2025/imported-period settlements. Neither imported facts nor original payslips are changed. */
@Service
public class PayrollHistoricalSettlementService {
    private final PayrollPeriodInputService inputs;private final PayrollTermsService terms;
    private final PayrollConfigurationService configuration;private final PayrollLedgerService ledger;
    private final SwissSourceTax sourceTax;private final ObjectMapper json;
    public PayrollHistoricalSettlementService(PayrollPeriodInputService inputs,PayrollTermsService terms,
            PayrollConfigurationService configuration,PayrollLedgerService ledger,SwissSourceTax sourceTax,ObjectMapper json) {
        this.inputs=inputs;this.terms=terms;this.configuration=configuration;this.ledger=ledger;this.sourceTax=sourceTax;this.json=json;
    }
    public record Prepared(PayrollLedgerService.History history,PayrollHistoricalAssessment assessment) {}
    private record Replay(List<PayrollCalculationService.Calculation> revised,Map<String,Double> employee,Map<String,Double> employer) {}
    private record PriorYear(PayrollPeriodData.GermanPriorYearSocial original,PayrollPeriodData.GermanPriorYearSocial revised) {}
    public Prepared prepare(User user,PayrollTerms current,LocalDate start,LocalDate end,PayrollLedgerService.History history,PayrollPaymentLedgerService.Plan plan) {
        List<PayrollCalculationService.Calculation> imported=history.entries().stream().filter(e->e.original().getId()<0).map(PayrollLedgerService.Entry::calculation).toList();
        boolean prior=plan.payments().stream().anyMatch(i->i.payment().earnedFrom().getYear()==2025)
                ||needsPrior(current,end,plan)&&!inputs.historicalYear(user,2025).isEmpty()
                ||history.entries().stream().anyMatch(e->e.calculation().remuneration()!=null&&e.calculation().remuneration().assessment()!=null
                    &&e.calculation().remuneration().assessment().originalPeriods().stream().anyMatch(c->c.periodStart().getYear()==2025));
        boolean opening=plan.payments().stream().anyMatch(i->imported.stream().anyMatch(c->contains(c,i.payment().earnedFrom())));
        if(!prior&&!opening&&history.entries().stream().noneMatch(e->e.calculation().remuneration()!=null&&e.calculation().remuneration().assessment()!=null))return new Prepared(history,null);
        var old2025=prior?load2025(user,current):List.<PayrollCalculationService.Calculation>of();
        for(var item:plan.payments())if("DE".equals(current.country())&&"REGULAR_ARREARS".equals(item.payment().kind())
                &&(item.payment().earnedFrom().getYear()==2025||imported.stream().anyMatch(c->contains(c,item.payment().earnedFrom())))
                &&!Boolean.TRUE.equals(item.payment().employeeSocialRecoveryConfirmed()))throw new IllegalStateException("Historischer Arbeitnehmer-SV-Abzug: Die zulässige Nachholung nach § 28g SGB IV muss anhand der Grundlage bestätigt sein. Kein automatisches Abwälzen versäumter Beiträge.");
        for(var item:plan.payments())if(item.payment().earnedFrom().getYear()==2025) {
            var p=item.payment();var work=old2025.stream().filter(c->contains(c,p.earnedFrom())&&contains(c,p.earnedThrough())).findFirst().orElseThrow(()->new IllegalStateException("Für die Arbeitsperiode 2025 fehlen bestätigte historische Monatsgrundlagen."));
            if(!Objects.equals(work.terms().country(),current.country()))throw new IllegalStateException("Nachzahlung über einen Länder-/Versicherungswechsel benötigt eine gesonderte Zuordnung.");
            if("CH".equals(current.country())&&PayrollPaymentLedgerService.socialMonth(p,current).getYear()==2026&&work.periodStart().getYear()<=work.terms().birthDate().getYear()+17)throw new IllegalStateException("Die historische Arbeit war noch nicht AHV-pflichtig. Einen gesonderten Beitragspflichtwechsel prüfen; aktuelle AHV-Sätze werden nicht auf minderjährige Arbeitsperioden angewandt.");
        }
        var overlay=opening?replay(user,current,imported,plan,null,true):new Replay(imported,Map.of(),Map.of());
        List<PayrollLedgerService.Entry> effective=new ArrayList<>();List<PayrollLedgerService.Reference> refs=new ArrayList<>();int index=0;
        for(var e:history.entries()) {
            if(e.original().getId()<0&&opening) {
                var c=overlay.revised().get(index++);var basis=new Payslip();basis.setId(e.original().getId());basis.setUser(user);basis.setPeriodStart(c.periodStart());basis.setPeriodEnd(c.periodEnd());basis.setGrossSalary(c.gross().total().doubleValue());basis.setDeductions(c.employee().values().stream().mapToDouble(Double::doubleValue).sum());basis.setNetSalary(basis.getGrossSalary()-basis.getDeductions());basis.setCalculationSnapshot(snapshot(c));
                effective.add(new PayrollLedgerService.Entry(basis,basis,c));refs.add(ledger.reference(basis,basis));
            }else{effective.add(e);refs.add(ledger.reference(e.original(),e.revision()));}
        }
        var revisedHistory=new PayrollLedgerService.History(List.copyOf(effective),List.copyOf(refs),history.complete());
        for(var e:effective)if(e.original().getId()>0) {
            // A previously approved financial period must first adopt the revised opening bases.
            for(var ref:refs)if(ref.originalId()<0&&e.calculation().history().stream().anyMatch(old->old.originalId().equals(ref.originalId())&&!old.equals(ref)))
                throw new IllegalStateException("Nachzahlung verändert die übernommenen Grundlagen. Korrekturlauf ab "+e.calculation().periodStart()+" zuerst vollständig freigeben.");
            var saved=e.calculation().remuneration()==null?null:e.calculation().remuneration().assessment();
            var expected=assessment(user,current,old2025,imported,plan,e.calculation().periodEnd(),List.of());
            if((saved!=null||hasCharges(expected))&&!Objects.equals(saved==null?null:saved.fingerprint(),expected==null?null:expected.fingerprint()))
                throw new IllegalStateException("Historische Nachzahlungsgrundlagen sind geändert. Korrekturlauf ab "+e.calculation().periodStart()+" zuerst freigeben.");
        }
        return new Prepared(revisedHistory,assessment(user,current,old2025,imported,plan,end,effective));
    }
    private boolean hasCharges(PayrollHistoricalAssessment a){return a!=null&&(!a.cumulativeEmployee().isEmpty()||!a.cumulativeEmployer().isEmpty());}
    private PayrollHistoricalAssessment assessment(User user,PayrollTerms current,List<PayrollCalculationService.Calculation> old2025,
            List<PayrollCalculationService.Calculation> imported,PayrollPaymentLedgerService.Plan plan,LocalDate cutoff,List<PayrollLedgerService.Entry> previous) {
        var paidItems=plan.payments().stream().filter(i->!i.payment().receivedOn().isAfter(cutoff)).toList();
        var paidIds=paidItems.stream().map(PayrollPaymentLedgerService.Item::versionId).collect(java.util.stream.Collectors.toSet());
        var paid=new PayrollPaymentLedgerService.Plan(paidItems,plan.references().stream().filter(r->paidIds.contains(r.versionId())).toList());
        boolean prior=paid.payments().stream().anyMatch(i->i.payment().earnedFrom().getYear()==2025)||!old2025.isEmpty()&&needsPrior(current,cutoff,paid);
        boolean opening=paid.payments().stream().anyMatch(i->imported.stream().anyMatch(c->contains(c,i.payment().earnedFrom())));
        boolean openingPlanned=plan.payments().stream().anyMatch(i->imported.stream().anyMatch(c->contains(c,i.payment().earnedFrom())));
        if(!prior&&!opening&&!openingPlanned)return null;
        List<PayrollCalculationService.Calculation> original=new ArrayList<>(),revised=new ArrayList<>();Map<String,Double> employee=new TreeMap<>(),employer=new TreeMap<>();
        if(prior){var result=replay(user,current,old2025,paid,cutoff,false);original.addAll(old2025);revised.addAll(result.revised());merge(employee,result.employee(),1);merge(employer,result.employer(),1);}
        if(opening){var result=replay(user,current,imported,paid,cutoff,false);original.addAll(imported);revised.addAll(result.revised());merge(employee,result.employee(),1);merge(employer,result.employer(),1);}
        for(var i:paid.payments()) {
            var p=i.payment();if(!"CH".equals(current.country())||p.earnedFrom().getYear()!=2025&&!imported.stream().anyMatch(c->contains(c,p.earnedFrom())))continue;
            if(p.bvgEmployeeExtra()==null||p.bvgEmployerExtra()==null)throw new IllegalStateException("Historische CH-Nachzahlung: zusätzliche BVG-Beiträge gemäss Vorsorgebestätigung ausdrücklich erfassen, auch 0.");
            add(employee,"BVG · Nachzahlung",p.bvgEmployeeExtra().doubleValue());add(employer,"BVG · Nachzahlung",p.bvgEmployerExtra().doubleValue());
        }
        employee=rounded(employee);employer=rounded(employer);Map<String,Double> deltaEmployee=new TreeMap<>(employee),deltaEmployer=new TreeMap<>(employer);
        for(var e:previous)if(e.original().getId()>0&&e.calculation().remuneration()!=null&&e.calculation().remuneration().assessment()!=null) {
            var a=e.calculation().remuneration().assessment();merge(deltaEmployee,a.employee(),-1);merge(deltaEmployer,a.employer(),-1);
        }
        var last=old2025.isEmpty()?null:old2025.get(old2025.size()-1);
        var openingProof=openingPlanned?replay(user,current,imported,plan,null,true).revised():List.of();
        PayrollPeriodData.GermanPriorYearSocial priorSocial=null;
        if(prior&&needsPrior(current,cutoff,paid)) {
            priorSocial=priorYear(user,current,old2025,plan).revised();
        }
        String fingerprint=hash(Arrays.asList(original,revised,paid.payments(),employee,employer,openingProof,priorSocial));
        return new PayrollHistoricalAssessment(List.copyOf(original),List.copyOf(revised),employee,employer,rounded(deltaEmployee),rounded(deltaEmployer),fingerprint,last,priorSocial);
    }
    private static boolean needsPrior(PayrollTerms t,LocalDate end,PayrollPaymentLedgerService.Plan plan) {
        return "DE".equals(t.country())&&end.getMonthValue()<=3&&t.entryDate().getYear()<2026&&(t.exitDate()==null||t.exitDate().getYear()==2026)
                &&(t.thirteenthMonths()>1||plan.payments().stream().anyMatch(i->"ONE_OFF".equals(i.payment().kind())&&i.payment().receivedOn().getMonthValue()<=3));
    }
    private List<PayrollCalculationService.Calculation> load2025(User user,PayrollTerms current) {
        List<PayrollCalculationService.Calculation> result=new ArrayList<>();LocalDate cursor=current.entryDate().isAfter(LocalDate.of(2025,1,1))?current.entryDate():LocalDate.of(2025,1,1);
        var until=current.exitDate()!=null&&current.exitDate().isBefore(LocalDate.of(2025,12,31))?current.exitDate():LocalDate.of(2025,12,31);
        for(var saved:inputs.historicalYear(user,2025)) {
            var p=saved.reference().data().historicalBaseline();if(p==null||!p.start().equals(cursor))throw new IllegalStateException("Die Altgrundlagen 2025 müssen seit Januar/Eintritt vollständig und lückenlos vorliegen.");
            var resolved=terms.resolve(user,p.start(),p.end());var t=resolved.terms();TaxCalculationService.validateEmploymentPeriod(t,p.start(),p.end(),2025);
            PayrollPeriodInputService.validate(saved.reference().data(),t,p.start(),p.end());
            var gross=new PayrollGrossCalculator.Result(p.base(),p.premium(),p.overtimePayout(),p.base().add(p.premium()).add(p.overtimePayout()).add(p.thirteenth()),0,p.thirteenth(),null,p.thirteenthEligibleBase()==null?p.base():p.thirteenthEligibleBase());
            int days="CH".equals(t.country())?PayrollPeriod.swissDays(p.start(),p.end()):PayrollPeriod.fullMonth(p.start(),p.end())?30:Math.min(30,(int)java.time.temporal.ChronoUnit.DAYS.between(p.start(),p.end())+1);
            if("DE".equals(t.country())&&t.multipleEmployment()!=null&&saved.reference().data().germanConcurrentIncome()!=null)days=GermanConcurrentSettlement.calculate(gross.total(),p.start(),p.end(),saved.reference().data().germanConcurrentIncome(),new BigDecimal("8050"),new BigDecimal("5512.50")).frameDays();
            if(days!=p.insuranceDays())throw new IllegalStateException("Historische Versicherungstage stimmen nicht mit dem Anstellungs-/SV-Rahmenzeitraum überein.");
            SwissPayrollSettlement.Basis ch=null;GermanPayrollTax.Basis de=null;Map<String,Double> tax;
            if("CH".equals(t.country())) {
                var allocations=p.taxAllocations()==null?null:p.taxAllocations().stream().map(a->new SwissWithholdingSettlement.Allocation("CH_SOURCE:"+a.canton()+":"+a.from(),a.canton(),a.from(),a.amount(),null,null)).toList();
                var rate=SwissWithholdingSettlement.calculate(sourceTax,t,p.start(),p.end(),gross,new PayrollLedgerService.History(result.stream().map(PayrollHistoricalSettlementService::entry).toList(),List.of(),true),saved.reference().data());
                if(allocations!=null){Set<String> phases=new HashSet<>();String phase=null;PayrollTerms previous=null;for(var row:result){if(previous==null||!Objects.equals(previous.taxMode(),row.terms().taxMode())||!Objects.equals(previous.canton(),row.terms().canton()))phase=row.terms().taxMode()+":"+Objects.toString(row.terms().canton(),"")+":"+row.periodStart();if("CH_SOURCE".equals(row.terms().taxMode()))phases.add(phase);previous=row.terms();}
                    if(previous==null||!Objects.equals(previous.taxMode(),t.taxMode())||!Objects.equals(previous.canton(),t.canton()))phase=t.taxMode()+":"+Objects.toString(t.canton(),"")+":"+p.start();if("CH_SOURCE".equals(t.taxMode()))phases.add(phase);
                    for(var a:allocations)if(!phases.contains(a.key()))throw new IllegalStateException("Historische Quellensteuer verweist auf keinen belegten Kantonsabschnitt.");
                }
                ch=new SwissPayrollSettlement.Basis(days,p.alvUvgBase(),p.ktgBase(),rate.annualRate(),rate.monthlyRate(),p.ktgAnnualCap(),p.ktgPool(),allocations);tax=Map.of("Withholding tax",p.withholding().doubleValue());
            }else{int yearDays=days;BigDecimal rv=p.pensionBase(),kv=p.healthBase();for(var previous:result){yearDays+=previous.germanBasis().insuranceDays();rv=rv.add(previous.germanBasis().pensionBase());kv=kv.add(previous.germanBasis().healthBase());}
                if(rv.compareTo(new BigDecimal("96600").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP))>0||kv.compareTo(new BigDecimal("66150").multiply(BigDecimal.valueOf(yearDays)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP))>0)throw new IllegalStateException("Historische SV-Jahresbasen übersteigen die belegten zeitanteiligen 2025-Grenzen.");
                de=new GermanPayrollTax.Basis(days,p.pensionBase(),p.healthBase(),null);tax=Map.of("Income tax",p.withholding().doubleValue(),"Solidarity surcharge",p.solidarityTax().doubleValue(),"Church tax",p.churchTax().doubleValue());}
            result.add(new PayrollCalculationService.Calculation(resolved.versionId(),t,p.start(),p.end(),List.of(),gross,tax,Map.of(),"Altbasis #"+saved.reference().versionId()+": "+saved.reference().source(),ch,de,List.of(),null,saved.reference()));cursor=p.end().plusDays(1);
        }
        if(!cursor.equals(until.plusDays(1)))throw new IllegalStateException("Die bestätigten Altgrundlagen 2025 fehlen bis zum Jahresende/Austritt.");
        return List.copyOf(result);
    }
    private Replay replay(User user,PayrollTerms current,List<PayrollCalculationService.Calculation> original,PayrollPaymentLedgerService.Plan plan,LocalDate cutoff,boolean openingOverlay) {
        List<PayrollCalculationService.Calculation> revised=new ArrayList<>();List<PayrollLedgerService.Entry> oldEntries=new ArrayList<>(),oldSocialEntries=new ArrayList<>(),newEntries=new ArrayList<>(),taxEntries=new ArrayList<>();
        Map<String,Double> employee=new TreeMap<>(),employer=new TreeMap<>();
        PriorYear priorYear=null;
        if(!original.isEmpty()&&original.get(0).periodStart().getYear()==2026&&"DE".equals(current.country())&&current.entryDate().getYear()<2026
                &&(original.stream().anyMatch(c->c.periodStart().getMonthValue()<=3&&c.terms().thirteenthMonths()>1&&c.gross().thirteenthAmount().signum()>0)
                    ||plan.payments().stream().anyMatch(i->"ONE_OFF".equals(i.payment().kind())&&i.payment().receivedOn().getMonthValue()<=3))&&!inputs.historicalYear(user,2025).isEmpty())
            priorYear=priorYear(user,current,load2025(user,current),plan);
        for(var c:original) {
            var t=c.terms();var periodReference=c.periodInputs();
            if(c.periodStart().getYear()==2026)periodReference=inputs.resolve(user,c.periodStart(),c.periodEnd()).orElse(periodReference);
            var periodData=periodReference==null?null:periodReference.data();
            BigDecimal social=BigDecimal.ZERO,oneOffSocial=BigDecimal.ZERO,regularTax=BigDecimal.ZERO,otherTax=BigDecimal.ZERO;
            for(var i:plan.payments()) {
                var p=i.payment();if(YearMonth.from(PayrollPaymentLedgerService.socialMonth(p,current)).equals(YearMonth.from(c.periodStart()))) {
                    if("DE".equals(t.country())&&"ONE_OFF".equals(p.kind())) {if(c.periodStart().getYear()==2026||p.receivedOn().getMonthValue()<=3)oneOffSocial=oneOffSocial.add(p.gross());}
                    else social=social.add(p.gross());
                }
                if(YearMonth.from(PayrollPaymentLedgerService.taxMonth(p,current)).equals(YearMonth.from(c.periodStart()))) {
                    if("DE".equals(t.country()))regularTax=regularTax.add(p.gross());else otherTax=otherTax.add(p.gross());
                }
            }
            var socialGross=addGross(c.gross(),social,oneOffSocial);var taxGross=addGross(c.gross(),regularTax,otherTax);
            var oldHistory=new PayrollLedgerService.History(List.copyOf(oldEntries),List.of(),true);var newHistory=new PayrollLedgerService.History(List.copyOf(newEntries),List.of(),true);
            TaxCalculationService.Result before,after;
            if("CH".equals(t.country())) {
                var coverage=configuration.resolve(user,c.periodStart(),c.periodEnd());if(!coverage.insuranceComplete()||!coverage.pensionComplete())throw new IllegalStateException("Für historische Nachzahlungen fehlen die damals gültigen Versicherungs-/BVG-Versionen.");
                var oldData=periodData;
                if(periodData!=null&&periodData.historicalInsuranceAllocations()!=null)oldData=new PayrollPeriodData(periodData.absences(),periodData.germanConcurrentIncome(),periodData.swissOtherAperiodicGross(),periodData.historicalInsuranceAllocations(),null,null);
                before=swiss(coverage,t,c,c.gross(),oldHistory,oldData);after=swiss(coverage,t,c,socialGross,newHistory,periodData);
                if(before.getSwissBasis().alvUvgBase().compareTo(c.swissBasis().alvUvgBase())!=0||before.getSwissBasis().ktgBase().compareTo(c.swissBasis().ktgBase())!=0)throw new IllegalStateException("Die ursprünglichen Schweizer SV-Basen passen nicht zum belegten Lohn und den damaligen Policen. Alt-/Eröffnungsgrundlagen anhand der Originalbelege prüfen.");
            }else if(c.periodStart().getYear()==2026) {
                var supplied=periodData==null?null:periodData.germanPriorYearSocial();
                if(priorYear!=null&&supplied!=null)validatePrior(supplied,priorYear.original());
                var beforePrior=priorYear==null?supplied:withOriginalAllocation(priorYear.original(),supplied);
                var afterPrior=priorYear==null?withoutAllocation(supplied):priorYear.revised();
                var beforeData=withPrior(periodData,beforePrior);var afterData=withPrior(periodData,afterPrior);
                var oldAllocation=new PayrollRemuneration(c.gross(),c.gross(),List.of(),List.of(),false);
                before=GermanSocialPayments.calculate(t,c.gross(),oldAllocation,c.periodStart(),c.periodEnd(),new PayrollLedgerService.History(List.copyOf(oldSocialEntries),List.of(),true),beforeData);
                var oldBasis=before.getGermanBasis();
                if(oldBasis.insuranceDays()!=c.germanBasis().insuranceDays()||oldBasis.pensionBase().compareTo(c.germanBasis().pensionBase())!=0||oldBasis.healthBase().compareTo(c.germanBasis().healthBase())!=0)throw new IllegalStateException("Die ursprünglichen deutschen Eröffnungs-SV-Basen passen nicht zum belegten Lohn und den damaligen Regeln.");
                if(oldBasis.priorYear()!=null&&(supplied==null||supplied.pensionAllocatedBase()==null||supplied.healthAllocatedBase()==null))throw new IllegalStateException("Eröffnungs-13.-Monatslohn mit ursprünglicher Märzklausel: Die tatsächlich bereits zugeordneten 2025-Einmalzahlungsbasen als datierte Monatsgrundlage ausdrücklich belegen.");
                if(oldBasis.priorYear()!=null&&t.multipleEmployment()!=null)throw new IllegalStateException("Eröffnungs-Märzklausel bei Mehrfachbeschäftigung benötigt getrennte ursprüngliche und revidierte Einzugsstellen-Zuordnungen; keine automatische Neuaufteilung.");
                if(priorYear!=null)for(var item:plan.payments())if(item.priorSocial()!=null&&YearMonth.from(PayrollPaymentLedgerService.socialMonth(item.payment(),current)).equals(YearMonth.from(c.periodStart())))validatePrior(item.priorSocial(),priorYear.original());
                var allocation=PayrollPaymentLedgerService.apply(plan,current,c.periodStart(),c.periodEnd(),c.gross(),false).remuneration();
                after=GermanSocialPayments.calculate(t,c.gross(),allocation,c.periodStart(),c.periodEnd(),newHistory,afterData);
            }else {
                before=new TaxCalculationService.Result(Map.of(),Map.of()).withGermanBasis(c.germanBasis());
                after=germanSocial(t,c,social,oneOffSocial,original,revised,periodData,plan,current);
            }
            String suffix=" · Altperiode "+c.periodStart();
            // Historical pension extras are confirmed per actual payment, never charged again from coverage slices.
            if("CH".equals(t.country())){var beforeEmployee=new TreeMap<>(before.getEmployee());var afterEmployee=new TreeMap<>(after.getEmployee());var beforeEmployer=new TreeMap<>(before.getEmployer());var afterEmployer=new TreeMap<>(after.getEmployer());for(var map:List.of(beforeEmployee,afterEmployee,beforeEmployer,afterEmployer))map.remove("BVG");difference(employee,afterEmployee,beforeEmployee,suffix);difference(employer,afterEmployer,beforeEmployer,suffix);}
            else{difference(employee,after.getEmployee(),before.getEmployee(),suffix);difference(employer,after.getEmployer(),before.getEmployer(),suffix);}
            Map<String,Double> deductions=new TreeMap<>(c.employee());
            boolean earlierRegular2025="DE".equals(t.country())&&c.periodStart().getYear()==2025&&plan.payments().stream().anyMatch(i->i.payment().earnedFrom().getYear()==2025&&PayrollPaymentLedgerService.germanRegularTax(i.payment(),current));
            boolean earlierRegular2026="DE".equals(t.country())&&c.periodStart().getYear()==2026&&plan.payments().stream().anyMatch(i->PayrollPaymentLedgerService.germanRegularTax(i.payment(),current)
                    &&PayrollPaymentLedgerService.taxMonth(i.payment(),current).getYear()==2026&&!PayrollPaymentLedgerService.taxMonth(i.payment(),current).isAfter(c.periodEnd()));
            if(regularTax.signum()!=0||otherTax.signum()!=0||(earlierRegular2025||earlierRegular2026)&&t.thirteenthMonths()!=1&&c.gross().thirteenthAmount().signum()>0) {
                Map<String,Double> target;
                if("CH".equals(t.country())) {var tax=SwissWithholdingSettlement.calculate(sourceTax,t,c.periodStart(),c.periodEnd(),taxGross,new PayrollLedgerService.History(oldEntries,List.of(),true),periodData);target=Map.of("Withholding tax",tax.amount().doubleValue());}
                else {
                    var oneOff=t.thirteenthMonths()==1?BigDecimal.ZERO:c.gross().thirteenthAmount();BigDecimal annual=null;
                    if(c.periodStart().getYear()==2025) {
                        annual=t.contract().germanPreviousEmploymentGross();if(annual==null)throw new IllegalStateException("Für historische sonstige Bezüge das belegte Vorarbeitgeber-Brutto 2025 ausdrücklich erfassen.");
                        for(var originalPeriod:original){annual=annual.add(originalPeriod.gross().total());if(originalPeriod.terms().thirteenthMonths()!=1&&!originalPeriod.periodStart().isBefore(c.periodStart()))annual=annual.subtract(originalPeriod.gross().thirteenthAmount());}
                        for(var item:plan.payments())if(item.payment().earnedFrom().getYear()==2025&&PayrollPaymentLedgerService.germanRegularTax(item.payment(),current))annual=annual.add(item.payment().gross());
                    }
                    target=c.periodStart().getYear()==2025?GermanHistoricalTax.taxes(t,taxGross,c.periodStart(),c.periodEnd(),oldHistory,oneOff,annual):GermanPayrollTax.taxes(t,taxGross,c.periodStart(),c.periodEnd(),new PayrollLedgerService.History(List.copyOf(taxEntries),List.of(),true),oneOff);
                }
                difference(employee,target,c.employee(),suffix);
            }
            var applied=PayrollPaymentLedgerService.apply(plan,current,c.periodStart(),c.periodEnd(),c.gross(),false).remuneration();
            var usedIds=applied.applied().stream().map(PayrollRemuneration.PaymentReference::versionId).collect(java.util.stream.Collectors.toSet());
            var allocation=new PayrollRemuneration(taxGross,socialGross,applied.applied(),applied.plan().stream().filter(r->usedIds.contains(r.versionId())).toList(),false);
            var swiss=after.getSwissBasis();
            if(swiss!=null)swiss=new SwissPayrollSettlement.Basis(swiss.insuranceDays(),swiss.alvUvgBase(),swiss.ktgBase(),c.swissBasis().annualRateIncome(),c.swissBasis().monthlyRateIncome(),swiss.ktgAnnualSalaryCap(),swiss.ktgPool(),c.swissBasis().withholdingAllocations(),swiss.insuranceSlices());
            // Only original paid tax is credited here. New historical tax is charged on the receipt document.
            var b=new PayrollCalculationService.Calculation(c.rulesVersion(),t,c.periodStart(),c.periodEnd(),List.of(),c.gross(),deductions,Map.of(),after.getConfigurationReference(),swiss,after.getGermanBasis(),List.of(),null,periodReference,allocation);
            revised.add(b);oldEntries.add(entry(c));oldSocialEntries.add(entry(new PayrollCalculationService.Calculation(c.rulesVersion(),t,c.periodStart(),c.periodEnd(),List.of(),c.gross(),c.employee(),c.employer(),c.taxReference(),c.swissBasis(),before.getGermanBasis(),List.of(),null,c.periodInputs())));newEntries.add(entry(b.withGross(socialGross)));taxEntries.add(entry(b.withGross(taxGross)));
        }
        return new Replay(List.copyOf(revised),rounded(employee),rounded(employer));
    }
    private PriorYear priorYear(User user,PayrollTerms current,List<PayrollCalculationService.Calculation> original,PayrollPaymentLedgerService.Plan plan){
        // Regular 2025 arrears alter headroom even if their actual receipt is later in 2026.
        var regularPlan=new PayrollPaymentLedgerService.Plan(plan.payments().stream().filter(i->i.payment().earnedFrom().getYear()==2025&&PayrollPaymentLedgerService.regular(i.payment())&&PayrollPaymentLedgerService.socialMonth(i.payment(),current).getYear()==2025).toList(),plan.references());
        return new PriorYear(priorTotals(original,"Bestätigte ursprüngliche Monatsgrundlagen 2025"),priorTotals(replay(user,current,original,regularPlan,null,false).revised(),"Bestätigte Monatsgrundlagen 2025 einschliesslich regulärer Chrono-Nachzahlungen"));
    }
    private static PayrollPeriodData.GermanPriorYearSocial priorTotals(List<PayrollCalculationService.Calculation> periods,String source){
        int days=0;BigDecimal rv=BigDecimal.ZERO,kv=BigDecimal.ZERO;for(var c:periods){days+=c.germanBasis().insuranceDays();rv=rv.add(c.germanBasis().pensionBase());kv=kv.add(c.germanBasis().healthBase());}var last=periods.get(periods.size()-1);var t=last.terms();
        return new PayrollPeriodData.GermanPriorYearSocial(last.periodEnd(),days,rv,kv,t.healthAdditionalRate(),t.parent(),t.childrenUnder25(),t.saxony(),null,null,source,true);
    }
    private static void validatePrior(PayrollPeriodData.GermanPriorYearSocial actual,PayrollPeriodData.GermanPriorYearSocial expected){
        if(!actual.assignmentDate().equals(expected.assignmentDate())||!actual.insuranceDays().equals(expected.insuranceDays())||actual.pensionAssessed().compareTo(expected.pensionAssessed())!=0||actual.healthAssessed().compareTo(expected.healthAssessed())!=0||actual.healthAdditionalRate().compareTo(expected.healthAdditionalRate())!=0||!actual.parent().equals(expected.parent())||!actual.childrenUnder25().equals(expected.childrenUnder25())||!actual.saxony().equals(expected.saxony()))throw new IllegalStateException("Erfasste Vorjahres-SV widerspricht den bestätigten ursprünglichen Monatsgrundlagen 2025.");
    }
    private static PayrollPeriodData.GermanPriorYearSocial withOriginalAllocation(PayrollPeriodData.GermanPriorYearSocial p,PayrollPeriodData.GermanPriorYearSocial allocation){return new PayrollPeriodData.GermanPriorYearSocial(p.assignmentDate(),p.insuranceDays(),p.pensionAssessed(),p.healthAssessed(),p.healthAdditionalRate(),p.parent(),p.childrenUnder25(),p.saxony(),allocation==null?null:allocation.pensionAllocatedBase(),allocation==null?null:allocation.healthAllocatedBase(),p.source(),true);}
    private static PayrollPeriodData.GermanPriorYearSocial withoutAllocation(PayrollPeriodData.GermanPriorYearSocial p){return p==null?null:withOriginalAllocation(p,null);}
    private static PayrollPeriodData withPrior(PayrollPeriodData data,PayrollPeriodData.GermanPriorYearSocial p){return data==null?new PayrollPeriodData(List.of(),null,null,null,null,p):new PayrollPeriodData(data.absences(),data.germanConcurrentIncome(),data.swissOtherAperiodicGross(),data.swissInsuranceAllocations(),data.payments(),p,data.historicalBaseline(),data.historicalInsuranceAllocations());}
    private TaxCalculationService.Result swiss(PayrollConfigurationService.Coverage coverage,PayrollTerms t,PayrollCalculationService.Calculation c,
            PayrollGrossCalculator.Result gross,PayrollLedgerService.History history,PayrollPeriodData data) {
        return coverage.insuranceChangesWithinPeriod()||coverage.pensionChangesWithinPeriod()||coverage.insurance().getUvgPoolKey()!=null||data!=null&&data.swissInsuranceAllocations()!=null
                ?SwissInsuranceSettlement.calculate(sourceTax,coverage,t,c.periodStart(),c.periodEnd(),gross,history,data,false)
                :SwissPayrollSettlement.calculate(sourceTax,coverage,t,c.periodStart(),c.periodEnd(),gross,history,data,false);
    }
    private TaxCalculationService.Result germanSocial(PayrollTerms t,PayrollCalculationService.Calculation c,BigDecimal extra,BigDecimal newOneOff,
            List<PayrollCalculationService.Calculation> original,List<PayrollCalculationService.Calculation> revised,PayrollPeriodData data,PayrollPaymentLedgerService.Plan plan,PayrollTerms current) {
        var base=c.germanBasis();if(base==null)throw new IllegalStateException("Historische deutsche SV-Basen fehlen.");int year=c.periodStart().getYear();
        var rvCap=new BigDecimal(year==2025?"8050":"8450").multiply(BigDecimal.valueOf(base.insuranceDays())).divide(BigDecimal.valueOf(30),2,RoundingMode.HALF_UP);
        var kvCap=new BigDecimal(year==2025?"5512.50":"5812.50").multiply(BigDecimal.valueOf(base.insuranceDays())).divide(BigDecimal.valueOf(30),2,RoundingMode.HALF_UP);
        BigDecimal oldBonus=t.thirteenthMonths()==1?BigDecimal.ZERO:c.gross().thirteenthAmount(),regular=c.gross().total().subtract(oldBonus);
        BigDecimal combinedMonthly=t.hourly()?regular.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(base.insuranceDays()),12,RoundingMode.HALF_UP):t.amount();
        var concurrentStatus=data==null?null:data.germanConcurrentIncome();
        if(concurrentStatus!=null){if(concurrentStatus.employments()==null||concurrentStatus.employments().isEmpty())combinedMonthly=combinedMonthly.add(concurrentStatus.otherPensionGross().multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(base.insuranceDays()),12,RoundingMode.HALF_UP));
            else for(var employment:concurrentStatus.employments())if(!employment.from().isAfter(c.periodEnd())&&!employment.through().isBefore(c.periodStart()))combinedMonthly=combinedMonthly.add(employment.pensionGross().multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(GermanConcurrentSettlement.days(employment.from(),employment.through())),12,RoundingMode.HALF_UP));}
        if(combinedMonthly.compareTo(new BigDecimal("2000"))<=0)throw new IllegalStateException("Historische Mini-/Midijobs benötigen eigene Sozialversicherungsregeln; keine regulären Sätze auf nicht abgedeckte Altgrundlagen anwenden.");
        BigDecimal oldRv=regular.min(rvCap),oldKv=regular.min(kvCap),newRv=regular.add(extra).min(rvCap),newKv=regular.add(extra).min(kvCap);
        if(t.multipleEmployment()!=null) {
            var concurrent=data==null?null:data.germanConcurrentIncome();if(concurrent==null||!Boolean.TRUE.equals(concurrent.fundAllocationConfirmed()))throw new IllegalStateException("Historische Mehrfachbeschäftigung benötigt die bestätigten Entgelte und SV-Zeiträume aller Arbeitgeber.");
            var before=GermanConcurrentSettlement.calculate(regular,c.periodStart(),c.periodEnd(),concurrent,new BigDecimal(year==2025?"8050":"8450"),new BigDecimal(year==2025?"5512.50":"5812.50"));
            var after=GermanConcurrentSettlement.calculate(regular.add(extra),c.periodStart(),c.periodEnd(),concurrent,new BigDecimal(year==2025?"8050":"8450"),new BigDecimal(year==2025?"5512.50":"5812.50"));oldRv=before.pension();oldKv=before.health();newRv=after.pension();newKv=after.health();
        }
        BigDecimal oldBonusRv=base.pensionBase().subtract(oldRv),oldBonusKv=base.healthBase().subtract(oldKv);
        if(oldBonusRv.signum()<0||oldBonusKv.signum()<0||oldBonusRv.compareTo(oldBonus)>0||oldBonusKv.compareTo(oldBonus)>0)throw new IllegalStateException("Belegte historische SV-Basen passen nicht zu regulärem Lohn und gesonderten Einmalzahlungen; Altgrundlagen prüfen.");
        int days=base.insuranceDays();BigDecimal previousRv=BigDecimal.ZERO,previousKv=BigDecimal.ZERO;
        for(var p:revised){days+=p.germanBasis().insuranceDays();previousRv=previousRv.add(p.germanBasis().pensionBase());previousKv=previousKv.add(p.germanBasis().healthBase());}
        var maxRv=new BigDecimal(year==2025?"96600":"101400").multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP).subtract(previousRv).subtract(newRv).max(BigDecimal.ZERO);
        var maxKv=new BigDecimal(year==2025?"66150":"69750").multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360),2,RoundingMode.HALF_UP).subtract(previousKv).subtract(newKv).max(BigDecimal.ZERO);
        var retainedRv=oldBonusRv.min(maxRv);var retainedKv=oldBonusKv.min(maxKv);
        var bonusRv=newOneOff.min(maxRv.subtract(retainedRv));var bonusKv=newOneOff.min(maxKv.subtract(retainedKv));
        if(t.multipleEmployment()!=null&&newOneOff.signum()>0) {
            Set<Long> owners=new HashSet<>();BigDecimal allocatedRv=BigDecimal.ZERO,allocatedKv=BigDecimal.ZERO,originalRv=BigDecimal.ZERO,originalKv=BigDecimal.ZERO;int originalDays=0;
            for(var p:original){originalRv=originalRv.add(p.germanBasis().pensionBase());originalKv=originalKv.add(p.germanBasis().healthBase());originalDays+=p.germanBasis().insuranceDays();}
            var last=original.get(original.size()-1);
            for(var item:plan.payments())if("ONE_OFF".equals(item.payment().kind())&&item.payment().receivedOn().getMonthValue()<=3&&YearMonth.from(PayrollPaymentLedgerService.socialMonth(item.payment(),current)).equals(YearMonth.from(c.periodStart()))&&owners.add(item.versionId())) {
                var p=item.priorSocial();if(p==null||p.pensionAllocatedBase()==null||p.healthAllocatedBase()==null||!Boolean.TRUE.equals(p.confirmed()))throw new IllegalStateException("Historische Einmalzahlung: bestätigte eigene 2025-Einmalzahlungsbasen der Einzugsstelle erfassen.");
                if(!p.assignmentDate().equals(last.periodEnd())||p.insuranceDays()!=originalDays||p.pensionAssessed().compareTo(originalRv)!=0||p.healthAssessed().compareTo(originalKv)!=0)throw new IllegalStateException("Die Einzugsstellen-Grundlagen widersprechen den ursprünglichen 2025-Monatsbasen.");
                allocatedRv=allocatedRv.add(p.pensionAllocatedBase());allocatedKv=allocatedKv.add(p.healthAllocatedBase());
            }
            if(allocatedRv.compareTo(bonusRv)>0||allocatedKv.compareTo(bonusKv)>0)throw new IllegalStateException("Gemeldete historische Einmalzahlungsbasen übersteigen den verbleibenden eigenen 2025-Spielraum.");bonusRv=allocatedRv;bonusKv=allocatedKv;
        }
        BigDecimal rv=newRv.subtract(oldRv).add(retainedRv).add(bonusRv).subtract(oldBonusRv),kv=newKv.subtract(oldKv).add(retainedKv).add(bonusKv).subtract(oldBonusKv);
        boolean surcharge=!t.parent()&&!c.periodStart().isBefore(t.birthDate().plusYears(23).withDayOfMonth(1).plusMonths(1));int discounts=t.parent()?Math.min(4,Math.max(0,t.childrenUnder25()-1)):0;
        var health=new BigDecimal("0.073").add(t.healthAdditionalRate().divide(BigDecimal.valueOf(2)));var care=new BigDecimal(t.saxony()?"0.023":"0.018").add(surcharge?new BigDecimal("0.006"):new BigDecimal("-0.0025").multiply(BigDecimal.valueOf(discounts)));
        Map<String,Double> employee=new TreeMap<>(),employer=new TreeMap<>();
        for(var side:List.of(employee,employer)){
            contributionDifference(side,"Pension insurance",oldRv,newRv,oldBonusRv,retainedRv.add(bonusRv),new BigDecimal("0.093"));
            contributionDifference(side,"Unemployment insurance",oldRv,newRv,oldBonusRv,retainedRv.add(bonusRv),new BigDecimal("0.013"));
            contributionDifference(side,"Health insurance",oldKv,newKv,oldBonusKv,retainedKv.add(bonusKv),health);
            contributionDifference(side,"Nursing insurance",oldKv,newKv,oldBonusKv,retainedKv.add(bonusKv),side==employee?care:new BigDecimal(t.saxony()?"0.013":"0.018"));
        }
        contributionDifference(employer,"U1",oldRv,newRv,BigDecimal.ZERO,BigDecimal.ZERO,t.u1Rate());contributionDifference(employer,"U2",oldRv,newRv,BigDecimal.ZERO,BigDecimal.ZERO,t.u2Rate());
        contributionDifference(employer,"Insolvency levy",oldRv,newRv,oldBonusRv,retainedRv.add(bonusRv),t.insolvencyRate());
        return new TaxCalculationService.Result(employee,employer,"SV "+year+"; historische Regel #"+c.rulesVersion()).withGermanBasis(new GermanPayrollTax.Basis(base.insuranceDays(),base.pensionBase().add(rv),base.healthBase().add(kv),null));
    }
    private static void contributionDifference(Map<String,Double> map,String key,BigDecimal oldRegular,BigDecimal newRegular,BigDecimal oldBonus,BigDecimal newBonus,BigDecimal rate){
        BigDecimal before=oldRegular.multiply(rate).setScale(2,RoundingMode.HALF_UP).add(oldBonus.multiply(rate).setScale(2,RoundingMode.HALF_UP));
        BigDecimal after=newRegular.multiply(rate).setScale(2,RoundingMode.HALF_UP).add(newBonus.multiply(rate).setScale(2,RoundingMode.HALF_UP));add(map,key,after.subtract(before).doubleValue());
    }
    private static PayrollGrossCalculator.Result addGross(PayrollGrossCalculator.Result g,BigDecimal regular,BigDecimal other){return new PayrollGrossCalculator.Result(g.base().add(regular),g.premium().add(other),g.payout(),g.total().add(regular).add(other),0,g.thirteenthAmount(),g.extraEarnings(),g.eligibleThirteenthBase());}
    private static boolean contains(PayrollCalculationService.Calculation c,LocalDate d){return !d.isBefore(c.periodStart())&&!d.isAfter(c.periodEnd());}
    private static PayrollLedgerService.Entry entry(PayrollCalculationService.Calculation c){var p=new Payslip();p.setId(0L);return new PayrollLedgerService.Entry(p,p,c);}
    private static void difference(Map<String,Double> target,Map<String,Double> after,Map<String,Double> before,String suffix){Set<String> keys=new TreeSet<>(after.keySet());keys.addAll(before.keySet());for(String k:keys)add(target,k+suffix,after.getOrDefault(k,0.0)-before.getOrDefault(k,0.0));}
    private static void add(Map<String,Double> target,String key,double value){if(value!=0)target.merge(key,value,Double::sum);}
    private static void merge(Map<String,Double> target,Map<String,Double> other,int sign){other.forEach((k,v)->add(target,k,v*sign));}
    private static Map<String,Double> rounded(Map<String,Double> input){Map<String,Double> result=new TreeMap<>();input.forEach((k,v)->{double n=BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).doubleValue();if(n!=0)result.put(k,n);});return Collections.unmodifiableMap(result);}
    private String snapshot(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(Object o){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot(o).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
