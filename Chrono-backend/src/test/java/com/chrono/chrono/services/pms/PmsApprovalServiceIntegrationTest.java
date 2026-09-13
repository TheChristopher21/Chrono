package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.RefundPaymentRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.services.UserPermissionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/** Real JPA, real property permissions and real audit persistence; no provider side effects. */
@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import({PmsApprovalService.class, PmsPropertyAccessService.class, UserPermissionService.class, PmsAuditWriter.class})
class PmsApprovalServiceIntegrationTest {
    @Autowired PmsApprovalService approvals;
    @Autowired EntityManager em;
    Company company;
    HotelProperty property;
    Payment original;

    @BeforeEach void seedRealPaymentAndTwoAuthorizedPeople() {
        company = new Company("Approval Hotel Group"); company.setEnabledFeatures(Set.of("pms")); em.persist(company);
        property = new HotelProperty(); property.setCompany(company); property.setCode("APPROVAL");
        property.setName("Approval Hotel"); property.setCurrencyCode("CHF"); em.persist(property);
        Role master = new Role("ROLE_ADMIN"); em.persist(master);
        Role staff = new Role("ROLE_USER"); em.persist(staff);
        user("master", master, null); user("requester", staff, "MANAGE");
        user("reviewer", staff, "MANAGE"); user("viewer", staff, "VIEW"); user("unassigned", staff, null);
        RoomType type = new RoomType(); type.setProperty(property); type.setCode("DBL"); type.setName("Double"); em.persist(type);
        RatePlan rate = new RatePlan(); rate.setProperty(property); rate.setRoomType(type); rate.setCode("BASE");
        rate.setName("Base"); rate.setCurrencyCode("CHF"); rate.setNightlyRate(new BigDecimal("100")); em.persist(rate);
        GuestProfile guest = new GuestProfile(); guest.setCompany(company); guest.setFirstName("Test"); guest.setLastName("Guest"); em.persist(guest);
        Reservation stay = new Reservation(); stay.setProperty(property); stay.setGuest(guest); stay.setRoomType(type); stay.setRatePlan(rate);
        stay.setConfirmationCode("APPROVAL-STAY"); stay.setArrivalDate(LocalDate.now()); stay.setDepartureDate(LocalDate.now().plusDays(2));
        stay.setCurrencyCode("CHF"); stay.setCreatedBy("requester"); em.persist(stay);
        Folio folio = new Folio(); folio.setReservation(stay); folio.setCurrencyCode("CHF"); em.persist(folio);
        original = new Payment(); original.setFolio(folio); original.setAmount(new BigDecimal("1000"));
        original.setMethod(PaymentMethod.CARD); original.setKind(PaymentKind.PAYMENT); original.setStatus(PaymentStatus.POSTED);
        original.setCreatedBy("requester"); em.persist(original); em.flush();
        approvals.savePolicy("master", property.getId(), new PmsApprovalService.PolicyInput(true, new BigDecimal("500"), null));
        reload();
    }

