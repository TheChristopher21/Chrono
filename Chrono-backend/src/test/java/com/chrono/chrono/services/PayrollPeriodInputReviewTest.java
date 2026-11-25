package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollPeriodInputReviewTest {
    VacationRequestRepository vacations=mock(VacationRequestRepository.class);
    SickLeaveRepository sickness=mock(SickLeaveRepository.class);
    HolidayService holidays=mock(HolidayService.class);
    PayrollPeriodInputService service=new PayrollPeriodInputService(mock(PayrollPeriodInputRepository.class),mock(UserRepository.class),
            mock(PayrollTermsService.class),mock(EntityManager.class),new ObjectMapper(),vacations,sickness,holidays);
    LocalDate first=LocalDate.of(2026,1,1),last=LocalDate.of(2026,1,31);
    User user(){var u=new User();u.setCompany(new Company("Local fixture"));return u;}
    PayrollTerms hourly(){return PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("payType","HOURLY","amount","30"));}
    @Test void holidayRequiresAnExplicitMonthlyEntitlementReview() {
        when(holidays.getCompanyHolidays(eq(2026),any())).thenReturn(Set.of(first));
        assertThrows(IllegalStateException.class,()->service.requireHourlyAbsenceReview(user(),first,last,hourly(),null));
    }
    @Test void overlappingApprovedVacationAndSickLeaveRequireAReviewButOutsideDatesDoNot() {
        var v=new VacationRequest();v.setStartDate(first.minusDays(2));v.setEndDate(first);
        when(vacations.findByUserAndApprovedTrue(any())).thenReturn(List.of(v));
        assertThrows(IllegalStateException.class,()->service.requireHourlyAbsenceReview(user(),first,last,hourly(),null));
        v.setEndDate(first.minusDays(1));assertDoesNotThrow(()->service.requireHourlyAbsenceReview(user(),first,last,hourly(),null));
        var s=new SickLeave();s.setStartDate(last);s.setEndDate(last.plusDays(3));when(sickness.findByUser(any())).thenReturn(List.of(s));
        assertThrows(IllegalStateException.class,()->service.requireHourlyAbsenceReview(user(),first,last,hourly(),null));
    }
    @Test void reviewedNoPaymentIsDifferentFromUnknownAndMonthlySalariesNeedNoDuplicateAbsencePayment() {
        var confirmed=new PayrollPeriodInputService.Reference(1L,"Contract reviewed: no paid holiday entitlement",new PayrollPeriodData(List.of(),null));
        assertDoesNotThrow(()->service.requireHourlyAbsenceReview(user(),first,last,hourly(),confirmed));
        assertDoesNotThrow(()->service.requireHourlyAbsenceReview(user(),first,last,PayrollTestTerms.swiss(),null));
        verifyNoInteractions(vacations,sickness,holidays);
    }
}
