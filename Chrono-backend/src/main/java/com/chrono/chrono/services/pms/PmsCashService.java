package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.CashShiftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Called under the hotel row lock by posting/closing transactions. */
@Service
public class PmsCashService {
    private final CashShiftRepository shifts;
    public PmsCashService(CashShiftRepository shifts) { this.shifts = shifts; }
    public CashShift requireOpenShift(HotelProperty property, Long shiftId, String username) {
        if (shiftId != null) return shifts.findByIdAndProperty_Id(shiftId, property.getId())
                .filter(s -> s.getStatus() == CashShiftStatus.OPEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Die gewählte Kassenschicht ist nicht offen."));
        List<CashShift> open = shifts.findAllByProperty_IdAndStatusOrderByOpenedAtDesc(property.getId(), CashShiftStatus.OPEN);
        List<CashShift> own = open.stream().filter(s -> s.getOpenedBy().equals(username)).toList();
        if (own.size() == 1) return own.get(0);
        if (open.size() == 1) return open.get(0);
        throw new ResponseStatusException(HttpStatus.CONFLICT, open.isEmpty()
                ? "Vor einer Barbuchung muss eine Kassenschicht geöffnet werden."
                : "Bitte die Kassenschicht für diese Buchung auswählen.");
    }
}