    @Test void requesterCannotApproveTheirOwnRequestAndSecondAuthorizedPersonCan() {
        var request = input("self-approval-001", "500");
        var pending = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), request));
        assertStatus(HttpStatus.FORBIDDEN, () -> approvals.decide("requester", property.getId(), pending.id(), decision(pending, true)));
        var approved = approvals.decide("reviewer", property.getId(), pending.id(), decision(pending, true));
        reload();
        var stored = em.find(PmsFinancialApproval.class, pending.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(stored.getRequestedBy()).isEqualTo("requester");
        assertThat(stored.getDecidedBy()).isEqualTo("reviewer");
        assertThat(stored.getDecidedAt()).isNotNull();
    }

    @Test void exactPayloadIsBoundAndAnApprovalCanOnlyBeConsumedOnce() {
        var request = new RefundPaymentRequest(new BigDecimal("500"), "Guest complaint", "bound-refund-001", 41L);
        var approved = approved(request);
        List<RefundPaymentRequest> changed = List.of(
                new RefundPaymentRequest(new BigDecimal("501"), request.reason(), request.requestId(), request.cashShiftId()),
                new RefundPaymentRequest(request.amount(), "Different reason", request.requestId(), request.cashShiftId()),
                new RefundPaymentRequest(request.amount(), request.reason(), request.requestId(), 42L),
                new RefundPaymentRequest(request.amount(), request.reason(), "different-request", request.cashShiftId()));
        for (var payload : changed) assertStatus(HttpStatus.PRECONDITION_REQUIRED,
                () -> approvals.consumeForRefund(property, original.getId(), payload));
        assertStatus(HttpStatus.PRECONDITION_REQUIRED, () -> approvals.consumeForRefund(property, original.getId() + 1000, request));
        approvals.consumeForRefund(property, original.getId(), request);
        reload();
        assertThat(em.find(PmsFinancialApproval.class, approved.id()).getStatus()).isEqualTo("CONSUMED");
        assertThat(em.find(PmsFinancialApproval.class, approved.id()).getConsumedAt()).isNotNull();
        assertStatus(HttpStatus.PRECONDITION_REQUIRED, () -> approvals.consumeForRefund(property, original.getId(), request));
        assertThat(em.createQuery("select count(a) from PmsAuditEvent a where a.eventType='approval.consumed'", Long.class).getSingleResult()).isEqualTo(1);
    }

    @Test void requestRetriesAreIdempotentButReusingTheIdForNewTermsIsRejected() {
        var request = input("retry-refund-001", "500");
        var first = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), request));
        reload();
        var retry = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), request));
        assertThat(retry.id()).isEqualTo(first.id());
        assertStatus(HttpStatus.CONFLICT, () -> approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(),
                new RefundPaymentRequest(new BigDecimal("510"), request.reason(), request.requestId(), null))));
        assertThat(em.createQuery("select count(a) from PmsFinancialApproval a", Long.class).getSingleResult()).isEqualTo(1);
    }

    @Test void cumulativePostedAndPendingRefundsReachTheInclusiveThresholdWhileFailedOnesDoNotCount() {
        refund("100", PaymentStatus.POSTED); refund("100", PaymentStatus.PENDING); refund("900", PaymentStatus.FAILED);
        reload();
        assertThatCode(() -> approvals.consumeForRefund(property, original.getId(), input("below-limit-001", "299"))).doesNotThrowAnyException();
        var atLimit = input("at-the-limit-001", "300");
        assertStatus(HttpStatus.PRECONDITION_REQUIRED, () -> approvals.consumeForRefund(property, original.getId(), atLimit));
        var approved = approved(atLimit);
        approvals.consumeForRefund(property, original.getId(), atLimit);
        reload();
        assertThat(em.find(PmsFinancialApproval.class, approved.id()).getStatus()).isEqualTo("CONSUMED");
    }

    @Test void expiredPendingRequestsCannotBeApprovedAndExpiredApprovalsCannotBeConsumed() {
        var pending = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), input("expired-pending-01", "500")));
        var approval = approved(input("expired-approved-1", "500"));
        em.find(PmsFinancialApproval.class, pending.id()).setExpiresAt(LocalDateTime.now().minusSeconds(1));
        em.find(PmsFinancialApproval.class, approval.id()).setExpiresAt(LocalDateTime.now().minusSeconds(1));
        reload();
        var expiredPending = em.find(PmsFinancialApproval.class, pending.id());
        assertStatus(HttpStatus.CONFLICT, () -> approvals.decide("reviewer", property.getId(), pending.id(),
                new PmsApprovalService.Decision(true, "Checked details", expiredPending.getVersion())));
        assertStatus(HttpStatus.PRECONDITION_REQUIRED, () -> approvals.consumeForRefund(property, original.getId(), input("expired-approved-1", "500")));
        assertThat(approvals.list("reviewer", property.getId(),0,50,"HISTORY").items()).allSatisfy(value -> assertThat(value.status()).isEqualTo("EXPIRED"));
        assertThat(approvals.list("reviewer", property.getId(),0,50,"OPEN").items()).isEmpty();
    }

    @Test void readOnlyAndUnassignedEmployeesCannotDecideOrCreateRefundApprovals() {
        var pending = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), input("permissions-001", "500")));
        assertStatus(HttpStatus.FORBIDDEN, () -> approvals.decide("viewer", property.getId(), pending.id(), decision(pending, true)));
        assertStatus(HttpStatus.FORBIDDEN, () -> approvals.request("unassigned", property.getId(), new PmsApprovalService.Request(original.getId(), input("unassigned-001", "500"))));
        assertStatus(HttpStatus.FORBIDDEN, () -> approvals.savePolicy("requester", property.getId(), new PmsApprovalService.PolicyInput(false, BigDecimal.ONE, 0L)));
        assertThat(approvals.list("viewer", property.getId(),0,50,"OPEN").items()).hasSize(1);
    }

    @Test void declinedOrStaleDecisionsCannotAuthorizeARefund() {
        var request = input("declined-refund-1", "500");
        var pending = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), request));
        assertStatus(HttpStatus.CONFLICT, () -> approvals.decide("reviewer", property.getId(), pending.id(),
                new PmsApprovalService.Decision(true, "Stale version", pending.version() + 1)));
        approvals.decide("reviewer", property.getId(), pending.id(), decision(pending, false));
        reload();
        assertStatus(HttpStatus.PRECONDITION_REQUIRED, () -> approvals.consumeForRefund(property, original.getId(), request));
    }

    @Test void olderOpenApprovalsRemainReachableBeyondOneHundredCompletedRequestsAndHistoryHasStablePages() {
        var open = approved(input("older-open-refund", "500"));
        for (int index=0;index<105;index++) {
            PmsFinancialApproval row=new PmsFinancialApproval(); row.setProperty(property); row.setPaymentId(original.getId());
            row.setRequestKey("completed-"+index); row.setAmount(new BigDecimal("500")); row.setCurrency("CHF");
            row.setReason("Completed request"); row.setRequestHash("0".repeat(64)); row.setRequestedBy("requester");
            row.setStatus(index%2==0?"CONSUMED":"REJECTED"); row.setCreatedAt(LocalDateTime.now()); row.setExpiresAt(LocalDateTime.now().plusHours(24));
            em.persist(row);
        }
        // Another hotel's rows must not inflate the count, even inside the same chain.
        HotelProperty other=new HotelProperty();other.setCompany(company);other.setCode("OTHER");other.setName("Other Hotel");em.persist(other);
        PmsFinancialApproval foreign=new PmsFinancialApproval();foreign.setProperty(other);foreign.setPaymentId(original.getId());foreign.setRequestKey("other-hotel-approval");
        foreign.setAmount(BigDecimal.ONE);foreign.setCurrency("CHF");foreign.setReason("Other hotel's request");foreign.setRequestHash("1".repeat(64));
        foreign.setRequestedBy("master");foreign.setCreatedAt(LocalDateTime.now());foreign.setExpiresAt(LocalDateTime.now().plusHours(24));em.persist(foreign);
        reload();
        var openPage=approvals.list("reviewer",property.getId(),0,50,"OPEN");
        assertThat(openPage.items()).singleElement().satisfies(value->assertThat(value.id()).isEqualTo(open.id()));
        assertThat(openPage.totalElements()).isEqualTo(1);assertThat(openPage.hasNext()).isFalse();
        var first=approvals.list("reviewer",property.getId(),0,50,"HISTORY");
        var second=approvals.list("reviewer",property.getId(),1,50,"HISTORY");
        var last=approvals.list("reviewer",property.getId(),2,50,"HISTORY");
        assertThat(first.totalElements()).isEqualTo(105);assertThat(first.items()).hasSize(50);assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).hasSize(50);assertThat(second.items()).extracting(PmsApprovalService.View::id).doesNotContainAnyElementsOf(first.items().stream().map(PmsApprovalService.View::id).toList());
        assertThat(last.items()).hasSize(5);assertThat(last.hasNext()).isFalse();
        var allLast=approvals.list("reviewer",property.getId(),2,50,"ALL");
        assertThat(allLast.totalElements()).isEqualTo(106);assertThat(allLast.items()).extracting(PmsApprovalService.View::id).contains(open.id());
    }

    @Test void approvalPagesRejectUnboundedQueriesAndUnknownFilters() {
        assertStatus(HttpStatus.BAD_REQUEST,()->approvals.list("reviewer",property.getId(),-1,50,"OPEN"));
        assertStatus(HttpStatus.BAD_REQUEST,()->approvals.list("reviewer",property.getId(),0,101,"ALL"));
        assertStatus(HttpStatus.BAD_REQUEST,()->approvals.list("reviewer",property.getId(),0,50,"UNRECOGNIZED"));
    }

    private PmsApprovalService.View approved(RefundPaymentRequest request) {
        var pending = approvals.request("requester", property.getId(), new PmsApprovalService.Request(original.getId(), request));
        return approvals.decide("reviewer", property.getId(), pending.id(), decision(pending, true));
    }
    private PmsApprovalService.Decision decision(PmsApprovalService.View value, boolean approve) {
        return new PmsApprovalService.Decision(approve, "Second person checked details", value.version());
    }
    private RefundPaymentRequest input(String key, String amount) {
        return new RefundPaymentRequest(new BigDecimal(amount), "Guest complaint", key, null);
    }
    private void refund(String amount, PaymentStatus status) {
        Payment refund = new Payment(); refund.setFolio(original.getFolio()); refund.setOriginalPayment(original);
        refund.setAmount(new BigDecimal(amount).negate()); refund.setKind(PaymentKind.REFUND); refund.setMethod(PaymentMethod.CARD);
        refund.setStatus(status); refund.setCreatedBy("requester"); em.persist(refund);
    }
    private void user(String name, Role role, String refundLevel) {
        User user = new User(); user.setUsername(name); user.setPassword("test-only"); user.setCompany(company);
        user.setCountry("CH"); user.setPersonnelNumber(name); user.setRoles(Set.of(role)); user.setPagePermissions(Map.of("pms", "MANAGE")); em.persist(user);
        if (refundLevel != null) {
            PmsPropertyGrant grant = new PmsPropertyGrant(); grant.setUser(user); grant.setProperty(property);
            grant.setPermissions(Map.of("REFUNDS", refundLevel)); em.persist(grant);
        }
    }
    private void reload() {
        Long propertyId = property.getId(), paymentId = original.getId();
        em.flush(); em.clear(); property = em.find(HotelProperty.class, propertyId); original = em.find(Payment.class, paymentId);
    }
    private void assertStatus(HttpStatus expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(expected));
    }
}
