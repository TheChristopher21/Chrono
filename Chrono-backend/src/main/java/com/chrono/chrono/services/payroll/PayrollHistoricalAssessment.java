package com.chrono.chrono.services.payroll;

import com.chrono.chrono.services.PayrollCalculationService;
import java.util.List;
import java.util.Map;

/** Incremental charges against an immutable imported year/opening, never replacement wages. */
public record PayrollHistoricalAssessment(List<PayrollCalculationService.Calculation> originalPeriods,
        List<PayrollCalculationService.Calculation> revisedPeriods,
        Map<String,Double> cumulativeEmployee,Map<String,Double> cumulativeEmployer,
        Map<String,Double> employee,Map<String,Double> employer,String fingerprint,
        PayrollCalculationService.Calculation lastEmployment,PayrollPeriodData.GermanPriorYearSocial priorSocial) {}
