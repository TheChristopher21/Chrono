package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.CompleteGuestRegistrationRequest;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PmsInternationalSettingsServiceTest {
 final EntityManager em=mock(EntityManager.class);final PmsInternationalSettingsService service=new PmsInternationalSettingsService(em,mock(HotelPropertyRepository.class),mock(PmsPropertyAccessService.class),mock(PmsAuditWriter.class),new ObjectMapper());
 @Test void newInvitesFreezeConfiguredFieldsAndLaterEditsDoNotChangeExistingInvites(){HotelProperty hotel=new HotelProperty();org.springframework.test.util.ReflectionTestUtils.setField(hotel,"id",9L);hotel.setCountryCode("DE");Reservation stay=new Reservation();stay.setProperty(hotel);GuestRegistration registration=new GuestRegistration();registration.setReservation(stay);PmsInternationalSettings config=new PmsInternationalSettings();config.setPropertyId(9L);config.setVersion(3);config.setRuleCode("LOCAL-2026");config.setRequiredFieldsJson("[\"city\",\"signatureName\"]");when(em.find(PmsInternationalSettings.class,9L)).thenReturn(config);service.snapshot(registration);config.setRequiredFieldsJson("[\"documentNumber\"]");assertThat(registration.getRuleVersion()).isEqualTo(4);assertThat(service.requiredFields(registration)).containsExactly("city","signatureName");service.validate(registration,new CompleteGuestRegistrationRequest(null,null,"Berlin",null,null,null,null,"Alex",false,null,null));assertThat(registration.getDocumentHash()).isNull();}
 @Test void oldInvitesRetainTheirRequiredFieldsAndOptionalProvidedDataIsStillValidated(){GuestRegistration old=new GuestRegistration();assertThat(service.requiredFields(old)).contains("documentNumber","privacyConsent");assertThatThrownBy(()->service.validate(old,new CompleteGuestRegistrationRequest(null,null,null,null,null,null,null,null,false,null,null))).hasMessageContaining("Pflichtfeld");old.setRequiredFieldsSnapshot("[]");assertThatThrownBy(()->service.validate(old,new CompleteGuestRegistrationRequest(null,null,null,"ZZ",null,null,null,null,false,null,null))).hasMessageContaining("ISO-Ländercode");}
}
