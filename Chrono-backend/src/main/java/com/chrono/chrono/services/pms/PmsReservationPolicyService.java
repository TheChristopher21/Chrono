package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.ReservationPolicyView;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

@Service
public class PmsReservationPolicyService {
    private final FolioRepository folios;
    private final FolioItemRepository items;
    private final PaymentRepository payments;
    private final PmsInvoiceLineRepository invoiceLines;
    private final PmsFinancialPeriodService periods;
    public PmsReservationPolicyService(FolioRepository folios, FolioItemRepository items, PaymentRepository payments,
            PmsInvoiceLineRepository invoiceLines, PmsFinancialPeriodService periods) {
        this.folios = folios; this.items = items; this.payments = payments; this.invoiceLines = invoiceLines; this.periods = periods;
    }
    public static void snapshot(Reservation reservation, RatePlan rate) {
        reservation.setPolicySnapshotAt(LocalDateTime.now());
        reservation.setPolicyCancellationDeadlineHours(rate.getCancellationDeadlineHours());
        reservation.setPolicyCancellationFeePercent(rate.getCancellationFeePercent());
        reservation.setPolicyNoShowFeePercent(rate.getNoShowFeePercent());
        reservation.setPolicyFeeTaxRate(rate.getPolicyFeeTaxRate());
        reservation.setPolicyDepositPercent(rate.getDepositPercent());
        reservation.setPolicyDepositDueDaysBeforeArrival(rate.getDepositDueDaysBeforeArrival());
        reservation.setPolicyCheckInTime(rate.getProperty().getCheckInTime());
    }
    public ReservationPolicyView view(Reservation r) {
        // Legacy bookings have no accepted policy snapshot. Never retroactively apply current rate rules.
        BigDecimal required = percent(r.getTotalAmount(), r.getPolicyDepositPercent(), r.getCurrencyCode());
        List<Long> ids = folios.findAllByReservation_IdOrderByIdAsc(r.getId()).stream().filter(f -> !f.isGroupMaster()).map(Folio::getId).toList();
        BigDecimal paid = ids.isEmpty() ? BigDecimal.ZERO : payments.findAllByFolio_IdInOrderByReceivedAtAsc(ids).stream()
                .filter(p -> p.getStatus() == PaymentStatus.POSTED).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate due = r.getArrivalDate().minusDays(r.getPolicyDepositDueDaysBeforeArrival() == null ? 0 : r.getPolicyDepositDueDaysBeforeArrival());
        LocalDateTime deadline = r.getPolicyCancellationDeadlineHours() == null ? null : r.getArrivalDate()
                .atTime(r.getPolicyCheckInTime() == null ? r.getProperty().getCheckInTime() : r.getPolicyCheckInTime()).minusHours(r.getPolicyCancellationDeadlineHours());
        BigDecimal outstanding = required.subtract(paid).max(BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.now(ZoneId.of(r.getProperty().getTimezone()));
        BigDecimal cancel = deadline == null || !now.isBefore(deadline) ? percent(r.getTotalAmount(), r.getPolicyCancellationFeePercent(), r.getCurrencyCode()) : BigDecimal.ZERO;
        boolean needsCorrection = (r.getStatus() == ReservationStatus.CANCELLED || r.getStatus() == ReservationStatus.NO_SHOW)
                && !items.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(r.getId()).isEmpty();
        return new ReservationPolicyView(r.getId(), r.getPolicySnapshotAt(), zero(r.getPolicyDepositPercent()), required, paid,
                outstanding, due, !now.toLocalDate().isBefore(due) && outstanding.signum() > 0, deadline, cancel,
                percent(r.getTotalAmount(), r.getPolicyNoShowFeePercent(), r.getCurrencyCode()), r.getPolicyFeeTaxRate(), needsCorrection);
    }
    public void applyCancellation(Reservation reservation, boolean noShow) {
        ReservationPolicyView policy = view(reservation);
        BigDecimal fee = noShow ? policy.noShowFee() : policy.cancellationFeeNow();
        if (fee.signum() > 0 && reservation.getPolicyFeeTaxRate() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Für die vereinbarte Storno-/No-Show-Gebühr fehlt ein ausdrücklich konfigurierter Steuersatz.");
        LocalDate date = periods.currentBusinessDate(reservation.getProperty());
        periods.assertPostingOpen(reservation.getProperty(), date);
        BigDecimal protectedCharges = BigDecimal.ZERO;
        for (FolioItem line : items.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(reservation.getId())) {
            if (line.getFolio().getStatus() == FolioStatus.CLOSED || periods.isClosed(reservation.getProperty(), line.getServiceDate())
                    || invoiceLines.existsBySourceItem_Id(line.getId())) {
                // Released inventory must not mutate invoiced/closed history. The policy view explicitly flags a required credit correction.
                protectedCharges = protectedCharges.add(line.getTotalAmount());
            } else items.delete(line);
        }
        BigDecimal additionalFee = fee.subtract(protectedCharges).max(BigDecimal.ZERO);
        if (additionalFee.signum() > 0) {
            Folio folio = folios.findAllByReservation_IdOrderByIdAsc(reservation.getId()).stream()
                    .filter(f -> !f.isGroupMaster() && f.getStatus() == FolioStatus.OPEN).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ein offenes Gastkonto für die Gebühr ist erforderlich."));
            FolioItem line = new FolioItem(); line.setFolio(folio); line.setSourceReservation(reservation); line.setServiceDate(date);
            line.setType(FolioItemType.OTHER); line.setDescription(noShow ? "Vereinbarte No-Show-Gebühr" : "Vereinbarte Stornogebühr");
            line.setQuantity(BigDecimal.ONE); line.setUnitPrice(additionalFee); line.setTotalAmount(additionalFee);
            line.setTaxRate(reservation.getPolicyFeeTaxRate()); line.setTaxIncluded(true); line.setRateGenerated(false); items.save(line);
        }
        items.flush();
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal percent(BigDecimal total, BigDecimal percent, String currencyCode) { return PmsMoney.round(zero(total).multiply(zero(percent)).movePointLeft(2), currencyCode); }
}
