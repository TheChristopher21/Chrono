package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PmsSetupAccessTest {
    @SuppressWarnings("unchecked")
    @Test void authenticatedStaffUseOnlyTheirHotelIdsBeforeBuildingAnyRoomViews() {
        var properties=mock(HotelPropertyRepository.class);var rooms=mock(RoomRepository.class);var types=mock(RoomTypeRepository.class);
        var service=new PmsSetupService(properties,types,rooms);var access=mock(PmsPropertyAccessService.class);
        ObjectProvider<PmsPropertyAccessService> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(access);service.setAccessProvider(provider);
        Company company=new Company("Chain");company.setId(3L);
        when(access.access("staff")).thenReturn(new PmsPropertyAccessService.Access(7L,3L,false,Map.of(5L,Map.of("HOUSEKEEPING","VIEW"))));
        assertThat(service.getSetupForUser(company,false,"staff").properties()).isEmpty();
        verify(properties).findAllByCompany_IdAndIdInOrderByNameAsc(3L,Set.of(5L));
        verify(properties,never()).findAllByCompany_IdOrderByNameAsc(any());verifyNoInteractions(rooms,types);
        when(access.access("staff")).thenReturn(new PmsPropertyAccessService.Access(7L,3L,false,Map.of()));
        assertThat(service.getSetupForUser(company,false,"staff").totalProperties()).isZero();
    }
    @Test void authenticatedSetupFailsClosedWithoutGrantService() {
        var properties=mock(HotelPropertyRepository.class);var service=new PmsSetupService(properties,mock(RoomTypeRepository.class),mock(RoomRepository.class));
        Company company=new Company("Chain");company.setId(3L);
        assertThatThrownBy(() -> service.getSetupForUser(company,true,"staff")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(properties);
    }
}
