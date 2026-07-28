package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PmsPrivacyServiceTest {
    private final GuestProfileRepository guestRepository = mock(GuestProfileRepository.class);
    private final HotelPropertyRepository propertyRepository = mock(HotelPropertyRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final FolioRepository folioRepository = mock(FolioRepository.class);
    private final PmsInvoiceRepository invoiceRepository = mock(PmsInvoiceRepository.class);
    private final GuestCommunicationRepository communicationRepository =
            mock(GuestCommunicationRepository.class);
    private final GuestRegistrationRepository registrationRepository =
            mock(GuestRegistrationRepository.class);
    private final PmsAuditEventRepository auditRepository = mock(PmsAuditEventRepository.class);
    private final PmsAuditWriter auditWriter = mock(PmsAuditWriter.class);
    private final PmsPrivacyService service = new PmsPrivacyService(
            guestRepository, propertyRepository, reservationRepository, folioRepository,
            invoiceRepository, communicationRepository, registrationRepository,
            auditRepository, auditWriter);
    private final Company company = new Company("Chrono Hotel AG");
    private final GuestProfile guest = new GuestProfile();
    private final Reservation reservation = new Reservation();
    private final Folio folio = new Folio();
    private final HotelProperty property = mock(HotelProperty.class);

    @BeforeEach
    void setUp() {
        company.setId(3L);
        guest.setId(9L);
        guest.setCompany(company);
        guest.setFirstName("Gabriela");
        guest.setLastName("Tschopp");
        guest.setEmail("gabriela@example.com");
        guest.setPhone("+41000000000");
        reservation.setId(11L);
        reservation.setGuest(guest);
        reservation.setProperty(property);
        reservation.setStatus(ReservationStatus.CHECKED_OUT);
        reservation.setArrivalDate(LocalDate.now().minusDays(3));
        reservation.setDepartureDate(LocalDate.now().minusDays(1));
        folio.setId(12L);
        folio.setReservation(reservation);
        folio.setStatus(FolioStatus.CLOSED);
        when(guestRepository.findByIdAndCompany_Id(9L, 3L)).thenReturn(Optional.of(guest));
        when(reservationRepository.findAllByGuest_IdOrderByArrivalDateDesc(9L))
                .thenReturn(List.of(reservation));
        when(folioRepository.findAllByReservation_IdOrderByIdAsc(11L)).thenReturn(List.of(folio));
        when(communicationRepository.findAllByGuest_IdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of());
        when(registrationRepository.findByReservation_Id(11L)).thenReturn(Optional.empty());
    }

    @Test
    void anonymizesFinishedGuestWhileRetainingFinancialReferences() {
        var response = service.anonymizeGuest(
                company, 9L, "Auf Antrag der betroffenen Person", "Christopher");

        assertThat(response.anonymized()).isTrue();
        assertThat(guest.getFirstName()).isEqualTo("Anonymisiert");
        assertThat(guest.getLastName()).isEqualTo("GAST-9");
        assertThat(guest.getEmail()).isNull();
        assertThat(guest.getPhone()).isNull();
        assertThat(folio.getStatus()).isEqualTo(FolioStatus.CLOSED);
        verify(auditWriter).append(
                eq(property), eq("privacy.guest_anonymized"), eq("guest"), eq("9"), anyString());
    }

    @Test
    void blocksAnonymizationWithActiveReservation() {
        reservation.setStatus(ReservationStatus.CHECKED_IN);

        assertThatThrownBy(() -> service.anonymizeGuest(
                company, 9L, "Antrag", "Christopher"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("aktiver oder zukünftiger");
        verify(guestRepository, never()).save(any());
    }
}
