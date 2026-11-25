package com.chrono.chrono.services.payroll;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A real payment: cash receipt, legal entitlement and work period are distinct facts. */
public record PayrollPayment(String reference,String kind,LocalDate earnedFrom,LocalDate earnedThrough,
        LocalDate receivedOn,LocalDate claimEstablishedOn,BigDecimal gross,Boolean thirteenthSettled,
        BigDecimal bvgEmployeeExtra,BigDecimal bvgEmployerExtra,Boolean employeeSocialRecoveryConfirmed) {
    public PayrollPayment(String r,String k,LocalDate f,LocalDate t,LocalDate received,LocalDate claim,BigDecimal g,Boolean settled){this(r,k,f,t,received,claim,g,settled,null,null,null);}
    public PayrollPayment(String r,String k,LocalDate f,LocalDate t,LocalDate received,LocalDate claim,BigDecimal g,Boolean settled,BigDecimal bvgEmployee,BigDecimal bvgEmployer){this(r,k,f,t,received,claim,g,settled,bvgEmployee,bvgEmployer,null);}
}
