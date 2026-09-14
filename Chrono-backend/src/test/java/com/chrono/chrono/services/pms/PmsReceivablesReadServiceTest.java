package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PmsReceivablesReadServiceTest {
    @Test void pagedReadUsesDatabaseFiltersGlobalTotalsAndNeverAcquiresPropertyOrPeriodWriteLock() {
        var properties=mock(HotelPropertyRepository.class);var receivables=mock(PmsReceivableRepository.class);var periods=mock(PmsFinancialPeriodService.class);
        var service=new PmsReceivablesService(properties,mock(PmsOrganizationRepository.class),mock(PmsCreditAccountRepository.class),receivables,
                mock(PmsReceivableSettlementRepository.class),mock(PmsInvoiceRepository.class),mock(PmsInvoiceLineRepository.class),mock(FolioItemRepository.class),mock(PaymentRepository.class),periods,mock(PmsAuditWriter.class));
        Company company=new Company("Chain");company.setId(3L);HotelProperty hotel=new HotelProperty();org.springframework.test.util.ReflectionTestUtils.setField(hotel, "id", 5L);hotel.setCompany(company);
        LocalDate date=LocalDate.of(2026,9,12);when(properties.findByIdAndCompany_Id(5L,3L)).thenReturn(Optional.of(hotel));when(periods.readBusinessDate(hotel)).thenReturn(date);
        when(receivables.searchPage(eq(5L),isNull(),eq(true),eq(true),eq(date),eq("%acme!_%"),eq(PageRequest.of(1,2)))).thenReturn(new PageImpl<>(List.of(),PageRequest.of(1,2),5));
        var totals=mock(PmsReceivableRepository.Summary.class);when(totals.getTotalOutstanding()).thenReturn(new BigDecimal("150.00"));when(totals.getTotalOverdue()).thenReturn(new BigDecimal("50.00"));when(receivables.summary(5L,date)).thenReturn(totals);
        var result=service.listPage(company,5L,null,true,true,1,2,"ACME_");
        assertThat(result.totalElements()).isEqualTo(5);assertThat(result.totalOutstanding()).isEqualByComparingTo("150");assertThat(result.totalOverdue()).isEqualByComparingTo("50");
        verify(properties,never()).findByIdAndCompany_IdForUpdate(any(),any());verify(periods,never()).currentBusinessDate(any());
        verify(receivables,never()).findAllByProperty_IdOrderByDueDateAscIdAsc(any());
    }
}
