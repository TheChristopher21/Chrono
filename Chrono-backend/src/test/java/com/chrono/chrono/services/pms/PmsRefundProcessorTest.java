package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.RefundPaymentRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsRefundProcessorTest {
    PaymentRepository payments; FolioItemRepository items; HotelPropertyRepository properties;
    PmsFinancialPeriodService periods; PmsPaymentGateway gateway; PlatformTransactionManager transactions; PmsCashService cash;
    PmsRefundProcessor processor; Payment original; Map<Long, Payment> rows;
    @BeforeEach void setup() throws Exception {
        payments = mock(PaymentRepository.class); items = mock(FolioItemRepository.class); properties = mock(HotelPropertyRepository.class);
        periods = mock(PmsFinancialPeriodService.class); gateway = mock(PmsPaymentGateway.class); transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        Company company = new Company("Hotel"); company.setId(1L);
        HotelProperty hotel = new HotelProperty(); org.springframework.test.util.ReflectionTestUtils.setField(hotel, "id", 2L); hotel.setCompany(company); hotel.setCurrencyCode("CHF");
        Reservation reservation = new Reservation(); reservation.setId(3L); reservation.setProperty(hotel);
        Folio folio = new Folio(); folio.setId(4L); folio.setReservation(reservation);
        original = new Payment(); original.setId(5L); original.setFolio(folio); original.setAmount(new BigDecimal("100.00")); original.setMethod(PaymentMethod.CARD); original.setReference("pi_original");
        rows = new LinkedHashMap<>(); rows.put(5L, original); original.setMerchantContext("CONNECT:acct_original");
        when(payments.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0))));
        when(properties.findByIdAndCompany_IdForUpdate(2L, 1L)).thenReturn(Optional.of(hotel));
        when(periods.currentBusinessDate(hotel)).thenReturn(LocalDate.now());
        when(payments.findByIdForUpdate(anyLong(), eq(2L), eq(1L))).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0))));
        when(payments.findByRefundRequestId(anyString())).thenAnswer(inv -> rows.values().stream().filter(p -> inv.getArgument(0).equals(p.getRefundRequestId())).findFirst());
        when(payments.findAllByOriginalPayment_IdAndStatus(anyLong(), any())).thenAnswer(inv -> rows.values().stream().filter(p -> p.getOriginalPayment() == original && p.getStatus() == inv.getArgument(1)).toList());
        when(payments.findAllByFolio_IdOrderByReceivedAtAsc(4L)).thenAnswer(inv -> new ArrayList<>(rows.values()));
        when(payments.saveAndFlush(any())).thenAnswer(inv -> { Payment p = inv.getArgument(0); p.setId(5L + rows.size()); p.setReceivedAt(java.time.LocalDateTime.now()); rows.put(p.getId(), p); return p; });
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.supports(PaymentMethod.CARD)).thenReturn(true);
        when(gateway.refund(any(), any(), any(), any())).thenAnswer(inv -> new PmsPaymentGateway.RefundResult("re_" + inv.getArgument(3), "succeeded"));
        cash = mock(PmsCashService.class);
        processor = new PmsRefundProcessor(payments, items, properties, cash, periods, List.of(gateway), transactions, mock(PmsAuditWriter.class));
    }
    private RefundPaymentRequest request(String key) { return new RefundPaymentRequest(new BigDecimal("25.00"), "Reklamation", key, null); }
    @Test void missingSecondPersonApprovalStopsBeforeAnyRefundIntentOrProviderCall() throws Exception {
        var approvals=mock(PmsApprovalService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(processor,"approvals",approvals);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_REQUIRED,"Freigabe erforderlich"))
                .when(approvals).consumeForRefund(any(),eq(5L),any());
        assertThatThrownBy(() -> processor.process(1L,2L,5L,request("refund-approval"),"Alice")).hasMessageContaining("428");
        verify(payments,never()).saveAndFlush(any()); verify(gateway,never()).refund(any(),any(),any(),any());
        assertThat(rows).hasSize(1);
    }
    @Test void separateEqualAmountsUseDistinctProviderKeysAndReplayDoesNotCreateAnotherRefund() throws Exception {
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        processor.process(1L, 2L, 5L, request("refund-two"), "Alice");
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        assertThat(rows).hasSize(3);
        verify(gateway).refund(original, new BigDecimal("25.00"), "Reklamation", "chrono-pms-refund-6");
        verify(gateway).refund(original, new BigDecimal("25.00"), "Reklamation", "chrono-pms-refund-7");
        verify(gateway, times(2)).refund(any(), any(), any(), any());
    }
    @Test void commitsIntentBeforeProviderCallAndRetainsItOnAnUnknownNetworkResult() throws Exception {
        when(gateway.refund(any(), any(), any(), any())).thenThrow(new java.io.IOException("timeout"));
        assertThatThrownBy(() -> processor.process(1L, 2L, 5L, request("refund-one"), "Alice")).isInstanceOf(ResponseStatusException.class);
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.PENDING);
        var order = inOrder(transactions, gateway);
        order.verify(transactions,times(2)).commit(any());
        order.verify(gateway).refund(any(), any(), any(), eq("chrono-pms-refund-6"));
        doReturn(new PmsPaymentGateway.RefundResult("re_confirmed", "succeeded")).when(gateway).refund(any(), any(), any(), any());
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(6L).getProviderTransactionId()).isEqualTo("re_confirmed");
    }
    @Test void pendingProviderStatusRemainsUnpostedAndIsReconciledByItsProviderId() throws Exception {
        when(gateway.refund(any(), any(), any(), any())).thenReturn(new PmsPaymentGateway.RefundResult("re_pending", "pending"));
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.PENDING);
        when(gateway.retrieveRefund("re_pending","CONNECT:acct_original")).thenReturn(new PmsPaymentGateway.RefundResult("re_pending", "succeeded"));
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.POSTED);
        verify(gateway, times(1)).refund(any(), any(), any(), any());
    }
    @Test void closedFolioRefundRequiresAServiceCreditAndAllowsAnApprovedCredit() {
        original.getFolio().setStatus(FolioStatus.CLOSED);
        FolioItem item = new FolioItem(); item.setTotalAmount(new BigDecimal("100"));
        when(items.findAllByFolio_IdOrderByServiceDateAscIdAsc(4L)).thenReturn(List.of(item));
        assertThatThrownBy(() -> processor.process(1L, 2L, 5L, request("refund-one"), "Alice")).hasMessageContaining("Gutschrift");
        item.setTotalAmount(new BigDecimal("75"));
        processor.process(1L, 2L, 5L, request("refund-one"), "Alice");
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.POSTED);
    }
    @Test void mismatchedProviderRefundIsNeverPostedAndOriginalMerchantIsRetained() throws Exception {
        when(gateway.refund(any(),any(),any(),any())).thenReturn(new PmsPaymentGateway.RefundResult("re_wrong","succeeded","pi_another",new BigDecimal("25"),"CHF"));
        assertThatThrownBy(() -> processor.process(1L,2L,5L,request("refund-wrong"),"Alice")).hasMessageContaining("gespeicherten Vorgang");
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(rows.get(6L).getMerchantContext()).isEqualTo("CONNECT:acct_original");
    }

    @Test void cashRefundValidationAndPostingCommitTogetherWithoutAnIntermediatePendingCashIntent() {
        original.setMethod(PaymentMethod.CASH);
        CashShift shift = new CashShift(); shift.setId(8L); shift.setProperty(original.getFolio().getReservation().getProperty());
        when(cash.requireOpenShift(any(), isNull(), eq("Alice"))).thenReturn(shift);
        processor.process(1L, 2L, 5L, request("cash-refund-one"), "Alice");
        assertThat(rows.get(6L).getStatus()).isEqualTo(PaymentStatus.POSTED);
        assertThat(rows.get(6L).getCashShift()).isSameAs(shift);
        verify(transactions, times(1)).commit(any());
        verifyNoInteractions(gateway);
    }
}
