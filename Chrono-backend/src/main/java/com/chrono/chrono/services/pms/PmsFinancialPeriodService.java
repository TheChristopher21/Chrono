package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsFinancialDayResponse;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** A hotel-wide posting boundary, serialized by the same property lock as financial writes. */
@Service
@Transactional
public class PmsFinancialPeriodService {
    private final PmsFinancialPeriodRepository periods;
    private final HotelPropertyRepository properties;
    private final EntityManager em;

    public PmsFinancialPeriodService(PmsFinancialPeriodRepository periods, HotelPropertyRepository properties, EntityManager em) {
        this.periods = periods; this.properties = properties; this.em = em;
    }

    public LocalDate currentBusinessDate(HotelProperty property) { return lockedPeriod(property).getBusinessDate(); }

    @Transactional(readOnly=true)
    public LocalDate readBusinessDate(HotelProperty property) {
        return periods.findById(property.getId()).map(PmsFinancialPeriod::getBusinessDate).orElseGet(()->{
            LocalDate closed=latestLegacyClose(property);
            return closed==null?LocalDate.now(ZoneId.of(property.getTimezone())):closed.plusDays(1);
        });
    }

    @Transactional(readOnly = true)
    public boolean isClosed(HotelProperty property, LocalDate date) {
        LocalDate closed = lastClosedDate(property);
        return date != null && closed != null && !date.isAfter(closed);
    }

    @Transactional(readOnly=true)
    public LocalDate lastClosedDate(HotelProperty property) {
        PmsFinancialPeriod period=periods.findById(property.getId()).orElse(null);
        return period==null?latestLegacyClose(property):period.getLastClosedDate();
    }

    public void assertPostingOpen(HotelProperty property, LocalDate postingDate) {
        PmsFinancialPeriod period = lockedPeriod(property);
        if (postingDate == null || period.getLastClosedDate() != null && !postingDate.isAfter(period.getLastClosedDate())) {
            throw conflict("Der Leistungs- oder Buchungstag ist bereits abgeschlossen. Korrekturen müssen auf dem offenen Betriebstag erfolgen.");
        }
    }

    public PmsFinancialDayResponse status(HotelProperty property) {
        PmsFinancialPeriod period = lockedPeriod(property);
        long cash = em.createQuery("select count(s) from CashShift s where s.property.id=:property and s.status=:status", Long.class)
                .setParameter("property", property.getId()).setParameter("status", CashShiftStatus.OPEN).getSingleResult();
        long departures = em.createQuery("select count(r) from Reservation r where r.property.id=:property and r.status=:status and r.departureDate<=:date", Long.class)
                .setParameter("property", property.getId()).setParameter("status", ReservationStatus.CHECKED_IN)
                .setParameter("date", period.getBusinessDate()).getSingleResult();
        long arrivals = em.createQuery("select count(r) from Reservation r where r.property.id=:property and r.status=:status and r.arrivalDate<=:date", Long.class)
                .setParameter("property", property.getId()).setParameter("status", ReservationStatus.CONFIRMED)
                .setParameter("date", period.getBusinessDate()).getSingleResult();
        List<String> blockers = new ArrayList<>();
        if (cash > 0) blockers.add(cash + " Kassenschichten sind noch geöffnet.");
        if (departures > 0) blockers.add(departures + " fällige Abreisen müssen ausgecheckt oder verlängert werden.");
        if (arrivals > 0) blockers.add(arrivals + " bestätigte Anreisen müssen eingecheckt, verschoben oder als No-show abgeschlossen werden.");
        if (period.getBusinessDate().isAfter(LocalDate.now(ZoneId.of(property.getTimezone())))) blockers.add("Ein zukünftiger Betriebstag kann noch nicht abgeschlossen werden.");
        return new PmsFinancialDayResponse(period.getBusinessDate(), period.getLastClosedDate(), cash, arrivals, departures, blockers.isEmpty(), List.copyOf(blockers));
    }

    public List<Reservation> pendingArrivals(HotelProperty property, LocalDate date) {
        return em.createQuery("select r from Reservation r where r.property.id=:property and r.status=:status and r.arrivalDate<=:date order by r.arrivalDate, r.id", Reservation.class)
                .setParameter("property", property.getId()).setParameter("status", ReservationStatus.CONFIRMED).setParameter("date", date).getResultList();
    }

    public void assertCanClose(HotelProperty property, LocalDate requested, boolean markNoShows) {
        PmsFinancialDayResponse status = status(property);
        if (status.lastClosedDate() != null && requested != null && !requested.isAfter(status.lastClosedDate())) {
            throw conflict("Dieser Betriebstag wurde bereits abgeschlossen.");
        }
        if (!status.businessDate().equals(requested)) throw conflict("Abgeschlossen werden muss der aktuelle Betriebstag " + status.businessDate() + ".");
        if (requested.isAfter(LocalDate.now(ZoneId.of(property.getTimezone())))) throw conflict("Ein zukünftiger Betriebstag kann nicht abgeschlossen werden.");
        if (status.openCashShifts() > 0 || status.pendingDepartures() > 0 || status.pendingArrivals() > 0 && !markNoShows) {
            throw conflict(String.join(" ", status.blockers()));
        }
    }

    public void completeClose(HotelProperty property, LocalDate closedDate) {
        PmsFinancialPeriod period = lockedPeriod(property);
        if (!closedDate.equals(period.getBusinessDate())) throw conflict("Der Betriebstag wurde bereits weitergeschaltet.");
        period.setLastClosedDate(closedDate);
        period.setBusinessDate(closedDate.plusDays(1));
        periods.save(period);
    }

    private PmsFinancialPeriod lockedPeriod(HotelProperty property) {
        properties.findByIdAndCompany_IdForUpdate(property.getId(), property.getCompany().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotelbetrieb nicht gefunden."));
        return periods.findById(property.getId()).orElseGet(() -> {
            PmsFinancialPeriod period = new PmsFinancialPeriod();
            period.setProperty(property);
            LocalDate closed = latestLegacyClose(property);
            period.setLastClosedDate(closed);
            period.setBusinessDate(closed == null ? LocalDate.now(ZoneId.of(property.getTimezone())) : closed.plusDays(1));
            return periods.saveAndFlush(period);
        });
    }

    private LocalDate latestLegacyClose(HotelProperty property) {
        return em.createQuery("select max(a.businessDate) from NightAudit a where a.property.id=:property", LocalDate.class)
                .setParameter("property", property.getId()).getSingleResult();
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
