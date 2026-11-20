package com.chrono.chrono.services.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Confirmed payroll inputs for one exact period; these never create clock entries. */
public record PayrollPeriodData(List<AbsencePayment> absences, GermanConcurrentIncome germanConcurrentIncome,BigDecimal swissOtherAperiodicGross,
        List<SwissInsuranceAllocation> swissInsuranceAllocations,List<PayrollPayment> payments,GermanPriorYearSocial germanPriorYearSocial,
        com.chrono.chrono.services.PayrollOpeningService.Period historicalBaseline,List<SwissInsuranceAllocation> historicalInsuranceAllocations) {
    public PayrollPeriodData(List<AbsencePayment> a,GermanConcurrentIncome c,BigDecimal o,List<SwissInsuranceAllocation> s,List<PayrollPayment> p,GermanPriorYearSocial y,com.chrono.chrono.services.PayrollOpeningService.Period b){this(a,c,o,s,p,y,b,null);}
    public PayrollPeriodData(List<AbsencePayment> a,GermanConcurrentIncome c,BigDecimal o,List<SwissInsuranceAllocation> s,List<PayrollPayment> p,GermanPriorYearSocial y){this(a,c,o,s,p,y,null);}
    public PayrollPeriodData(List<AbsencePayment> absences,GermanConcurrentIncome concurrent) {this(absences,concurrent,null,null,null,null);}
    public PayrollPeriodData(List<AbsencePayment> absences,GermanConcurrentIncome concurrent,BigDecimal other) {this(absences,concurrent,other,null,null,null);}
    public PayrollPeriodData(List<AbsencePayment> absences,GermanConcurrentIncome concurrent,BigDecimal other,List<SwissInsuranceAllocation> allocations) {this(absences,concurrent,other,allocations,null,null);}
    public PayrollPeriodData(List<AbsencePayment> absences,GermanConcurrentIncome concurrent,BigDecimal other,List<SwissInsuranceAllocation> allocations,List<PayrollPayment> payments) {this(absences,concurrent,other,allocations,payments,null);}
    public record GermanPriorYearSocial(LocalDate assignmentDate,Integer insuranceDays,BigDecimal pensionAssessed,BigDecimal healthAssessed,
            BigDecimal healthAdditionalRate,Boolean parent,Integer childrenUnder25,Boolean saxony,BigDecimal pensionAllocatedBase,BigDecimal healthAllocatedBase,String source,Boolean confirmed) {}
    public record SwissInsuranceAllocation(LocalDate from,LocalDate through,BigDecimal gross,BigDecimal pensionEmployee,BigDecimal pensionEmployer) {}
    public record AbsencePayment(LocalDate date, String type, Integer minutes, BigDecimal hourlyRate, BigDecimal payRate) {}
    /** Other gross is for the same overlapping SV days. One-off shares are from the collection agency. */
    public record GermanConcurrentIncome(BigDecimal otherPensionGross, BigDecimal otherHealthGross,
            BigDecimal pensionOneOffBase, BigDecimal healthOneOffBase, Boolean fundAllocationConfirmed,List<GermanEmployment> employments) {
        public GermanConcurrentIncome(BigDecimal pension,BigDecimal health,BigDecimal pensionExtra,BigDecimal healthExtra,Boolean confirmed) {
            this(pension,health,pensionExtra,healthExtra,confirmed,null);
        }
    }
    public record GermanEmployment(String employer,LocalDate from,LocalDate through,BigDecimal pensionGross,BigDecimal healthGross) {}
    public List<AbsencePayment> absencePayments() { return absences==null?List.of():absences; }
}
