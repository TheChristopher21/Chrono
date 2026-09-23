package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
public class PmsGroupRoutingService {
    private final FolioRepository folios;
    private final FolioItemRepository items;
    private final PmsInvoiceLineRepository invoiceLines;
    private final PmsFinancialPeriodService periods;
    public static Set<FolioItemType> types(GroupBooking group) {
        if(group.getRoutedTypes()==null || group.getRoutedTypes().isBlank()) return Set.of();
        return Arrays.stream(group.getRoutedTypes().split(",")).map(FolioItemType::valueOf).collect(Collectors.toSet());
    }
    public void route(Reservation reservation) {
        GroupBooking group=reservation.getGroupBooking();
        if(group==null || types(group).isEmpty()) return;
        Folio master=folios.findByGroupBooking_IdAndGroupMasterTrue(group.getId()).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.CONFLICT,"Das Gruppenhauptkonto fehlt."));
        if(!master.getCurrencyCode().equals(reservation.getCurrencyCode())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Gruppenleistungen können nur in derselben Währung weitergeleitet werden.");
        Set<FolioItemType> routed=types(group);
        java.time.LocalDate closedDate=periods.lastClosedDate(group.getProperty());
        for(FolioItem item:items.findAllBySourceReservation(reservation.getId())) {
            if(!routed.contains(item.getType()) || item.getFolio().getId().equals(master.getId())) continue;
            if(item.getFolio().getStatus()!=FolioStatus.OPEN) continue;
            if(invoiceLines.existsBySourceItem_Id(item.getId()) || closedDate!=null && !item.getServiceDate().isAfter(closedDate)) continue;
            if(master.getStatus()!=FolioStatus.OPEN) throw new ResponseStatusException(HttpStatus.CONFLICT,"Das Gruppenhauptkonto ist geschlossen.");
            item.setSourceReservation(reservation);
            item.setFolio(master);
        }
    }
}
