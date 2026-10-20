package com.chrono.chrono.dto.pms;

import java.time.LocalDate;
import java.util.List;

public record PmsFinancialDayResponse(LocalDate businessDate, LocalDate lastClosedDate,
                                      long openCashShifts, long pendingArrivals, long pendingDepartures,
                                      boolean canClose, List<String> blockers) { }
