package com.chrono.chrono.services;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import({PayrollService.class,PayrollCalculationService.class,PayrollLedgerService.class,PayrollApprovalGuard.class,PayrollCorrectionService.class,
        PayrollPeriodInputService.class,PayrollPaymentLedgerService.class,PayrollTermsService.class,PayrollConfigurationService.class,TaxCalculationService.class,SwissSourceTax.class,PayrollTermsIntegrationTest.Json.class})
class PayrollPaymentsIntegrationTest {
    @MockitoBean TimeTrackingService tracking;@MockitoBean EmploymentModelHistoryService employment;@MockitoBean HolidayService holidays;
    @MockitoBean EmailService mail;@MockitoBean PdfService pdf;@MockitoBean AccountingService accounting;
    @Autowired PayrollService payroll;@Autowired PayrollCorrectionService corrections;@Autowired PayrollPeriodInputService inputs;@Autowired PayrollPaymentLedgerService payments;
    @Autowired PayrollTermsService terms;@Autowired PayrollConfigurationService configuration;@Autowired PayrollLedgerService ledger;
    @Autowired CompanyRepository companies;@Autowired UserRepository users;@Autowired PayslipRepository slips;@Autowired ObjectMapper json;
    User user;PayrollTerms rule;LocalDate jan=LocalDate.of(2026,1,1),march=LocalDate.of(2026,3,1);
    @BeforeEach void setup() {
        var c=companies.saveAndFlush(new Company("Synthetic payment fixture"));var u=new User();u.setCompany(c);u.setUsername("payment-"+c.getId());u.setPersonnelNumber("payment-"+c.getId());u.setPassword("test-only");u.setCountry("CH");u.setIsHourly(false);u.setDailyWorkHours(8.0);u.setExpectedWorkDays(5);u.setTrackingBalanceInMinutes(120);user=users.saveAndFlush(u);
        BigDecimal zero=BigDecimal.ZERO;configuration.addInsurance(c.getId(),user.getId(),new PayrollConfigurationService.InsuranceInput(jan,"Synthetic policy",zero,zero,zero,zero,zero,zero,zero,zero));
        configuration.addPension(c.getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),jan,"Synthetic pension",zero,zero));
        when(tracking.getUserPeriodSummary(any(),any(),any())).thenAnswer(i->new TimePeriodSummaryDTO("fixture",i.getArgument(1),i.getArgument(2),0,0,0,0,0,List.of()));
        when(employment.resolveUserSnapshotsForRange(any(),any(),any())).thenAnswer(i->Map.of(jan,i.getArgument(0)));when(pdf.generatePayslipPdf(any())).thenReturn("synthetic-payment.pdf");
    }
    void contract(String country,String canton,String exit) {
        Map<String,Object> c=new LinkedHashMap<>();c.put("salaryProration","THIRTY_DAYS");c.put("pensionProration","FULL");c.put("thirteenthFrequency","NONE");c.put("germanPreviousEmploymentGross","0");if(exit!=null)c.put("exitDate",exit);
        var changes=new LinkedHashMap<String,Object>();changes.put("entryDate","2026-01-01");changes.put("contract",c);
        if(canton!=null){changes.put("taxMode","CH_SOURCE");changes.put("canton",canton);changes.put("tariffCode","A0N");}
        rule=PayrollTestTerms.change("DE".equals(country)?PayrollTestTerms.german():PayrollTestTerms.swiss(),changes);
        terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),jan,"Synthetic contract",rule));
    }
    Payslip create(int month,boolean approve) {return create(LocalDate.of(2026,month,1),approve);}
    Payslip create(LocalDate start,boolean approve) {var p=payroll.generatePayslip(user.getId(),start,start.withDayOfMonth(start.lengthOfMonth()),null);if(approve)payroll.approvePayslip(p.getId(),"fixture");return p;}
    PayrollPayment payment(String reference,String kind,String received,String claim){return new PayrollPayment(reference,kind,jan,jan.withDayOfMonth(31),LocalDate.parse(received),LocalDate.parse(claim),new BigDecimal("500.00"),true);}
    void add(LocalDate start,List<PayrollPayment> rows) {inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),start,start.withDayOfMonth(start.lengthOfMonth()),"Verified synthetic payment source",true,new PayrollPeriodData(List.of(),null,null,null,rows)));}
    void correct(Payslip original,String date) {var batch=corrections.generate(original.getId(),new PayrollCorrectionService.Request("Verified arrears allocation",LocalDate.parse(date),null),"fixture");payroll.approveBatch(batch.stream().map(Payslip::getId).toList(),"checked");}
    String fingerprint(Payslip p){return p.getGrossSalary()+":"+p.getDeductions()+":"+p.getNetSalary()+":"+p.getCalculationSnapshot()+":"+p.getPdfPath()+":"+p.getVersion();}
    @Test void germanRegularArrearsReconcileOriginalTaxAndSvButPayCashOnlyOnceInReceiptMonth() {
        contract("DE",null,null);var january=create(1,true);var february=create(2,true);String old=fingerprint(january),oldFeb=fingerprint(february);
        add(march,List.of(payment("DE-regular","REGULAR_ARREARS","2026-03-15","2026-01-31")));
        assertThrows(IllegalStateException.class,()->create(3,false));correct(january,"2026-03-15");
        var effective=ledger.read(ledger.latest(january));assertEquals(5000.0,effective.gross().total().doubleValue());assertEquals(5500.0,effective.remuneration().taxGross().total().doubleValue());assertEquals(5500.0,effective.germanBasis().pensionBase().doubleValue());
        var receipt=create(3,true);var calculation=ledger.read(receipt);assertEquals(5500.0,receipt.getGrossSalary());assertEquals(5000.0,calculation.remuneration().taxGross().total().doubleValue());assertEquals(5000.0,calculation.germanBasis().pensionBase().doubleValue());
        assertEquals(782.41,calculation.employee().get("Income tax"));assertEquals(465.0,calculation.employee().get("Pension insurance"));
        assertEquals(old,fingerprint(january));assertEquals(oldFeb,fingerprint(february));assertEquals(120,user.getTrackingBalanceInMinutes());
        assertEquals(15500.0,List.of(ledger.read(ledger.latest(january)),ledger.read(ledger.latest(february)),calculation).stream().mapToDouble(c->c.gross().total().doubleValue()).sum());
    }
    @Test void swissArrearsUseReceiptTaxAndHistoricalWorkInsuranceWithoutRewritingOriginals() {
        contract("CH","ZH",null);var january=create(1,true);create(2,true);String old=fingerprint(january);
        add(march,List.of(payment("CH-regular","REGULAR_ARREARS","2026-03-15","2026-01-31")));correct(january,"2026-03-15");
        var work=ledger.read(ledger.latest(january));assertEquals(5500.0,work.remuneration().socialGross().total().doubleValue());assertEquals(5000.0,work.remuneration().taxGross().total().doubleValue());assertEquals(276.0,work.employee().get("Withholding tax"));
        var receipt=ledger.read(create(3,true));assertEquals(5500.0,receipt.gross().total().doubleValue());assertEquals(5500.0,receipt.remuneration().taxGross().total().doubleValue());assertEquals(5000.0,receipt.remuneration().socialGross().total().doubleValue());assertEquals(0,new BigDecimal("5500").compareTo(receipt.swissBasis().monthlyRateIncome()));
        assertEquals(old,fingerprint(january));
    }
    @Test void vestedSwissPaymentAfterExitUsesLastPayrollTaxAndCreatesANewCashOnlyDocument() {
        contract("CH","ZH","2026-01-31");var january=create(1,true);String old=fingerprint(january);
        add(march,List.of(payment("CH-vested","REGULAR_ARREARS","2026-03-15","2026-01-31")));correct(january,"2026-03-15");
        var original=ledger.read(ledger.latest(january));assertEquals(5500.0,original.remuneration().taxGross().total().doubleValue());
        var receipt=create(3,true);var c=ledger.read(receipt);assertEquals(500.0,receipt.getGrossSalary());assertTrue(c.remuneration().cashOnly());assertEquals(0,c.swissBasis().insuranceDays());assertEquals(0.0,receipt.getDeductions());assertEquals(old,fingerprint(january));
    }
    @Test void newSwissRightInExitMonthUsesSeparatePostExitReceiptPeriodAndCurrentTariff() {
        contract("CH","VD","2026-01-15");var january=payroll.generatePayslip(user.getId(),jan,jan.withDayOfMonth(15),null);payroll.approvePayslip(january.getId(),"fixture");
        var after=jan.withDayOfMonth(16);add(after,List.of(new PayrollPayment("CH-new-right","ONE_OFF",jan,jan.withDayOfMonth(15),jan.withDayOfMonth(25),jan.withDayOfMonth(20),new BigDecimal("500"),true)));
        correct(january,"2026-01-25");var receipt=create(after,true);var c=ledger.read(receipt);
        assertTrue(c.remuneration().cashOnly());assertEquals(500.0,receipt.getGrossSalary());assertEquals(0,c.swissBasis().insuranceDays());assertEquals(0,new BigDecimal("60500").compareTo(c.swissBasis().annualRateIncome()));assertTrue(c.employee().get("Withholding tax")>0);
    }
    @Test void duplicateAndFutureYearReceiptsAreRejectedAndAChangedPlanMakesDraftsStale() {
        contract("DE",null,null);var draft=create(1,false);
        add(march,List.of(payment("DE-regular","REGULAR_ARREARS","2026-03-15","2026-01-31")));assertThrows(IllegalStateException.class,()->payroll.approvePayslip(draft.getId(),"stale"));
        assertThrows(RuntimeException.class,()->add(LocalDate.of(2026,4,1),List.of(new PayrollPayment("DE-regular","ONE_OFF",jan,jan.withDayOfMonth(31),LocalDate.of(2026,4,15),jan.withDayOfMonth(31),new BigDecimal("500"),true))));
        assertThrows(RuntimeException.class,()->add(march,List.of(payment("2027","REGULAR_ARREARS","2027-03-15","2026-01-31"))));
    }
    @Test void realDatedInsuranceAndPensionVersionsAreResolvedAtTheMidmonthBoundary() {
        contract("CH","ZH",null);var change=jan.withDayOfMonth(16);var zero=BigDecimal.ZERO;
        configuration.addInsurance(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.InsuranceInput(change,"New insurer",zero,new BigDecimal("0.02"),zero,zero,zero,zero,zero,zero,null,new PayrollInsurancePool("new insurer",change,LocalDate.of(2026,12,31))));
        configuration.addPension(user.getCompany().getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),change,"New pension agreement",new BigDecimal("200"),zero));
        var allocations=List.of(new PayrollPeriodData.SwissInsuranceAllocation(jan,change.minusDays(1),new BigDecimal("2500"),zero,zero),new PayrollPeriodData.SwissInsuranceAllocation(change,jan.withDayOfMonth(31),new BigDecimal("2500"),new BigDecimal("100"),zero));
        inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),jan,jan.withDayOfMonth(31),"Confirmed segment wages and pension amounts",true,new PayrollPeriodData(List.of(),null,null,allocations)));
        var slip=create(1,true);var saved=ledger.read(slip);assertEquals(2,saved.swissBasis().insuranceSlices().size());assertEquals(50.0,saved.employee().get("NBU"));assertEquals(100.0,saved.employee().get("BVG"));
        assertEquals(746.0,slip.getDeductions());assertEquals(4254.0,slip.getNetSalary());
    }
    void entryIn2025(){rule=PayrollTestTerms.change(rule,Map.of("entryDate","2025-01-01"));terms.add(user.getCompany().getId(),user.getId(),new PayrollTermsService.Input(user.getId(),jan,"Actual employment began in 2025",rule));}
    PayrollPeriodData.GermanPriorYearSocial prior(){return new PayrollPeriodData.GermanPriorYearSocial(LocalDate.of(2025,12,31),360,new BigDecimal("50000"),new BigDecimal("66000"),new BigDecimal("0.02"),false,0,false,null,null,"Confirmed original 2025 social-insurance ledger",true);}
    void priorPayment(LocalDate start,PayrollPayment payment){inputs.add(user.getCompany().getId(),user.getId(),new PayrollPeriodInputService.Input(user.getId(),start,start.withDayOfMonth(start.lengthOfMonth()),"Confirmed one-off and prior-year data",true,new PayrollPeriodData(List.of(),null,null,null,List.of(payment),prior())));}
    @Test void marchClauseAssignsTheWholeOneOffTo2025WithoutConsuming2026Ceilings() {
        contract("DE",null,null);entryIn2025();var bonus=new PayrollPayment("March clause Jan","ONE_OFF",jan,jan.withDayOfMonth(31),jan.withDayOfMonth(31),jan.withDayOfMonth(31),new BigDecimal("1000"),true);
        priorPayment(jan,bonus);var c=ledger.read(create(1,true));assertEquals(6000.0,c.gross().total().doubleValue());assertEquals(5000.0,c.germanBasis().pensionBase().doubleValue());assertEquals(5000.0,c.germanBasis().healthBase().doubleValue());
        assertEquals(1000.0,c.germanBasis().priorYear().pensionBase().doubleValue());assertEquals(150.0,c.germanBasis().priorYear().healthBase().doubleValue());assertEquals(93.0,c.employee().get("Pension insurance · SV 2025"));assertEquals(12.45,c.employee().get("Health insurance · SV 2025"));
        var feb=jan.plusMonths(1);priorPayment(feb,new PayrollPayment("March clause Feb","ONE_OFF",jan,jan.withDayOfMonth(31),feb.withDayOfMonth(28),jan.withDayOfMonth(31),new BigDecimal("3000"),true));
        var second=ledger.read(create(2,true));assertEquals(0.0,second.germanBasis().priorYear().healthBase().doubleValue());assertEquals(5000.0,second.germanBasis().healthBase().doubleValue());
    }
    @Test void marchClauseNeverInventsMissingPriorYearInsuranceData() {
        contract("DE",null,null);entryIn2025();add(jan,List.of(new PayrollPayment("No prior data","ONE_OFF",jan,jan.withDayOfMonth(31),jan.withDayOfMonth(31),jan.withDayOfMonth(31),new BigDecimal("1000"),true)));
        assertThrows(IllegalStateException.class,()->create(1,false));assertEquals(0,slips.count());
    }
    @Test void successivePostExitOneOffsRespectActualReceiptMonthsRatherThanTreatingMayAsAMarchPayment() {
        contract("DE",null,"2026-01-31");entryIn2025();var original=create(1,true);String before=fingerprint(original);
        add(march,List.of(payment("Paid March","ONE_OFF","2026-03-15","2026-03-01")));
        add(LocalDate.of(2026,5,1),List.of(payment("Paid May","ONE_OFF","2026-05-15","2026-05-01")));
        correct(original,"2026-05-15");var corrected=ledger.read(ledger.latest(original));assertNull(corrected.germanBasis().priorYear());assertEquals(6000.0,corrected.germanBasis().pensionBase().doubleValue());assertEquals(5812.5,corrected.germanBasis().healthBase().doubleValue());
        assertEquals(500.0,create(3,true).getGrossSalary());assertEquals(500.0,create(5,true).getGrossSalary());assertEquals(before,fingerprint(original));
    }
    @Test void paymentCorrectionDoesNotAdoptALaterLegacyHoursDocument() {
        contract("CH","ZH",null);var january=create(1,true);create(2,true);
        var old=new Payslip();old.setUser(user);old.setPeriodStart(LocalDate.of(2026,9,1));old.setPeriodEnd(LocalDate.of(2026,9,30));old.setApproved(true);old.setGrossSalary(100.0);old.setDeductions(10.0);old.setNetSalary(90.0);old.setEmployerContributions(0.0);old.setPdfPath("untouched-hours.pdf");old.setCurrency("CHF");old=slips.saveAndFlush(old);String before=fingerprint(old);
        add(march,List.of(payment("Old document protection","REGULAR_ARREARS","2026-03-15","2026-01-31")));correct(january,"2026-03-15");
        assertEquals(before,fingerprint(old));assertNull(old.getCalculationSnapshot());assertEquals(2,slips.findByUserAndApproved(user,true).stream().filter(Payslip::isCorrection).count());
    }
}
