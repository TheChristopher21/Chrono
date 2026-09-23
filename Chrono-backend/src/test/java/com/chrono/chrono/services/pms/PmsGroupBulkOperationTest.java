package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsGroupOperationsDto;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsGroupBulkOperationTest {
    @Test void recordsMemberConflictAndContinuesWithoutRetryingSuccessfulMembers() {
        var tx=mock(PmsGroupMemberTransaction.class);
        var service=new PmsGroupService(null,null,null,null,null,null,null,null,null,null,null,null,tx,null);
        var company=new Company("Test");
        LocalDate date=LocalDate.of(2026,9,12);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Zimmer ist noch schmutzig"))
                .when(tx).execute(company,1L,2L,4L,PmsGroupOperationsDto.Action.CHECK_IN,"desk",date);
        var result=service.bulk(company,1L,2L,new PmsGroupOperationsDto.BulkOperation(
                PmsGroupOperationsDto.Action.CHECK_IN,List.of(3L,4L,5L,3L),date),"desk");
        assertThat(result).extracting(PmsGroupOperationsDto.MemberResult::success).containsExactly(true,false,true);
        assertThat(result.get(1).message()).isEqualTo("Zimmer ist noch schmutzig");
        verify(tx,times(1)).execute(company,1L,2L,3L,PmsGroupOperationsDto.Action.CHECK_IN,"desk",date);
        verify(tx).execute(company,1L,2L,5L,PmsGroupOperationsDto.Action.CHECK_IN,"desk",date);
    }

    @Test void memberTransactionEnforcesGroupMembershipBeforeDelegatingExistingCheckin() {
        var repository=mock(ReservationRepository.class);
        var operations=mock(PmsOperationsService.class);
        var company=new Company("Hotel");company.setId(1L);
        var property=new HotelProperty();org.springframework.test.util.ReflectionTestUtils.setField(property, "id", 2L);property.setCompany(company);
        var group=new GroupBooking();group.setId(3L);group.setProperty(property);
        var member=new Reservation();member.setId(4L);member.setProperty(property);member.setGroupBooking(group);
        when(repository.findByIdAndProperty_Company_Id(4L,1L)).thenReturn(Optional.of(member));
        var service=new PmsGroupMemberTransaction(operations,repository);
        service.execute(company,2L,3L,4L,PmsGroupOperationsDto.Action.CHECK_IN,"desk",null);
        verify(operations).checkIn(company,4L,"desk",null);
        assertThatThrownBy(()->service.execute(company,2L,99L,4L,PmsGroupOperationsDto.Action.CHECK_OUT,"desk",null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(operations,never()).checkOut(any(),any(),anyString(),any());
    }
}
