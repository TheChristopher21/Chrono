package com.chrono.chrono.services.payroll;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Joint principles §22 SGB IV, section 4: monthly frame, not invented daily wages. */
public final class GermanConcurrentSettlement {
    private GermanConcurrentSettlement() {}
    public record Allocation(BigDecimal pension,BigDecimal health,int frameDays) {}
    public static int days(LocalDate from,LocalDate through) {
        return PayrollPeriod.fullMonth(from,through)?30:Math.min(30,Math.toIntExact(ChronoUnit.DAYS.between(from,through)+1));
    }
    public static Allocation calculate(BigDecimal own,LocalDate from,LocalDate through,PayrollPeriodData.GermanConcurrentIncome data) {
        return calculate(own,from,through,data,new BigDecimal("8450"),new BigDecimal("5812.50"));
    }
    public static Allocation calculate(BigDecimal own,LocalDate from,LocalDate through,PayrollPeriodData.GermanConcurrentIncome data,BigDecimal rvCap,BigDecimal kvCap) {
        if(data.employments()==null||data.employments().isEmpty())return new Allocation(
                GermanPayrollTax.concurrentShare(own,data.otherPensionGross(),rvCap,days(from,through)),
                GermanPayrollTax.concurrentShare(own,data.otherHealthGross(),kvCap,days(from,through)),days(from,through));
        var connected=new ArrayList<PayrollPeriodData.GermanEmployment>();var remaining=new ArrayList<>(data.employments());
        LocalDate first=from,last=through;
        boolean added;
        do {
            added=false;
            for(var iterator=remaining.iterator();iterator.hasNext();) {
                var row=iterator.next();
                if(!row.from().isAfter(last)&&!row.through().isBefore(first)) {
                    connected.add(row);iterator.remove();if(row.from().isBefore(first))first=row.from();if(row.through().isAfter(last))last=row.through();added=true;
                }
            }
        } while(added);
        int frame=days(first,last);
        return new Allocation(share(own,connected.stream().map(PayrollPeriodData.GermanEmployment::pensionGross).toList(),rvCap,frame),
                share(own,connected.stream().map(PayrollPeriodData.GermanEmployment::healthGross).toList(),kvCap,frame),frame);
    }
    private static BigDecimal share(BigDecimal own,List<BigDecimal> others,BigDecimal monthlyCap,int days) {
        BigDecimal cap=monthlyCap.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(30),2,RoundingMode.HALF_UP);
        BigDecimal ownCapped=own.min(cap),sum=ownCapped;
        for(var other:others)sum=sum.add(other.min(cap));
        return sum.compareTo(cap)<=0?ownCapped:cap.multiply(ownCapped).divide(sum,2,RoundingMode.HALF_UP);
    }
}
