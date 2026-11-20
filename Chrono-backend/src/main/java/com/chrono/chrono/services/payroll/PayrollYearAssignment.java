package com.chrono.chrono.services.payroll;

/** A correction of wages already paid in the original year, never a new retrospective grant. */
public record PayrollYearAssignment(String source,Boolean originalGrossAlreadyPaid,
        Boolean germanCertificateNotTransmitted,Boolean germanAnnualAdjustmentPermitted) {}
