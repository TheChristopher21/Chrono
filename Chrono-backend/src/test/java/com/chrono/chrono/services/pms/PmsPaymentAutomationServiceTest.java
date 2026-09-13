package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.chrono.chrono.dto.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PmsPaymentAutomationServiceTest {
    PmsPaymentRequestRepository requests=mock(PmsPaymentRequestRepository.class);PaymentRepository payments=mock(PaymentRepository.class);PmsStripeEventRepository events=mock(PmsStripeEventRepository.class);
    PmsPaymentRequestService checkout=mock(PmsPaymentRequestService.class);PmsRefundProcessor refunds=mock(PmsRefundProcessor.class);PmsPaymentSettingsRepository settings=mock(PmsPaymentSettingsRepository.class);
    ReservationRepository reservations=mock(ReservationRepository.class);FolioRepository folios=mock(FolioRepository.class);HotelPropertyRepository properties=mock(HotelPropertyRepository.class);
    PmsReservationPolicyService policies=mock(PmsReservationPolicyService.class);PmsDeliveryService delivery=mock(PmsDeliveryService.class);
    PmsPaymentAutomationService service;PmsPaymentRequest request;PmsStripeEvent event;PmsPaymentSettings config;Reservation reservation;
    @BeforeEach void setup(){
        var transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        service=new PmsPaymentAutomationService(requests,payments,events,checkout,refunds,settings,reservations,folios,properties,policies,delivery,transactions,true);
        var company=new Company("Hotel");company.setId(1L);var hotel=new HotelProperty();ReflectionTestUtils.setField(hotel,"id",2L);hotel.setCompany(company);hotel.setCurrencyCode("CHF");
        reservation=new Reservation();reservation.setId(3L);reservation.setProperty(hotel);reservation.setStatus(ReservationStatus.CONFIRMED);
        var folio=new Folio();folio.setId(4L);folio.setReservation(reservation);folio.setStatus(FolioStatus.OPEN);
        request=new PmsPaymentRequest();request.setId(5L);request.setFolio(folio);request.setMerchantContext("CONNECT:acct_hotel");request.setProviderSessionId("cs_example");request.setRequestKey("deposit-reservation-3");
        event=new PmsStripeEvent();event.setId(6L);event.setEventId("evt_example");event.setObjectId("cs_example");event.setAccountId("acct_hotel");event.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        when(events.findLocked(6L)).thenReturn(Optional.of(event));when(events.save(any())).thenAnswer(i -> i.getArgument(0));
        when(requests.findByProviderSessionId("cs_example")).thenReturn(Optional.of(request));when(requests.findById(5L)).thenReturn(Optional.of(request));
        config=new PmsPaymentSettings();config.setPropertyId(2L);config.setVerifiedAt(LocalDateTime.now());config.setAutomaticDepositLinks(true);
        when(settings.findById(2L)).thenReturn(Optional.of(config));when(reservations.findById(3L)).thenReturn(Optional.of(reservation));when(folios.findAllByReservation_IdOrderByIdAsc(3L)).thenReturn(List.of(folio));
        when(policies.view(reservation)).thenReturn(new ReservationPolicyView(3L,LocalDateTime.now(),new BigDecimal("50"),new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"),LocalDate.now(),true,null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false));
    }
    @Test void duplicateEventReusesManualFinancialWorkflowExactlyOnce(){
        service.processEvent(6L);service.processEvent(6L);
        assertThat(event.getStatus()).isEqualTo("PROCESSED");verify(checkout,times(1)).reconcile(1L,2L,5L,"payment-automation");
        verifyNoInteractions(payments);
    }
    @Test void anotherConnectedAccountCannotTriggerAHotelPayment(){
        event.setAccountId("acct_other");service.processEvent(6L);
        assertThat(event.getStatus()).isEqualTo("REJECTED");verifyNoInteractions(checkout);
    }
    @Test void providerOrClosedFinancialDayFailureKeepsInboxRetryable(){
        doThrow(new IllegalStateException("financial day unavailable")).when(checkout).reconcile(1L,2L,5L,"payment-automation");
        service.processEvent(6L);
        assertThat(event.getStatus()).isEqualTo("PENDING");assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now());assertThat(request.getAutomationError()).isNotBlank();
    }
    @Test void optOutAndCancelledStayNeverCreateOrSendDepositLinks(){
        config.setAutomaticDepositLinks(false);service.queueDeposit(2L,3L);config.setAutomaticDepositLinks(true);reservation.setStatus(ReservationStatus.CANCELLED);service.queueDeposit(2L,3L);
        verifyNoInteractions(checkout,delivery);
    }
    @Test void dueDepositUsesStableRequestIdentityAndOnlyQueuesAGuestConfirmedPaymentLink(){
        var view=new PmsPaymentRequestView(5L,"deposit-reservation-3",4L,"PAYMENT","OPEN",new BigDecimal("100"),null,"CHF","https://checkout.stripe.com/example","cs_example","pi_example","open",LocalDateTime.now(),LocalDateTime.now());
        when(checkout.createAutomaticDeposit(eq(1L),eq(2L),eq(4L),eq(3L),any())).thenReturn(view);
        service.queueDeposit(2L,3L);
        verify(checkout).createAutomaticDeposit(1L,2L,4L,3L,new CreatePmsPaymentRequest("deposit-reservation-3",new BigDecimal("100"),"PAYMENT"));
        verify(delivery).queuePaymentLink(1L,2L,3L,"deposit-reservation-3","https://checkout.stripe.com/example",new BigDecimal("100"),"CHF");
        when(requests.findByRequestKey("deposit-reservation-3")).thenReturn(Optional.of(request));when(checkout.reconcile(1L,2L,5L,"deposit-automation")).thenReturn(view);
        service.queueDeposit(2L,3L);verify(checkout,times(1)).createAutomaticDeposit(anyLong(),anyLong(),anyLong(),anyLong(),any());
        verify(delivery,times(2)).queuePaymentLink(1L,2L,3L,"deposit-reservation-3","https://checkout.stripe.com/example",new BigDecimal("100"),"CHF");
        verify(checkout,never()).capture(anyLong(),anyLong(),anyLong(),any(),anyString());
    }
}
