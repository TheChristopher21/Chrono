package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsGroupOperationsDto;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.pms.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;

/** Every member succeeds or rolls back independently, through the same front-desk rules. */
@Service @RequiredArgsConstructor
public class PmsGroupMemberTransaction {
    private final PmsOperationsService operations;
    private final ReservationRepository reservations;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void execute(Company company,Long propertyId,Long groupId,Long reservationId,
                        PmsGroupOperationsDto.Action action,String username,LocalDate date) {
        var reservation=reservations.findByIdAndProperty_Company_Id(reservationId,company.getId())
                .filter(r->r.getProperty().getId().equals(propertyId) && r.getGroupBooking()!=null && r.getGroupBooking().getId().equals(groupId))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Gruppenmitglied nicht gefunden."));
        if(action==PmsGroupOperationsDto.Action.CHECK_IN) operations.checkIn(company,reservation.getId(),username,date);
        else operations.checkOut(company,reservation.getId(),username,date);
    }
}
