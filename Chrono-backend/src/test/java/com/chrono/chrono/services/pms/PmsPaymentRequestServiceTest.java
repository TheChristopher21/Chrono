package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsPaymentRequestServiceTest {
    PmsPaymentRequestRepository requests; FolioRepository folios; FolioItemRepository items;
    PaymentRepository payments; PmsPaymentGateway gateway; PlatformTransactionManager transactions;
    PmsPaymentRequestService service; Map<Long,PmsPaymentRequest> rows; List<Payment> ledger; HotelProperty hotel; Folio folio;
    @BeforeEach void setup() throws Exception {
        requests=mock(PmsPaymentRequestRepository.class); folios=mock(FolioRepository.class); items=mock(FolioItemRepository.class);
        payments=mock(PaymentRepository.class); gateway=mock(PmsPaymentGateway.class); transactions=mock(PlatformTransactionManager.class);
        HotelPropertyRepository properties=mock(HotelPropertyRepository.class); PmsFinancialPeriodService periods=mock(PmsFinancialPeriodService.class);
        Company company=new Company("Hotel"); company.setId(1L); hotel=new HotelProperty(); ReflectionTestUtils.setField(hotel,"id",2L); hotel.setCompany(company); hotel.setCurrencyCode("CHF");
        Reservation stay=new Reservation(); stay.setId(3L); stay.setProperty(hotel); stay.setGuaranteeStatus(ReservationGuaranteeStatus.UNGUARANTEED);
        folio=new Folio(); folio.setId(4L); folio.setReservation(stay); folio.setStatus(FolioStatus.OPEN);
        rows=new LinkedHashMap<>(); ledger=new ArrayList<>();
        when(transactions.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        when(properties.findByIdAndCompany_IdForUpdate(2L,1L)).thenReturn(Optional.of(hotel));
        when(periods.currentBusinessDate(hotel)).thenReturn(LocalDate.now());
        when(folios.findByIdAndReservation_Property_Company_Id(4L,1L)).thenReturn(Optional.of(folio));
        when(folios.findAllByReservation_IdOrderByIdAsc(3L)).thenReturn(List.of(folio));
        when(requests.findById(anyLong())).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(0))));
        when(requests.findByRequestKey(anyString())).thenAnswer(i->rows.values().stream().filter(r->r.getRequestKey().equals(i.getArgument(0))).findFirst());
        when(requests.findAllByFolio_IdOrderByCreatedAtDesc(4L)).thenAnswer(i->new ArrayList<>(rows.values()));
        when(requests.saveAndFlush(any())).thenAnswer(i->{PmsPaymentRequest row=i.getArgument(0);row.setId(100L+rows.size());row.setCreatedAt(LocalDateTime.now());rows.put(row.getId(),row);return row;});
        when(requests.save(any())).thenAnswer(i->i.getArgument(0));
        FolioItem line=new FolioItem();line.setTotalAmount(new BigDecimal("200"));
        when(items.findAllByFolio_IdOrderByServiceDateAscIdAsc(4L)).thenReturn(List.of(line));
        when(payments.findAllByFolio_IdOrderByReceivedAtAsc(4L)).thenAnswer(i->ledger);
        when(payments.findByProviderTransactionId(anyString())).thenAnswer(i->ledger.stream().filter(p->p.getProviderTransactionId().equals(i.getArgument(0))).findFirst());
        when(payments.save(any())).thenAnswer(i->{Payment p=i.getArgument(0);ledger.add(p);return p;});
        when(gateway.supports(PaymentMethod.CARD)).thenReturn(true);
        when(gateway.merchantContext(hotel)).thenReturn("CONNECT:acct_original");
        when(gateway.createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString())).thenReturn(result("open", "0", "0"));
        service=new PmsPaymentRequestService(requests,folios,items,payments,properties,periods,mock(PmsAuditWriter.class),List.of(gateway),transactions,"https://hotel.example");
    }
    CreatePmsPaymentRequest payment() { return new CreatePmsPaymentRequest("payment-one",new BigDecimal("100.00"),"PAYMENT"); }
    CreatePmsPaymentRequest authorization() { return new CreatePmsPaymentRequest("guarantee-one",new BigDecimal("100.00"),"AUTHORIZATION"); }
    PmsPaymentGateway.CheckoutResult result(String status,String received,String capturable) {
        return new PmsPaymentGateway.CheckoutResult("cs_test","https://checkout.stripe.com/example","pi_test",status,new BigDecimal(received),new BigDecimal(capturable),"CHF","2","4");
    }
    @Test void persistsBeforeProviderAndAnOpenHostedLinkNeverCountsAsPaid() throws Exception {
        var view=service.create(1L,2L,4L,payment(),"Alice");
        assertThat(view.status()).isEqualTo("OPEN");assertThat(ledger).isEmpty();
        var order=inOrder(transactions,gateway);order.verify(transactions).commit(any());
        order.verify(gateway).createCheckout(hotel,folio,new BigDecimal("100.00"),false,"https://hotel.example/pms-payment-return","payment-one","CONNECT:acct_original");
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(result("succeeded","100","0"));
        assertThat(service.reconcile(1L,2L,view.id(),"Alice").status()).isEqualTo("PAID");
        service.create(1L,2L,4L,payment(),"Alice");
        assertThat(ledger).hasSize(1);assertThat(ledger.get(0).getPostingDate()).isEqualTo(LocalDate.now());
        verify(gateway,times(1)).createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString());
    }
    @Test void networkFailureRetainsOriginalKeyForAnExplicitRetry() throws Exception {
        doThrow(new java.io.IOException("timeout")).when(gateway).createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->service.create(1L,2L,4L,payment(),"Alice")).hasMessageContaining("gespeichert");
        assertThat(rows).hasSize(1);assertThat(ledger).isEmpty();
        doReturn(result("open","0","0")).when(gateway).createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString());
        service.create(1L,2L,4L,payment(),"Alice");assertThat(rows).hasSize(1);
        verify(gateway,times(2)).createCheckout(any(),any(),any(),anyBoolean(),anyString(),eq("payment-one"),eq("CONNECT:acct_original"));
    }
    @Test void requestIdentityCannotBeReusedForAnotherAmountAndOpenLinksReserveTheBalance() {
        service.create(1L,2L,4L,payment(),"Alice");
        assertThatThrownBy(()->service.create(1L,2L,4L,new CreatePmsPaymentRequest("payment-one",new BigDecimal("20"),"PAYMENT"),"Alice")).hasMessageContaining("anderen");
        assertThatThrownBy(()->service.create(1L,2L,4L,new CreatePmsPaymentRequest("payment-two",new BigDecimal("101"),"PAYMENT"),"Alice")).hasMessageContaining("offener Zahlungslinks");
    }
    @Test void confirmedAuthorizationHasNoLedgerPaymentUntilCaptureAndPartialCaptureReplaysExactlyOnce() throws Exception {
        when(gateway.createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString())).thenReturn(result("requires_capture","0","100"));
        var view=service.create(1L,2L,4L,authorization(),"Alice");
        assertThat(view.status()).isEqualTo("AUTHORIZED");assertThat(ledger).isEmpty();
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(result("requires_capture","0","100"),result("succeeded","60","0"));
        assertThat(service.capture(1L,2L,view.id(),new BigDecimal("60"),"Alice").status()).isEqualTo("PAID");
        service.capture(1L,2L,view.id(),new BigDecimal("60"),"Alice");
        assertThat(ledger).hasSize(1);assertThat(ledger.get(0).getAmount()).isEqualByComparingTo("60");
        verify(gateway,times(1)).captureAuthorization("pi_test",new BigDecimal("60.00"),"CHF","guarantee-one","CONNECT:acct_original");
    }
    @Test void releaseReconcilesProviderStatusAndRemovesOnlyTheCardGuarantee() throws Exception {
        when(gateway.createCheckout(any(),any(),any(),anyBoolean(),anyString(),anyString(),anyString())).thenReturn(result("requires_capture","0","100"));
        var view=service.create(1L,2L,4L,authorization(),"Alice");
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(result("requires_capture","0","100"),result("canceled","0","0"));
        assertThat(service.release(1L,2L,view.id(),"Alice").status()).isEqualTo("RELEASED");
        assertThat(ledger).isEmpty();assertThat(folio.getReservation().getGuaranteeStatus()).isEqualTo(ReservationGuaranteeStatus.UNGUARANTEED);
    }
    @Test void refusesProviderAmountsAndMetadataBelongingToAnotherFolio() throws Exception {
        var view=service.create(1L,2L,4L,payment(),"Alice");
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(new PmsPaymentGateway.CheckoutResult("cs_test",null,"pi_test","succeeded",new BigDecimal("100"),BigDecimal.ZERO,"CHF","2","999"));
        assertThatThrownBy(()->service.reconcile(1L,2L,view.id(),"Alice")).hasMessageContaining("eindeutig");assertThat(ledger).isEmpty();
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(result("succeeded","90","0"));
        assertThatThrownBy(()->service.reconcile(1L,2L,view.id(),"Alice")).hasMessageContaining("weicht");assertThat(ledger).isEmpty();
    }
    @Test void aHotelMerchantChangeNeverMovesExistingCheckoutOrItsPostedPayment() throws Exception {
        var view=service.create(1L,2L,4L,payment(),"Alice");
        when(gateway.merchantContext(hotel)).thenReturn("CONNECT:acct_replacement");
        when(gateway.inspectCheckout("cs_test","CONNECT:acct_original")).thenReturn(result("succeeded","100","0"));
        service.reconcile(1L,2L,view.id(),"automation");
        assertThat(rows.get(view.id()).getMerchantContext()).isEqualTo("CONNECT:acct_original");
        assertThat(ledger.get(0).getMerchantContext()).isEqualTo("CONNECT:acct_original");
        verify(gateway,never()).inspectCheckout(anyString(),eq("CONNECT:acct_replacement"));
    }
    @Test void automaticDepositRechecksOptInAndReservationStateUnderTheHotelLock() {
        var settings=mock(PmsPaymentSettingsRepository.class);var policies=mock(PmsReservationPolicyService.class);
        ReflectionTestUtils.setField(service,"automationSettings",settings);ReflectionTestUtils.setField(service,"reservationPolicies",policies);
        var row=new PmsPaymentSettings();row.setPropertyId(2L);row.setVerifiedAt(LocalDateTime.now());row.setAutomaticDepositLinks(false);
        when(settings.findById(2L)).thenReturn(Optional.of(row));
        when(policies.view(folio.getReservation())).thenReturn(new ReservationPolicyView(3L,LocalDateTime.now(),new BigDecimal("50"),new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"),LocalDate.now(),true,null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false));
        folio.getReservation().setStatus(ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> service.createAutomaticDeposit(1L,2L,4L,3L,payment())).hasMessageContaining("aktuellen Bedingungen");
        row.setAutomaticDepositLinks(true);folio.getReservation().setStatus(ReservationStatus.CANCELLED);
        assertThatThrownBy(() -> service.createAutomaticDeposit(1L,2L,4L,3L,payment())).hasMessageContaining("aktuellen Bedingungen");
        assertThat(rows).isEmpty();assertThat(ledger).isEmpty();
    }
}
