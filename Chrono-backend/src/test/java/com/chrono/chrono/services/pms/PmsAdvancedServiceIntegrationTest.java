package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PmsOperationsService.class, com.chrono.chrono.services.pms.PmsGroupRoutingService.class, com.chrono.chrono.services.pms.PmsGroupInventoryService.class, com.chrono.chrono.services.pms.PmsHousekeepingService.class, com.chrono.chrono.services.pms.PmsReservationPolicyService.class, PmsRefundProcessor.class, PmsCashService.class, PmsFinancialPeriodService.class, PmsAdvancedService.class, PmsAuditWriter.class,
        PmsDocumentFingerprintService.class, PmsProfileDocumentService.class,PmsEventOrderService.class})
@ActiveProfiles("test")
class PmsAdvancedServiceIntegrationTest {

    @Autowired
    private PmsAdvancedService service;
    @Autowired
    private PmsProfileDocumentService documentService;
    @Autowired
    private PmsInvoiceLineRepository invoiceLineRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PmsOperationsService operationsService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private HotelPropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private GuestProfileRepository guestRepository;
    @Autowired
    private RatePlanRepository ratePlanRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private GroupBookingRepository groupRepository;
    @Autowired
    private PmsOrganizationRepository organizationRepository;
    @Autowired
    private FolioItemRepository folioItemRepository;
    @Autowired
    private NightAuditRepository nightAuditRepository;
    @Autowired
    private PmsInvoiceRepository invoiceRepository;
    @Autowired
    private FolioRepository folioRepository;
    @Autowired
    private CashShiftRepository cashShiftRepository;
    @Autowired
    private PmsFinancialPeriodRepository financialPeriodRepository;
    @Autowired
    private PmsReceivablesService receivablesService;
    @Autowired
    private PmsReceivableRepository receivableRepository;
    @Autowired
    private PmsReceivableSettlementRepository settlementRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private GuestRegistrationRepository guestRegistrationRepository;
    @Autowired private PmsGroupService groupOperations;
    @Autowired private PmsGroupAllotmentRepository allotmentRepository;
    @Autowired private PmsGroupInventoryService groupInventory;
    @Autowired private PmsEventOrderService eventOrders;
    @Autowired private HotelResourceRepository resourceRepository;
    @Autowired private ResourceBookingRepository resourceBookingRepository;

    private Company company;
    private HotelProperty property;
    private RoomType roomType;
    private Room room101;
    private Room room102;
    private GuestProfile guest;
    private GuestProfile secondGuest;
    private RatePlan ratePlan;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Chrono Hotel AG"));
        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
        property.setLegalName("Chrono Hotel AG");
        property.setAddressLine1("Musterstrasse 1");
        property.setPostalCode("8000");
        property.setCity("Zürich");
        property.setTimezone("Europe/Zurich");
        property.setCurrencyCode("CHF");
        property = propertyRepository.save(property);

        roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCode("DBL");
        roomType.setName("Doppelzimmer");
        roomType.setBaseOccupancy(1);
        roomType.setMaxOccupancy(2);
        roomType = roomTypeRepository.save(roomType);
        room101 = room("101");
        room102 = room("102");
        guest = guest("Gabriela", "Tschopp", "gabriela@example.com");
        secondGuest = guest("Raja", "Siefert", "raja@example.com");

        ratePlan = new RatePlan();
        ratePlan.setProperty(property);
        ratePlan.setRoomType(roomType);
        ratePlan.setCode("BAR");
        ratePlan.setName("Beste verfügbare Rate");
        ratePlan.setCurrencyCode("CHF");
        ratePlan.setNightlyRate(new BigDecimal("108.10"));
        ratePlan.setMinStay(1);
        ratePlan = ratePlanRepository.save(ratePlan);
        today = LocalDate.now(ZoneId.of("Europe/Zurich"));
    }

    @Test
    void createsAtomicGroupWithRoomingListAndFolios() {
        PmsAdvancedResponse response = service.createGroupBooking(
                company,
                groupRequest(List.of(
                        rooming(guest, room101),
                        rooming(secondGuest, room102)
                )),
                "Christopher",
                today
        );

        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().get(0).rooms()).hasSize(2);
        assertThat(response.groups().get(0).rooms())
                .extracting(PmsAdvancedResponse.GroupMemberView::guestName)
                .containsExactlyInAnyOrder("Gabriela Tschopp", "Raja Siefert");
        assertThat(reservationRepository.findAll()).hasSize(2);
        assertThat(groupRepository.findAll()).singleElement()
                .extracting(GroupBooking::getGroupCode).isEqualTo("TEAM-26");
        assertThat(operationsService.getOperations(company, property.getId(), today, null, null).folios())
                .hasSize(2);
    }

    @Test
    void unnamedAllotmentReservesCapacityAndPickupDoesNotDoubleCount() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of()),"test",today).groups().get(0).id();
        groupOperations.addAllotment(company,property.getId(),groupId,new PmsGroupOperationsDto.Allotment(
                roomType.getId(),today.plusDays(1),today.plusDays(3),2,today.plusDays(1)));
        assertThat(operationsService.getAvailability(company,property.getId(),today.plusDays(1),today.plusDays(3)).roomTypes().get(0).availableRooms()).isZero();
        groupOperations.appendMembers(company,property.getId(),groupId,new PmsGroupOperationsDto.RoomingList(List.of(rooming(guest,room101))),"test");
        var state=groupOperations.view(company,property.getId(),groupId);
        assertThat(state.allotments().get(0).nights()).allSatisfy(n->{assertThat(n.pickedUp()).isEqualTo(1);assertThat(n.held()).isEqualTo(1);});
        assertThat(operationsService.getAvailability(company,property.getId(),today.plusDays(1),today.plusDays(3)).roomTypes().get(0).availableRooms()).isZero();
        groupOperations.release(company,property.getId(),groupId,state.allotments().get(0).id());
        assertThat(operationsService.getAvailability(company,property.getId(),today.plusDays(1),today.plusDays(3)).roomTypes().get(0).availableRooms()).isEqualTo(1);
    }

    @Test
    void allotmentPreventsOutsideReservationAndRejectsOverselling() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of()),"test",today).groups().get(0).id();
        groupOperations.addAllotment(company,property.getId(),groupId,new PmsGroupOperationsDto.Allotment(
                roomType.getId(),today.plusDays(1),today.plusDays(3),2,today.plusDays(1)));
        assertThatThrownBy(()->operationsService.createReservation(company,reservationRequest(),"outside",today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ausgebucht");
    }

    @Test
    void rejectsAllotmentBeyondPhysicalInventory() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of()),"test",today).groups().get(0).id();
        assertThatThrownBy(()->groupOperations.addAllotment(company,property.getId(),groupId,new PmsGroupOperationsDto.Allotment(
                roomType.getId(),today.plusDays(1),today.plusDays(3),3,today.plusDays(1))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Kapazität");
    }

    @Test
    void expiredAllotmentReleasesWithoutSchedulerAndMixedStayPickupIsPerNight() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of()),"test",today).groups().get(0).id();
        groupOperations.addAllotment(company,property.getId(),groupId,new PmsGroupOperationsDto.Allotment(
                roomType.getId(),today.plusDays(1),today.plusDays(3),2,today.plusDays(1)));
        var room=new CreateGroupBookingRequest.RoomingEntry(guest.getId(),roomType.getId(),room101.getId(),ratePlan.getId(),1,0,
                ReservationSource.DIRECT,null,null,today.plusDays(2),today.plusDays(3));
        groupOperations.appendMembers(company,property.getId(),groupId,new PmsGroupOperationsDto.RoomingList(List.of(room)),"test");
        var state=groupOperations.view(company,property.getId(),groupId);
        assertThat(state.allotments().get(0).nights().get(0).pickedUp()).isZero();
        assertThat(state.allotments().get(0).nights().get(1).pickedUp()).isEqualTo(1);
        PmsGroupAllotment stored=allotmentRepository.findById(state.allotments().get(0).id()).orElseThrow();
        stored.setReleaseDate(today);
        assertThat(groupInventory.heldRooms(property.getId(),roomType.getId(),today.plusDays(2),null)).isZero();
        var member=service.getAdvanced(company,property.getId(),today).groups().get(0).rooms().get(0);
        assertThat(member.arrivalDate()).isEqualTo(today.plusDays(2));
    }

    @Test
    void routingMovesGroupChargesPreservesSourceAndRoutesNewMembers() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of(rooming(guest,room101))),"test",today).groups().get(0).id();
        var state=groupOperations.configureRouting(company,property.getId(),groupId,new PmsGroupOperationsDto.Routing(java.util.Set.of(FolioItemType.ROOM)));
        Long firstId=reservationRepository.findAllByGroupBooking_IdOrderByGuest_LastNameAsc(groupId).get(0).getId();
        groupOperations.appendMembers(company,property.getId(),groupId,new PmsGroupOperationsDto.RoomingList(List.of(rooming(secondGuest,room102))),"test");
        List<FolioItem> masterItems=folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(state.masterFolioId());
        assertThat(masterItems).hasSize(4).allSatisfy(i->assertThat(i.getSourceReservation()).isNotNull());
        assertThat(masterItems.stream().map(i->i.getSourceReservation().getId()).distinct().count()).isEqualTo(2);
        assertThat(folioItemRepository.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(firstId)).hasSize(2);
        assertThat(folioRepository.findById(state.masterFolioId()).orElseThrow().isGroupMaster()).isTrue();
        var memberFolio=folioRepository.findFirstByReservation_IdOrderByIdAsc(firstId).orElseThrow();
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(memberFolio.getId())).isEmpty();
        operationsService.postFolioItem(company,property.getId(),memberFolio.getId(),new PostFolioItemRequest(today.plusDays(1),FolioItemType.SERVICE,
                "Minibar",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO),today);
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(memberFolio.getId())).hasSize(1);
    }

    @Test
    void groupMasterBalanceDoesNotBlockMemberCheckoutOrCloseGroupAccount() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of(rooming(guest,room101))),"test",today).groups().get(0).id();
        var state=groupOperations.configureRouting(company,property.getId(),groupId,new PmsGroupOperationsDto.Routing(java.util.Set.of(FolioItemType.ROOM)));
        Reservation member=reservationRepository.findAllByGroupBooking_IdOrderByGuest_LastNameAsc(groupId).get(0);
        member.setStatus(ReservationStatus.CHECKED_IN);
        operationsService.checkOut(company,member.getId(),"test",today);
        assertThat(member.getStatus()).isEqualTo(ReservationStatus.CHECKED_OUT);
        assertThat(folioRepository.findById(state.masterFolioId()).orElseThrow().getStatus()).isEqualTo(FolioStatus.OPEN);
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(state.masterFolioId())).hasSize(2);
    }

    @Test
    void changingAndCancellingOneMemberDoesNotMutateOtherMembersMasterCharges() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of(rooming(guest,room101),rooming(secondGuest,room102))),"test",today).groups().get(0).id();
        var state=groupOperations.configureRouting(company,property.getId(),groupId,new PmsGroupOperationsDto.Routing(java.util.Set.of(FolioItemType.ROOM)));
        Reservation first=reservationRepository.findAllByGroupBooking_IdOrderByGuest_LastNameAsc(groupId).stream()
                .filter(r->r.getGuest().getId().equals(guest.getId())).findFirst().orElseThrow();
        operationsService.updateReservation(company,first.getId(),new UpsertReservationRequest(property.getId(),guest.getId(),roomType.getId(),room101.getId(),ratePlan.getId(),
                today.plusDays(1),today.plusDays(4),1,0,ReservationStatus.CONFIRMED,ReservationSource.DIRECT,null),"test",today);
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(state.masterFolioId())).hasSize(5);
        operationsService.cancelReservation(company,first.getId(),null,"test",today);
        var remaining=folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(state.masterFolioId());
        assertThat(remaining).hasSize(2).allSatisfy(i->assertThat(i.getSourceReservation().getGuest().getId()).isEqualTo(secondGuest.getId()));
    }

    @Test
    void groupRoutingDoesNotMoveAlreadyInvoicedSourceLines() {
        var created=service.createGroupBooking(company,groupRequest(List.of(rooming(guest,room101))),"test",today);
        Long groupId=created.groups().get(0).id();
        Long reservationId=created.groups().get(0).rooms().get(0).reservationId();
        Folio memberFolio=folioRepository.findFirstByReservation_IdOrderByIdAsc(reservationId).orElseThrow();
        service.createInvoice(company,property.getId(),invoiceRequest(memberFolio.getId()),today);
        var state=groupOperations.configureRouting(company,property.getId(),groupId,new PmsGroupOperationsDto.Routing(java.util.Set.of(FolioItemType.ROOM)));
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(state.masterFolioId())).isEmpty();
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(memberFolio.getId())).hasSize(2);
    }

    @Test
    void groupOperationsDoNotExposeAnotherCompany() {
        Long groupId=service.createGroupBooking(company,groupRequest(List.of()),"test",today).groups().get(0).id();
        Company outsider=companyRepository.save(new Company("Outside Group Co"));
        assertThatThrownBy(()->groupOperations.view(outsider,property.getId(),groupId)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void createsSplitFolioAndMovesOnlySelectedCharges() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        PmsOperationsResponse.FolioView mainFolio = created.folios().get(0);
        Long firstItemId = mainFolio.items().get(0).id();

        PmsOperationsResponse split = service.createSplitFolio(
                company, property.getId(),
                new CreateSplitFolioRequest(reservationId, "Firmenkonto", null), today);
        PmsOperationsResponse.FolioView target = split.folios().stream()
                .filter(folio -> "Firmenkonto".equals(folio.label())).findFirst().orElseThrow();

        PmsOperationsResponse moved = service.moveFolioItems(
                company, property.getId(), mainFolio.id(),
                new MoveFolioItemsRequest(target.id(), List.of(firstItemId)), today);

        assertThat(moved.folios()).hasSize(2);
        assertThat(folioItemRepository.findById(firstItemId).orElseThrow().getFolio().getId())
                .isEqualTo(target.id());
    }

    @Test
    void snapshotsInvoiceWithSwissVatAndRendersPdf() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long folioId = created.folios().get(0).id();

        PmsAdvancedResponse response = service.createInvoice(
                company, property.getId(),
                new CreateInvoiceRequest(
                        folioId, today.plusDays(10), new BigDecimal("8.10"),
                        "Gabriela Tschopp", "Seestrasse 2", "8002", "Zürich", "CH",
                        "CH9300762011623852957", null
                ),
                today
        );

        PmsAdvancedResponse.InvoiceView invoice = response.invoices().get(0);
        assertThat(invoice.grossAmount()).isEqualByComparingTo("216.20");
        assertThat(invoice.netAmount()).isEqualByComparingTo("200.00");
        assertThat(invoice.vatAmount()).isEqualByComparingTo("16.20");
        assertThat(invoice.hasQrPaymentPart()).isTrue();
        assertThat(service.generateInvoicePdf(company, invoice.id()))
                .startsWith("%PDF".getBytes());
        String[] qrFields = service.swissQrPayload(invoiceRepository.findById(invoice.id()).orElseThrow())
                .split("\\n", -1);
        assertThat(qrFields).hasSize(34);
        assertThat(qrFields[4]).isEqualTo("S");
        assertThat(qrFields[6]).isEqualTo("Musterstrasse");
        assertThat(qrFields[7]).isEqualTo("1");
        assertThat(qrFields[20]).isEqualTo("S");
        assertThat(qrFields[22]).isEqualTo("Seestrasse");
        assertThat(qrFields[23]).isEqualTo("2");
    }

    @Test
    void preventsDuplicateInvoicesAndOnlyBillsNewSourceLinesOnSupplement() {
        Long folioId = operationsService.createReservation(company, reservationRequest(), "Christopher", today).folios().get(0).id();
        CreateInvoiceRequest request = invoiceRequest(folioId);
        Long firstId = service.createInvoice(company, property.getId(), request, today).invoices().get(0).id();
        assertThat(invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(firstId)).allSatisfy(line -> {
            assertThat(line.getSourceItem()).isNotNull();
            assertThat(line.getActiveSourceItemId()).isEqualTo(line.getSourceItem().getId());
        });
        assertThatThrownBy(() -> service.createInvoice(company, property.getId(), request, today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("keine noch nicht fakturierten");
        assertThat(invoiceRepository.countByProperty_Id(property.getId())).isEqualTo(1);
        operationsService.postFolioItem(company, property.getId(), folioId,
                new PostFolioItemRequest(today, FolioItemType.SERVICE, "Garage", BigDecimal.ONE, new BigDecimal("15.00")), today);
        var supplement = service.createInvoice(company, property.getId(), request, today).invoices().get(0);
        assertThat(supplement.grossAmount()).isEqualByComparingTo("15.00");
        assertThat(invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(supplement.id())).hasSize(1);
    }

    @Test
    void documentCorrectionReleasesBillingAllocationWithoutChangingTheOperationalBalance() {
        Long folioId = operationsService.createReservation(company, reservationRequest(), "Christopher", today).folios().get(0).id();
        Long originalId = service.createInvoice(company, property.getId(), invoiceRequest(folioId), today).invoices().get(0).id();
        service.correctInvoice(company, property.getId(), originalId, new CorrectInvoiceRequest("Empfänger korrigieren", "REISSUE"), "Christopher", today);
        assertThat(invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(originalId)).allSatisfy(line -> assertThat(line.getActiveSourceItemId()).isNull());
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId)).hasSize(2);
        var replacement = service.createInvoice(company, property.getId(), invoiceRequest(folioId), today).invoices().get(0);
        assertThat(replacement.grossAmount()).isEqualByComparingTo("216.20");
    }

    @Test
    void cancellationCreditPostsNegativeTaxSnapshotsEvenAfterCheckoutAndCannotBeBilledAgain() {
        Long folioId = operationsService.createReservation(company, reservationRequest(), "Christopher", today).folios().get(0).id();
        Long originalId = service.createInvoice(company, property.getId(), invoiceRequest(folioId), today).invoices().get(0).id();
        Folio folio = folioRepository.findById(folioId).orElseThrow();
        folio.setStatus(FolioStatus.CLOSED);
        folioRepository.save(folio);
        service.correctInvoice(company, property.getId(), originalId, new CorrectInvoiceRequest("Reklamation nach Abreise", "CANCEL_SERVICES"), "Christopher", today);
        List<FolioItem> items = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId);
        assertThat(items).hasSize(4);
        assertThat(items.stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0.00");
        assertThat(items.stream().filter(i -> i.getTotalAmount().signum() < 0)).allSatisfy(item -> {
            assertThat(item.getServiceDate()).isEqualTo(today);
            assertThat(item.getTaxRate()).isEqualByComparingTo("8.10");
            assertThat(invoiceLineRepository.existsByActiveSourceItemId(item.getId())).isTrue();
        });
        assertThatThrownBy(() -> service.createInvoice(company, property.getId(), invoiceRequest(folioId), today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("keine noch nicht fakturierten");
    }

    @Test
    void refusesLegacyInvoiceWithoutTraceableSourceAllocation() {
        Long folioId = operationsService.createReservation(company, reservationRequest(), "Christopher", today).folios().get(0).id();
        Long invoiceId = service.createInvoice(company, property.getId(), invoiceRequest(folioId), today).invoices().get(0).id();
        for (PmsInvoiceLine line : invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(invoiceId)) {
            line.setSourceItem(null); line.setActiveSourceItemId(null); invoiceLineRepository.save(line);
        }
        entityManager.flush();
        assertThatThrownBy(() -> service.createInvoice(company, property.getId(), invoiceRequest(folioId), today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Altrechnung");
    }

    @Test
    void refusesMovingAnAlreadyInvoicedSourceLine() {
        var created = operationsService.createReservation(company, reservationRequest(), "Christopher", today);
        var main = created.folios().get(0);
        service.createInvoice(company, property.getId(), invoiceRequest(main.id()), today);
        var split = service.createSplitFolio(company, property.getId(), new CreateSplitFolioRequest(main.reservationId(), "Zusatzkonto", null), today);
        Long target = split.folios().stream().filter(f -> !f.id().equals(main.id())).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> service.moveFolioItems(company, property.getId(), main.id(), new MoveFolioItemsRequest(target, List.of(main.items().get(0).id())), today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("fakturierte Positionen");
    }

    private CreateInvoiceRequest invoiceRequest(Long folioId) {
        return new CreateInvoiceRequest(folioId, today.plusDays(10), new BigDecimal("8.10"), "Gabriela Tschopp", "Seestrasse 2", "8002", "Zürich", "CH", null, null);
    }

    @Test
    void organizationDirectoryPagesBeyondCompositeLimitAndKeepsTenantBoundary() {
        for(int n=0;n<70;n++) {
            PmsOrganization organization=new PmsOrganization();organization.setCompany(company);organization.setType(OrganizationType.COMPANY);
            organization.setName("Paged company "+String.format("%03d",n));organization.setReferenceCode("PAGE-"+n);organizationRepository.save(organization);
        }
        Company other=companyRepository.save(new Company("Other directory tenant"));
        PmsOrganization foreign=new PmsOrganization();foreign.setCompany(other);foreign.setType(OrganizationType.COMPANY);foreign.setName("Paged company 060 foreign");organizationRepository.saveAndFlush(foreign);
        assertThat(service.getAdvanced(company,property.getId(),today).organizations()).hasSize(50);
        var second=organizationRepository.searchDirectory(company.getId(),"",false,false,org.springframework.data.domain.PageRequest.of(1,25));
        assertThat(second.getContent()).hasSize(25);assertThat(second.getTotalElements()).isEqualTo(70);assertThat(second.hasNext()).isTrue();
        var found=organizationRepository.searchDirectory(company.getId(),"%paged company 060%",false,false,org.springframework.data.domain.PageRequest.of(0,25));
        assertThat(found.getContent()).singleElement().satisfies(entry->assertThat(entry.getName()).isEqualTo("Paged company 060"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"DE,Rechnung,Gutschrift","EN,Invoice,Credit note","FR,Facture,Avoir","IT,Fattura,Nota di credito","ES,Factura,Nota de crédito"})
    void documentLanguageAndBytesRemainFixedAcrossProfileChangesAndCredit(String language,String invoiceLabel,String creditLabel) throws Exception {
        var created=operationsService.createReservation(company,reservationRequest(),"desk",today);
        Long folioId=created.folios().get(0).id();
        var request=new CreateInvoiceRequest(folioId,today.plusDays(10),new BigDecimal("8.1"),"Gabriela Tschopp","Seestrasse 2","8002","Zürich","CH",null,null,false,null,language);
        Long invoiceId=service.createInvoice(company,property.getId(),request,today).invoices().get(0).id();
        byte[] original=service.generateInvoicePdf(company,invoiceId);
        var reader=new com.itextpdf.text.pdf.PdfReader(original);
        assertThat(com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader,1)).contains(invoiceLabel);reader.close();
        guest.setLanguageCode("en");property.setLegalName("Changed hotel name");propertyRepository.saveAndFlush(property);
        assertThat(service.generateInvoicePdf(company,invoiceId)).isEqualTo(original);
        var corrected=service.correctInvoice(company,property.getId(),invoiceId,new CorrectInvoiceRequest("Document correction","REISSUE"),"desk",today);
        Long creditId=corrected.invoices().stream().filter(i->i.type()==InvoiceType.CREDIT_NOTE).findFirst().orElseThrow().id();
        assertThat(invoiceRepository.findById(creditId).orElseThrow().getLanguageCode()).isEqualTo(language);
        var creditReader=new com.itextpdf.text.pdf.PdfReader(service.generateInvoicePdf(company,creditId));
        assertThat(com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(creditReader,1)).contains(creditLabel);creditReader.close();
        assertThat(service.generateInvoicePdf(company,invoiceId)).isEqualTo(original);
    }

    @Test
    void chosenLegacyTaxIsPersistedOnSourceForCanonicalInvoiceAndExport() {
        var created=operationsService.createReservation(company,reservationRequest(),"desk",today);
        Long folioId=created.folios().get(0).id();
        var charges=folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId);
        charges.forEach(i->i.setTaxRate(null));
        service.createInvoice(company,property.getId(),invoiceRequest(folioId),today);
        assertThat(charges).allSatisfy(i->assertThat(i.getTaxRate()).isEqualByComparingTo("8.1"));
        assertThat(invoiceLineRepository.findAll()).allSatisfy(l->assertThat(l.getVatRate()).isEqualByComparingTo(l.getSourceItem().getTaxRate()));
    }

    @Test
    void paymentVoidCannotReopenDebtOnCheckedOutAccount() {
        var created=operationsService.createReservation(company,reservationRequest(),"desk",today);
        Long folioId=created.folios().get(0).id();
        var paid=operationsService.postPayment(company,property.getId(),folioId,new PostPaymentRequest(new BigDecimal("216.20"),PaymentMethod.BANK_TRANSFER,"BANK-VOID"),"desk",today);
        Long paymentId=paid.folios().get(0).paymentEntries().get(0).id();
        Reservation reservation=reservationRepository.findById(created.reservations().get(0).id()).orElseThrow();reservation.setStatus(ReservationStatus.CHECKED_IN);
        operationsService.checkOut(company,reservation.getId(),"desk",today);
        assertThatThrownBy(()->operationsService.voidPayment(company,property.getId(),paymentId,new VoidPaymentRequest("Eingabefehler"),"desk",today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("geschlossenen Gastkonto");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.POSTED);
        assertThat(folioRepository.findById(folioId).orElseThrow().getStatus()).isEqualTo(FolioStatus.CLOSED);
    }

    @Test
    void checkoutWaitsForPendingRefundEvenWhenPostedBalanceIsZero() {
        var created = operationsService.createReservation(company, reservationRequest(), "desk", today);
        Folio folio = folioRepository.findById(created.folios().get(0).id()).orElseThrow();
        Reservation reservation = folio.getReservation();
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        Payment original = new Payment();
        original.setFolio(folio); original.setAmount(new BigDecimal("216.20"));
        original.setMethod(PaymentMethod.CARD); original.setStatus(PaymentStatus.POSTED);
        original.setCreatedBy("provider-webhook"); original.setProviderTransactionId("pi-checkout-pending");
        original = paymentRepository.save(original);
        Payment refund = new Payment();
        refund.setFolio(folio); refund.setOriginalPayment(original); refund.setAmount(new BigDecimal("-100.00"));
        refund.setMethod(PaymentMethod.CARD); refund.setKind(PaymentKind.REFUND); refund.setStatus(PaymentStatus.PENDING);
        refund.setCreatedBy("desk"); refund.setRefundRequestId("checkout-pending-refund");
        paymentRepository.saveAndFlush(refund);

        assertThatThrownBy(() -> operationsService.checkOut(company, reservation.getId(), "desk", today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("offene Rückerstattungen");
        assertThat(folio.getStatus()).isEqualTo(FolioStatus.OPEN);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        refund.setStatus(PaymentStatus.FAILED);
        operationsService.checkOut(company, reservation.getId(), "desk", today);
        assertThat(folio.getStatus()).isEqualTo(FolioStatus.CLOSED);
    }

    @Test
    void checkoutRefreshesStatusChangedAfterReservationWasLoaded() {
        var created = operationsService.createReservation(company, reservationRequest(), "desk", today);
        Reservation stale = reservationRepository.findById(created.reservations().get(0).id()).orElseThrow();
        stale.setStatus(ReservationStatus.CHECKED_IN);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE pms_reservations SET status='CHECKED_OUT' WHERE id=:id")
                .setParameter("id", stale.getId()).executeUpdate();
        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);

        assertThatThrownBy(() -> operationsService.checkOut(company, stale.getId(), "desk", today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Nur eingecheckte Aufenthalte");
        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.CHECKED_OUT);
    }

    @Test
    void maximumLengthInvoiceDescriptionRemainsCorrectable() {
        Long folioId=operationsService.createReservation(company,reservationRequest(),"desk",today).folios().get(0).id();
        folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId).forEach(i->i.setDescription("x".repeat(240)));
        Long invoiceId=service.createInvoice(company,property.getId(),invoiceRequest(folioId),today).invoices().get(0).id();
        service.correctInvoice(company,property.getId(),invoiceId,new CorrectInvoiceRequest("Korrektur","CANCEL_SERVICES"),"desk",today);
        assertThat(invoiceLineRepository.findAll()).allSatisfy(line->assertThat(line.getDescription()).hasSizeLessThanOrEqualTo(240));
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId)).hasSize(4);
    }

    @Test
    void invoiceAndCorrectionUsePersistedBusinessDayInsteadOfArbitraryPickerDate() {
        Long folioId=operationsService.createReservation(company,reservationRequest(),"desk",today).folios().get(0).id();
        assertThatThrownBy(()->service.createInvoice(company,property.getId(),invoiceRequest(folioId),today.plusDays(2)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("offenen Betriebstag");
        assertThat(invoiceRepository.findAll()).isEmpty();
        Long invoiceId=service.createInvoice(company,property.getId(),invoiceRequest(folioId),null).invoices().get(0).id();
        assertThat(invoiceRepository.findById(invoiceId).orElseThrow().getServiceFrom()).isEqualTo(today.plusDays(1));
        assertThatThrownBy(()->service.correctInvoice(company,property.getId(),invoiceId,new CorrectInvoiceRequest("Datum"),"desk",today.plusDays(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("offenen Betriebstag");
        service.closeNightAudit(company,property.getId(),new CloseNightAuditRequest(today,false),"desk");
        var corrected=service.correctInvoice(company,property.getId(),invoiceId,new CorrectInvoiceRequest("Empfänger berichtigen"),"desk",null);
        assertThat(corrected.invoices().stream().filter(i->i.type()==InvoiceType.CREDIT_NOTE)).singleElement()
                .satisfies(i->assertThat(i.issueDate()).isEqualTo(today.plusDays(1)));
    }

    @Test
    void kuwaitiInvoiceAndCorporateReceivablePreserveThreeDecimalPayments() {
        property.setCurrencyCode("KWD");ratePlan.setCurrencyCode("KWD");ratePlan.setNightlyRate(new BigDecimal("1.123"));ratePlan.setVatRate(BigDecimal.ZERO);
        PmsInvoice invoice=companyInvoice();
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo("2.246");
        Long organization=invoice.getFolio().getOrganization().getId();
        receivablesService.configure(company,property.getId(),organization,new PmsReceivablesDto.CreditSettings(true,new BigDecimal("10.001"),30),"master");
        var debtor=receivablesService.directBill(company,property.getId(),invoice.getId(),new PmsReceivablesDto.DirectBill(organization),"desk");
        var paid=receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("2.245"),"bank","KWD-1"),"desk",false);
        assertThat(paid.balance()).isEqualByComparingTo("0.001");
        var finalPayment=receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("0.001"),"bank","KWD-2"),"desk",false);
        assertThat(finalPayment.balance()).isZero();
    }

    @Test
    void japaneseInvoiceTaxRoundsToWholeYen() {
        property.setCurrencyCode("JPY");ratePlan.setCurrencyCode("JPY");ratePlan.setNightlyRate(new BigDecimal("101"));ratePlan.setVatRate(new BigDecimal("10"));
        var created=operationsService.createReservation(company,reservationRequest(),"desk",today);
        service.createInvoice(company,property.getId(),invoiceRequest(created.folios().get(0).id()),today);
        PmsInvoice invoice=invoiceRepository.findAll().get(0);
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo("202");
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("184");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("18");
    }

    @Test
    void eventOrderPostsItemizedTaxExactlyOnceAndLocksItsSnapshot() {
        ResourceBooking booking=eventBooking();
        var draft=eventOrders.save(company,property.getId(),booking.getId(),eventOrder(30));
        assertThat(draft.netAmount()).isEqualByComparingTo("200");assertThat(draft.taxAmount()).isEqualByComparingTo("38");
        assertThat(draft.occupiedUntil()).isEqualTo(booking.getEndAt().plusMinutes(30));
        Long folioId=operationsService.createReservation(company,reservationRequest(),"desk",today).folios().get(0).id();
        var posted=eventOrders.post(company,property.getId(),booking.getId(),new PmsEventOrderDto.Post(folioId),"desk");
        assertThat(posted.status()).isEqualTo("POSTED");
        eventOrders.post(company,property.getId(),booking.getId(),new PmsEventOrderDto.Post(folioId),"desk");
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId)).hasSize(3)
                .anySatisfy(i->{assertThat(i.getTotalAmount()).isEqualByComparingTo("238");assertThat(i.getTaxRate()).isEqualByComparingTo("19");});
        assertThat(eventOrders.beoPdf(company,property.getId(),booking.getId())).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(()->eventOrders.save(company,property.getId(),booking.getId(),eventOrder(0))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("unveränderlich");
    }

    @Test
    void eventSetupBufferBlocksLegacyResourceBooking() {
        ResourceBooking booking=eventBooking();
        eventOrders.save(company,property.getId(),booking.getId(),eventOrder(30));
        assertThatThrownBy(()->service.createResourceBooking(company,property.getId(),new CreateResourceBookingRequest(booking.getResource().getId(),null,"Following","Organizer",
                booking.getEndAt().plusMinutes(15),booking.getEndAt().plusHours(1),2,ResourceBookingStatus.CONFIRMED,BigDecimal.ZERO,null),"desk",today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("bereits gebucht");
    }

    @Test
    void eventOrderCannotExpandBufferIntoExistingBookingOrCrossCompany() {
        ResourceBooking booking=eventBooking();
        service.createResourceBooking(company,property.getId(),new CreateResourceBookingRequest(booking.getResource().getId(),null,"Following","Organizer",
                booking.getEndAt().plusMinutes(15),booking.getEndAt().plusHours(1),2,ResourceBookingStatus.CONFIRMED,BigDecimal.ZERO,null),"desk",today);
        assertThatThrownBy(()->eventOrders.save(company,property.getId(),booking.getId(),eventOrder(30))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("überschneiden");
        Company outside=companyRepository.save(new Company("Outside Events"));
        assertThatThrownBy(()->eventOrders.get(outside,property.getId(),booking.getId())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    private ResourceBooking eventBooking() {
        HotelResource resource=new HotelResource();resource.setProperty(property);resource.setCode("BANQUET");resource.setName("Saal");resource.setCapacity(100);
        resource.setType(HotelResourceType.CONFERENCE_ROOM);resource.setCurrencyCode(property.getCurrencyCode());resource.setHourlyRate(BigDecimal.ZERO);resourceRepository.save(resource);
        ResourceBooking booking=new ResourceBooking();booking.setProperty(property);booking.setResource(resource);booking.setTitle("Abendessen");booking.setOrganizerName("Event Co");
        booking.setStartAt(today.atTime(17,0));booking.setEndAt(today.atTime(19,0));booking.setCreatedBy("desk");return resourceBookingRepository.save(booking);
    }
    private PmsEventOrderDto.Save eventOrder(int teardown) {
        return new PmsEventOrderDto.Save(15,teardown,"17:00 Begrüßung","Tischform U","Laktosefrei",List.of(new PmsEventOrderDto.Line("Menü",new BigDecimal("2"),new BigDecimal("100"),new BigDecimal("19"),FolioItemType.SERVICE)));
    }

    @Test
    void approvedCorporateCreditPermitsCheckoutWithoutPretendingBankMoneyWasReceived() {
        PmsInvoice invoice=companyInvoice();
        Long organizationId=invoice.getFolio().getOrganization().getId();
        receivablesService.configure(company,property.getId(),organizationId,new PmsReceivablesDto.CreditSettings(true,new BigDecimal("500.00"),30),"Master");
        var debtor=receivablesService.directBill(company,property.getId(),invoice.getId(),new PmsReceivablesDto.DirectBill(organizationId),"Reception");
        assertThat(debtor.balance()).isEqualByComparingTo("216.20");
        assertThat(debtor.dueDate()).isEqualTo(today.plusDays(30));
        assertThat(paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(invoice.getFolio().getId())).singleElement().satisfies(payment -> {
            assertThat(payment.getMethod()).isEqualTo(PaymentMethod.DIRECT_BILL);
            assertThat(payment.getPostingDate()).isEqualTo(today);
        });
        Reservation reservation=invoice.getFolio().getReservation(); reservation.setStatus(ReservationStatus.CHECKED_IN); reservationRepository.save(reservation);
        operationsService.checkOut(company,reservation.getId(),"Reception",today);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_OUT);
        assertThat(receivableRepository.findById(debtor.id()).orElseThrow().balance()).isEqualByComparingTo("216.20");
    }

    @Test
    void directBillRequiresApprovalAndCreditLimitAndDoesNotDuplicateTransfer() {
        PmsInvoice invoice=companyInvoice(); Long organizationId=invoice.getFolio().getOrganization().getId();
        var request=new PmsReceivablesDto.DirectBill(organizationId);
        assertThatThrownBy(() -> receivablesService.directBill(company,property.getId(),invoice.getId(),request,"Reception"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("kein freigegebener Hotelkredit");
        receivablesService.configure(company,property.getId(),organizationId,new PmsReceivablesDto.CreditSettings(true,new BigDecimal("100.00"),10),"Master");
        assertThatThrownBy(() -> receivablesService.directBill(company,property.getId(),invoice.getId(),request,"Reception"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("kreditlimit");
        receivablesService.configure(company,property.getId(),organizationId,new PmsReceivablesDto.CreditSettings(true,new BigDecimal("1000.00"),10),"Master");
        Long id=receivablesService.directBill(company,property.getId(),invoice.getId(),request,"Reception").id();
        assertThat(receivablesService.directBill(company,property.getId(),invoice.getId(),request,"Reception").id()).isEqualTo(id);
        assertThat(paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(invoice.getFolio().getId())).hasSize(1);
    }

    @Test
    void corporateSettlementsArePartialIdempotentAndMarkTheInvoicePaid() {
        PmsInvoice invoice=companyInvoice(); var debtor=transferCompanyInvoice(invoice);
        var part=new PmsReceivablesDto.Settlement(new BigDecimal("50.00"),"BANK-001","settlement-001");
        assertThat(receivablesService.settle(company,property.getId(),debtor.id(),part,"Accounts",false).balance()).isEqualByComparingTo("166.20");
        receivablesService.settle(company,property.getId(),debtor.id(),part,"Accounts",false);
        assertThat(settlementRepository.findAllByReceivable_IdOrderByIdAsc(debtor.id())).hasSize(1);
        assertThatThrownBy(() -> receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(BigDecimal.TEN,"BANK-001","settlement-001"),"Accounts",false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("andere Zahlungsdaten");
        receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("166.20"),"BANK-002","settlement-002"),"Accounts",false);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(receivablesService.list(company,property.getId(),null,true)).isEmpty();
    }

    @Test
    void cancellingPartPaidCorporateInvoiceCreatesRefundableCompanyCreditWithoutGuestRefundDuplication() {
        PmsInvoice invoice=companyInvoice(); var debtor=transferCompanyInvoice(invoice);
        receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("50.00"),"BANK-001","payment-001"),"Accounts",false);
        service.correctInvoice(company,property.getId(),invoice.getId(),new CorrectInvoiceRequest("Leistungen entfallen","CANCEL_SERVICES"),"Accounts",today);
        assertThat(receivableRepository.findById(debtor.id()).orElseThrow().balance()).isEqualByComparingTo("-50.00");
        BigDecimal charges=folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(invoice.getFolio().getId()).stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal payments=paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(invoice.getFolio().getId()).stream().map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertThat(charges.subtract(payments)).isEqualByComparingTo("0.00");
        receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("50.00"),"BANK-REFUND-001","refund-001"),"Accounts",true);
        assertThat(receivableRepository.findById(debtor.id()).orElseThrow().balance()).isEqualByComparingTo("0.00");
        assertThat(settlementRepository.findAllByReceivable_IdOrderByIdAsc(debtor.id())).extracting(PmsReceivableSettlement::getAmount).containsExactly(new BigDecimal("50.00"),new BigDecimal("-50.00"));
    }

    @Test
    void corporateDocumentReissuePreservesExistingReceivableAndPriorPaymentHistory() {
        PmsInvoice invoice=companyInvoice(); var debtor=transferCompanyInvoice(invoice);
        receivablesService.settle(company,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(new BigDecimal("50.00"),"BANK-001","payment-001"),"Accounts",false);
        service.correctInvoice(company,property.getId(),invoice.getId(),new CorrectInvoiceRequest("Adresse","REISSUE"),"Accounts",today);
        assertThat(receivablesService.list(company,property.getId(),null,false).get(0).status()).isEqualTo("REISSUE_PENDING");
        Long replacementId=service.createInvoice(company,property.getId(),invoiceRequest(invoice.getFolio().getId()),today).invoices().get(0).id();
        var retained=receivableRepository.findById(debtor.id()).orElseThrow();
        assertThat(retained.getInvoice().getId()).isEqualTo(replacementId);
        assertThat(retained.balance()).isEqualByComparingTo("166.20");
        assertThat(settlementRepository.findAllByReceivable_IdOrderByIdAsc(debtor.id())).hasSize(1);
        assertThat(paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(invoice.getFolio().getId())).hasSize(1);
    }

    @Test
    void corporateReceivablesCannotBeReadOrChangedThroughAnotherTenant() {
        PmsInvoice invoice=companyInvoice(); var debtor=transferCompanyInvoice(invoice);
        Company other=companyRepository.save(new Company("Other tenant"));
        assertThatThrownBy(() -> receivablesService.list(other,property.getId(),null,false)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> receivablesService.settle(other,property.getId(),debtor.id(),new PmsReceivablesDto.Settlement(BigDecimal.TEN,"foreign","foreign"),"Other",false)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    private PmsInvoice companyInvoice() {
        PmsOrganization organization=new PmsOrganization(); organization.setCompany(company); organization.setName("Firmenkunde AG"); organization.setType(OrganizationType.COMPANY);
        organization=organizationRepository.save(organization); guest.setOrganization(organization); guestRepository.save(guest);
        var request=new UpsertReservationRequest(property.getId(),guest.getId(),roomType.getId(),room101.getId(),ratePlan.getId(),today,today.plusDays(2),1,0,ReservationStatus.CONFIRMED,ReservationSource.DIRECT,null);
        Long folioId=operationsService.createReservation(company,request,"Reception",today).folios().get(0).id();
        Long invoiceId=service.createInvoice(company,property.getId(),invoiceRequest(folioId),today).invoices().get(0).id();
        return invoiceRepository.findById(invoiceId).orElseThrow();
    }
    private PmsReceivablesDto.ReceivableView transferCompanyInvoice(PmsInvoice invoice) {
        Long organizationId=invoice.getFolio().getOrganization().getId();
        receivablesService.configure(company,property.getId(),organizationId,new PmsReceivablesDto.CreditSettings(true,new BigDecimal("1000.00"),30),"Master");
        return receivablesService.directBill(company,property.getId(),invoice.getId(),new PmsReceivablesDto.DirectBill(organizationId),"Reception");
    }

    @Test
    void correctsIssuedInvoiceWithImmutableNegativeCreditNote() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        PmsAdvancedResponse issued = service.createInvoice(
                company, property.getId(),
                new CreateInvoiceRequest(
                        created.folios().get(0).id(), today.plusDays(10), new BigDecimal("8.10"),
                        "Gabriela Tschopp", "Seestrasse 2", "8002", "Zürich", "CH",
                        null, null
                ),
                today
        );
        Long invoiceId = issued.invoices().get(0).id();

        PmsAdvancedResponse corrected = service.correctInvoice(
                company,
                property.getId(),
                invoiceId,
                new CorrectInvoiceRequest("Adresse war falsch"),
                "Christopher",
                today
        );

        assertThat(corrected.invoices()).hasSize(2);
        PmsAdvancedResponse.InvoiceView credit = corrected.invoices().stream()
                .filter(invoice -> invoice.type() == InvoiceType.CREDIT_NOTE)
                .findFirst()
                .orElseThrow();
        PmsAdvancedResponse.InvoiceView original = corrected.invoices().stream()
                .filter(invoice -> invoice.id().equals(invoiceId))
                .findFirst()
                .orElseThrow();
        assertThat(credit.grossAmount()).isEqualByComparingTo("-216.20");
        assertThat(credit.originalInvoiceId()).isEqualTo(invoiceId);
        assertThat(credit.correctionReason()).isEqualTo("Adresse war falsch");
        assertThat(original.status()).isEqualTo(InvoiceStatus.CREDITED);
        assertThat(service.generateInvoicePdf(company, credit.id())).startsWith("%PDF".getBytes());
        assertThatThrownBy(() -> service.correctInvoice(
                company, property.getId(), invoiceId,
                new CorrectInvoiceRequest("Nochmals"), "Christopher", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("noch nicht korrigierte");
    }

    @Test
    void closesNightAuditOnlyOnceAndCanMarkNoShows() {
        operationsService.createReservation(company, new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), room101.getId(), ratePlan.getId(),
                today, today.plusDays(2), 1, 0, ReservationStatus.CONFIRMED,
                ReservationSource.DIRECT, null), "Christopher", today);

        PmsAdvancedResponse response = service.closeNightAudit(
                company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher");

        assertThat(response.nightAudits()).singleElement()
                .extracting(PmsAdvancedResponse.NightAuditView::noShowCount).isEqualTo(1L);
        assertThat(reservationRepository.findAll().get(0).getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThatThrownBy(() -> service.closeNightAudit(
                company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bereits abgeschlossen");
        assertThat(nightAuditRepository.findAll()).hasSize(1);
        assertThat(service.financialDay(company, property.getId()).businessDate()).isEqualTo(today.plusDays(1));
        assertThat(financialPeriodRepository.findById(property.getId()).orElseThrow().getLastClosedDate()).isEqualTo(today);
    }

    @Test
    void nightAuditRequiresClosedCashShiftsAndResolvedDepartures() {
        CashShift shift = new CashShift(); shift.setProperty(property); shift.setOpenedBy("Cashier"); shift.setStatus(CashShiftStatus.OPEN);
        cashShiftRepository.save(shift);
        assertThatThrownBy(() -> service.closeNightAudit(company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Kassenschichten");
        shift.setStatus(CashShiftStatus.CLOSED); shift.setClosedAt(LocalDateTime.now()); cashShiftRepository.save(shift);
        var created = operationsService.createReservation(company, reservationRequest(), "Christopher", today);
        Reservation due = reservationRepository.findById(created.reservations().get(0).id()).orElseThrow();
        due.setStatus(ReservationStatus.CHECKED_IN); due.setArrivalDate(today.minusDays(2)); due.setDepartureDate(today); reservationRepository.save(due);
        assertThatThrownBy(() -> service.closeNightAudit(company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("fällige Abreisen");
    }

    @Test
    void closedBusinessDateRejectsNewChargesWhileNextBusinessDateStaysWritable() {
        Long folioId = operationsService.createReservation(company, reservationRequest(), "Christopher", today).folios().get(0).id();
        service.closeNightAudit(company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher");
        assertThatThrownBy(() -> operationsService.postFolioItem(company, property.getId(), folioId,
                new PostFolioItemRequest(today, FolioItemType.SERVICE, "Nachtrag", BigDecimal.ONE, BigDecimal.TEN), today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("abgeschlossen");
        operationsService.postFolioItem(company, property.getId(), folioId,
                new PostFolioItemRequest(today.plusDays(1), FolioItemType.SERVICE, "Nachtrag offener Tag", BigDecimal.ONE, BigDecimal.TEN), today.plusDays(1));
        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId)).anySatisfy(item -> assertThat(item.getDescription()).isEqualTo("Nachtrag offener Tag"));
    }

    @Test
    void queuesRenderedGuestCommunicationWithoutPretendingItWasSent() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        PmsAdvancedResponse templates = service.createTemplate(
                company, property.getId(),
                new UpsertCommunicationTemplateRequest(
                        "PRE_ARRIVAL", "Vor Anreise", "Willkommen {{guestName}}",
                        "{{hotelName}} erwartet Sie am {{arrivalDate}}. Code: {{confirmationCode}}",
                        "de", true
                ), today);

        PmsAdvancedResponse response = service.queueCommunication(
                company, property.getId(),
                new QueueCommunicationRequest(
                        guest.getId(), reservationId, templates.communicationTemplates().get(0).id(), null),
                today
        );

        assertThat(response.communications()).singleElement().satisfies(communication -> {
            assertThat(communication.status()).isEqualTo(CommunicationStatus.QUEUED);
            assertThat(communication.subject()).isEqualTo("Willkommen Gabriela Tschopp");
            assertThat(communication.body()).contains("Chrono Zürich", "Code:");
            assertThat(communication.sentAt()).isNull();
        });
        assertThat(response.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("communication.queued");
    }

    @Test
    void recordsReadsAndRepliesToUnifiedInboxMessages() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        PmsAdvancedResponse received = service.recordInboundCommunication(
                company, property.getId(),
                new PostInboundCommunicationRequest(
                        guest.getId(), reservationId, CommunicationChannel.OTA,
                        "Gabriela Tschopp", "Anreise", "Können wir früher einchecken?",
                        "booking-thread-4711"
                ),
                today
        );

        PmsAdvancedResponse.GuestCommunicationView inbound = received.communications().get(0);
        assertThat(inbound.direction()).isEqualTo(CommunicationDirection.INBOUND);
        assertThat(inbound.status()).isEqualTo(CommunicationStatus.RECEIVED);
        assertThat(inbound.channel()).isEqualTo(CommunicationChannel.OTA);
        assertThat(inbound.readAt()).isNull();

        PmsAdvancedResponse read = service.markCommunicationRead(
                company, property.getId(), inbound.id(), today);
        assertThat(read.communications().get(0).readAt()).isNotNull();

        PmsAdvancedResponse replied = service.queueInboxReply(
                company, property.getId(),
                new QueueInboxReplyRequest(
                        guest.getId(), reservationId, CommunicationChannel.OTA,
                        "booking-thread-4711", "Re: Anreise",
                        "Ja, ab 13 Uhr ist das Zimmer bereit.", "booking-thread-4711"
                ),
                today
        );

        assertThat(replied.communications().get(0)).satisfies(reply -> {
            assertThat(reply.direction()).isEqualTo(CommunicationDirection.OUTBOUND);
            assertThat(reply.status()).isEqualTo(CommunicationStatus.QUEUED);
            assertThat(reply.channel()).isEqualTo(CommunicationChannel.OTA);
            assertThat(reply.externalThreadId()).isEqualTo("booking-thread-4711");
        });
        assertThat(replied.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("communication.received", "communication.reply_queued");
    }

    @Test
    void importsExternalBookingIdempotentlyAcrossProviderRetries() {
        ExternalBookingRequest request = new ExternalBookingRequest(
                "CHANNEL_TEST", "external-4711", reservationRequest());

        service.importExternalBooking(company, request, "Christopher", today);
        PmsOperationsResponse retried = service.importExternalBooking(company, request, "Christopher", today);

        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(retried.reservations()).hasSize(1);
        assertThat(service.getAdvanced(company, property.getId(), today).integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("booking.imported");
    }

    @Test
    void configuresProviderNeutralSandboxMappingAndPublishesInventorySnapshot() {
        PmsAdvancedResponse created = service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "booking-test", "Booking Test", ChannelEnvironment.SANDBOX, null,
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "DBL-EXT", "BAR-EXT"))
                ),
                today
        );
        Long connectionId = created.channelConnections().get(0).id();
        assertThat(created.channelConnections().get(0).mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.externalRoomCode()).isEqualTo("DBL-EXT");
            assertThat(mapping.externalRateCode()).isEqualTo("BAR-EXT");
        });

        PmsAdvancedResponse synced = service.syncChannelConnection(
                company, property.getId(), connectionId, today);
        assertThat(synced.channelConnections().get(0).lastSyncMessage()).contains("Testabgleich");
        assertThat(synced.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("channel.inventory_snapshot_ready");

        assertThatThrownBy(() -> service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-test", "Live Test", ChannelEnvironment.LIVE, null,
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D", "R"))
                ),
                today
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Server-Variable");

        assertThatThrownBy(() -> service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-raw-secret", "Live Raw Secret", ChannelEnvironment.LIVE, "do-not-store-this",
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D2", "R2"))
                ),
                today
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("env:NAME");

        PmsAdvancedResponse live = service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-gateway", "Live Gateway", ChannelEnvironment.LIVE, "env:CHANNEL_PROVIDER_SECRET",
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D3", "R3"))
                ),
                today
        );
        Long liveConnectionId = live.channelConnections().stream()
                .filter(connection -> connection.environment() == ChannelEnvironment.LIVE)
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> service.syncChannelConnection(
                company, property.getId(), liveConnectionId, today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sichere Anbieteranbindung");

        ReflectionTestUtils.setField(service, "providerGatewayEnabled", true);
        PmsAdvancedResponse liveSynced = service.syncChannelConnection(
                company, property.getId(), liveConnectionId, today);
        assertThat(liveSynced.channelConnections().stream()
                .filter(connection -> connection.id().equals(liveConnectionId))
                .findFirst().orElseThrow().lastSyncMessage())
                .contains("sicheren Übertragung");
    }

    @Test
    void completesGuestRegistrationWithoutPersistingDocumentNumberInPlaintext() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        PmsAdvancedResponse response = service.completeGuestRegistration(
                company,
                property.getId(),
                reservationId,
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", "ZH 12345", "Gabriela Tschopp", true,
                        null, null
                ),
                "Christopher",
                today
        );

        assertThat(response.guestRegistrations()).singleElement().satisfies(registration -> {
            assertThat(registration.status()).isEqualTo(GuestRegistrationStatus.COMPLETED);
            assertThat(registration.documentLastFour()).isEqualTo("6789");
            assertThat(registration.signatureName()).isEqualTo("Gabriela Tschopp");
        });
        GuestRegistration stored = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getDocumentHash()).hasSize(64).doesNotContain("X123456789");
    }

    @Test
    void issuesSingleUseDigitalCheckInLinkAndCompletesRegistration() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        GuestRegistrationInviteResponse invite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");

        assertThat(invite.token()).isNotBlank();
        assertThat(invite.portalPath()).isEqualTo("/guest-registration/" + invite.token());
        GuestRegistration pending = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(pending.getTokenHash()).hasSize(64).isNotEqualTo(invite.token());
        assertThat(pending.getInvitedBy()).isEqualTo("Christopher");

        PublicGuestRegistrationResponse publicView = service.getPublicGuestRegistration(invite.token());
        assertThat(publicView.status()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(publicView.ruleCode()).isEqualTo("CH-MELDESCHEIN");
        assertThat(publicView.ruleVersion()).isEqualTo(1);
        assertThat(publicView.requiredFields()).contains("documentNumber", "privacyConsent");

        PublicGuestRegistrationResponse completed = service.completePublicGuestRegistration(
                invite.token(),
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", "ZH 12345", "Gabriela Tschopp", true,
                        publicView.ruleCode(), publicView.ruleVersion()
                )
        );

        assertThat(completed.status()).isEqualTo(GuestRegistrationStatus.COMPLETED);
        assertThat(completed.ruleCode()).isEqualTo(publicView.ruleCode());
        assertThat(completed.ruleVersion()).isEqualTo(publicView.ruleVersion());
        GuestRegistration stored = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getTokenHash()).isNull();
        assertThat(stored.getDocumentHash()).hasSize(64).doesNotContain("X123456789");
        assertThat(stored.getRuleCode()).isEqualTo(publicView.ruleCode());
        assertThat(stored.getRuleVersion()).isEqualTo(publicView.ruleVersion());
        assertThat(stored.getPrivacyConsentAt()).isNotNull();
        assertThatThrownBy(() -> service.getPublicGuestRegistration(invite.token()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ungültig");
    }

    @Test
    void selectsRegistrationRuleFromHotelCountryAndUsesNeutralGlobalFallback() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        GuestRegistrationInviteResponse swissInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        assertThat(service.getPublicGuestRegistration(swissInvite.token()).ruleCode())
                .isEqualTo("CH-MELDESCHEIN");

        property.setCountryCode("DE");
        propertyRepository.saveAndFlush(property);
        GuestRegistrationInviteResponse germanInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        assertThat(service.getPublicGuestRegistration(germanInvite.token()).ruleCode())
                .isEqualTo("DE-MELDESCHEIN");

        property.setCountryCode("AT");
        propertyRepository.saveAndFlush(property);
        GuestRegistrationInviteResponse globalInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        PublicGuestRegistrationResponse globalView =
                service.getPublicGuestRegistration(globalInvite.token());
        assertThat(globalView.ruleCode()).isEqualTo("GLOBAL-REGISTRATION");
        assertThat(globalView.ruleVersion()).isEqualTo(1);
        assertThat(globalView.requiredFields())
                .containsExactlyElementsOf(List.of(
                        "addressLine", "postalCode", "city", "countryCode", "nationalityCode",
                        "documentNumber", "signatureName", "privacyConsent"
                ));
    }

    @Test
    void rejectsPublicCompletionWhenDisplayedRuleVersionDoesNotMatchInvite() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        GuestRegistrationInviteResponse invite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");

        assertThatThrownBy(() -> service.completePublicGuestRegistration(
                invite.token(),
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", null, "Gabriela Tschopp", true,
                        "GLOBAL-REGISTRATION", 1
                )
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Regelversion")
                .hasMessageContaining("Formular neu");

        GuestRegistration stored =
                guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(stored.getPrivacyConsentAt()).isNull();
    }

    @Test
    void booksHotelResourcesWithoutAllowingTimeConflicts() {
        PmsAdvancedResponse withResource = service.createHotelResource(
                company, property.getId(),
                new UpsertHotelResourceRequest(
                        HotelResourceType.CONFERENCE_ROOM, "CONF-1", "Konferenzraum Zürich",
                        "Erdgeschoss", 20, new BigDecimal("120.00"), "CHF", true
                ),
                today
        );
        Long resourceId = withResource.hotelResources().get(0).id();
        LocalDateTime start = today.atTime(9, 0);
        LocalDateTime end = today.atTime(12, 0);

        PmsAdvancedResponse booked = service.createResourceBooking(
                company, property.getId(),
                new CreateResourceBookingRequest(
                        resourceId, null, "Strategiemeeting", "Beispiel AG",
                        start, end, 12, ResourceBookingStatus.CONFIRMED,
                        new BigDecimal("360.00"), "Beamer benötigt"
                ),
                "Christopher", today
        );

        assertThat(booked.resourceBookings()).singleElement().satisfies(booking -> {
            assertThat(booking.resourceName()).isEqualTo("Konferenzraum Zürich");
            assertThat(booking.status()).isEqualTo(ResourceBookingStatus.CONFIRMED);
            assertThat(booking.totalAmount()).isEqualByComparingTo("360.00");
        });
        assertThatThrownBy(() -> service.createResourceBooking(
                company, property.getId(),
                new CreateResourceBookingRequest(
                        resourceId, null, "Überschneidung", "Andere Firma",
                        start.plusHours(1), end.plusHours(1), 4, ResourceBookingStatus.CONFIRMED,
                        BigDecimal.ZERO, null
                ),
                "Christopher", today
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bereits gebucht");

        Long bookingId = booked.resourceBookings().get(0).id();
        PmsAdvancedResponse cancelled = service.cancelResourceBooking(
                company, property.getId(), bookingId, today);
        assertThat(cancelled.resourceBookings().get(0).status())
                .isEqualTo(ResourceBookingStatus.CANCELLED);
    }

    @Test
    void createsMasterAndChildCompanyCardsWithStableReferencesAndMergeHistory() {
        PmsAdvancedResponse withMaster = service.createOrganization(
                company, property.getId(),
                new UpsertOrganizationRequest(
                        OrganizationType.COMPANY, "BMW Hauptsitz", null, "Hauptstrasse 1", "8000",
                        "Zürich", "CH", "hq@example.com", null, "invoice@example.com",
                        30, null, true, true, null),
                today);
        PmsAdvancedResponse.OrganizationView master = withMaster.organizations().stream()
                .filter(PmsAdvancedResponse.OrganizationView::masterRecord).findFirst().orElseThrow();

        PmsAdvancedResponse withChild = service.createOrganization(
                company, property.getId(),
                new UpsertOrganizationRequest(
                        OrganizationType.COMPANY, "BMW Basel", null, "Rheinweg 2", "4051",
                        "Basel", "CH", "basel@example.com", null, null,
                        10, null, true, false, master.id()),
                today);
        PmsAdvancedResponse.OrganizationView child = withChild.organizations().stream()
                .filter(value -> value.name().equals("BMW Basel")).findFirst().orElseThrow();

        assertThat(master.referenceCode()).startsWith("MK");
        assertThat(child.referenceCode()).startsWith("FK");
        assertThat(child.parentOrganizationId()).isEqualTo(master.id());
        assertThat(child.parentOrganizationName()).isEqualTo("BMW Hauptsitz");

        service.mergeOrganization(company, property.getId(), child.id(),
                new MergeOrganizationsRequest(master.id(), java.util.Set.of("email")), today);

        PmsOrganization retainedSource = organizationRepository.findById(child.id()).orElseThrow();
        PmsOrganization target = organizationRepository.findById(master.id()).orElseThrow();
        assertThat(retainedSource.isActive()).isFalse();
        assertThat(retainedSource.getMergedInto().getId()).isEqualTo(master.id());
        assertThat(target.getEmail()).isEqualTo("basel@example.com");
    }

    @Test
    void snapshotsEmployeeBillingAndSupplierAndPreservesMixedTaxLinesOnCredit() {
        property.setTaxNumber("CHE-123.456.789 MWST");
        property.setInvoicePrefix("ZRH");
        property.setInvoiceFooter("Register Zürich · Zahlung netto");
        PmsOrganization organization = new PmsOrganization();
        organization.setCompany(company); organization.setName("International Company");
        organization.setType(OrganizationType.COMPANY); organization.setCountryCode("US");
        organization = organizationRepository.save(organization);
        guest.setOrganization(organization);
        guest.setBillingOverride(true);
        BillingProfile billing = new BillingProfile("US Branch Inc.", "Finance / Gabriela", "Main Street 1", "Floor 4",
                "10001", "New York", "NY", "US", "US-CUSTOMER-123", "ap@example.com", "PO-23", "CC-42",
                "PERSON_FIRST", "CITY_REGION_POSTAL", "Cost approval on file");
        guest.setBillingProfile(PmsProfileData.encode(billing));
        var created = operationsService.createReservation(company, reservationRequest(), "Tester", today);
        Long folioId = created.folios().get(0).id();
        var items = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId);
        items.get(0).setTaxRate(new BigDecimal("8.10"));
        items.get(1).setTaxRate(new BigDecimal("3.80"));
        var issued = service.createInvoice(company, property.getId(), new CreateInvoiceRequest(folioId,today.plusDays(10),
                new BigDecimal("99"),"ignored","ignored",null,null,"CH",null,null,true,null),today);
        var invoice = invoiceRepository.findById(issued.invoices().get(0).id()).orElseThrow();
        assertThat(invoice.getRecipientName()).isEqualTo("US Branch Inc.");
        assertThat(invoice.getInvoiceNumber()).startsWith("ZRH-");
        assertThat(invoice.getDueDate()).isEqualTo(today.plusDays(10));
        assertThat(invoice.getRecipientCountryCode()).isEqualTo("US");
        assertThat(PmsProfileData.billing(invoice.getRecipientSnapshot()).costCenter()).isEqualTo("CC-42");
        assertThat(PmsProfileData.recipientBlock(billing)).startsWith("Finance / Gabriela\nUS Branch Inc.").contains("New York NY 10001");
        assertThat(invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(invoice.getId()))
                .extracting(PmsInvoiceLine::getVatRate).containsExactly(new BigDecimal("8.10"),new BigDecimal("3.80"));
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("204.14");
        property.setLegalName("Changed operator"); guest.setBillingProfile(null);
        assertThat(PmsProfileData.billing(invoice.getSupplierSnapshot()).legalName()).isEqualTo("Chrono Hotel AG");
        assertThat(service.generateInvoicePdf(company,invoice.getId())).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var corrected = service.correctInvoice(company,property.getId(),invoice.getId(),new CorrectInvoiceRequest("Test correction"),"Tester",today);
        var creditId = corrected.invoices().stream().filter(value -> value.type() == InvoiceType.CREDIT_NOTE).findFirst().orElseThrow().id();
        var credit = invoiceRepository.findById(creditId).orElseThrow();
        assertThat(credit.getRecipientSnapshot()).isEqualTo(invoice.getRecipientSnapshot());
        assertThat(credit.getSupplierSnapshot()).isEqualTo(invoice.getSupplierSnapshot());
        assertThat(credit.getNetAmount()).isEqualByComparingTo(invoice.getNetAmount().negate());
    }

    @Test
    void storesMultipleContactsAndLinksGuestWithinTenant() {
        var request = new UpsertOrganizationRequest(OrganizationType.COMPANY,"Acme","US-123","Main Street 1","10001",
                "New York","US","main@example.com",null,"ap@example.com",30,null,true,false,null,
                "owner@example.com","office@example.com",List.of("extra@example.com"),
                List.of(new OrganizationContact(null,guest.getId(),"Gabriela Tschopp","Travel manager","travel@example.com",null,true),
                        new OrganizationContact(null,null,"Accounts payable","Finance","ap@example.com",null,false)),null);
        var result=service.createOrganization(company,property.getId(),request,today).organizations().get(0);
        assertThat(result.contacts()).hasSize(2);
        assertThat(result.contacts().get(0).id()).isNotBlank();
        assertThat(guest.getOrganization().getId()).isEqualTo(result.id());
        assertThat(result.additionalEmails()).containsExactly("extra@example.com");
        Company other=companyRepository.save(new Company("Other tenant"));
        GuestProfile foreign=new GuestProfile(); foreign.setCompany(other);foreign.setFirstName("Foreign");foreign.setLastName("Guest");guestRepository.save(foreign);
        var invalid=new UpsertOrganizationRequest(OrganizationType.COMPANY,"Bad",null,null,null,null,"US",null,null,null,0,null,true,false,null,
                null,null,null,List.of(new OrganizationContact(null,foreign.getId(),"Foreign",null,null,null,true)),null);
        assertThatThrownBy(() -> service.createOrganization(company,property.getId(),invalid,today))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Gast nicht gefunden");
    }

    @Test
    void storesPrivateValidatedContractAndRejectsOtherTenantDownload() throws Exception {
        PmsOrganization organization=new PmsOrganization(); organization.setCompany(company);organization.setName("Contracts");organization.setType(OrganizationType.COMPANY);
        organization=organizationRepository.save(organization);
        byte[] pdf="%PDF-1.4\ncontract".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var saved=documentService.upload(company,organization.getId(),ratePlan.getId(),
                new org.springframework.mock.web.MockMultipartFile("file","contract.pdf","application/pdf",pdf),"Tester");
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long loadedBefore = statistics.getEntityStatistics(PmsProfileDocument.class.getName()).getLoadCount();
            var metadata = documentService.list(company,organization.getId());
            assertThat(metadata).singleElement().satisfies(value -> assertThat(value.fileName()).isEqualTo("contract.pdf"));
            assertThat(statistics.getEntityStatistics(PmsProfileDocument.class.getName()).getLoadCount()).isEqualTo(loadedBefore);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(metadata);
            assertThat(json).contains("\"contentType\":\"application/pdf\"").doesNotContain("\"content\":");
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }
        assertThat(documentService.download(company,saved.id()).getContent()).containsExactly(pdf);
        Company other=companyRepository.save(new Company("Other tenant"));
        assertThatThrownBy(() -> documentService.download(other,saved.id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        Long organizationId=organization.getId();
        assertThatThrownBy(() -> documentService.upload(company,organizationId,null,
                new org.springframework.mock.web.MockMultipartFile("file","malware.pdf","application/pdf","<script>".getBytes()),"Tester"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Dateiinhalt");
    }

    @Test
    void contractVersionsKeepOriginalBytesAfterReplacementAndArchivalAndRejectStalePredecessor() throws Exception {
        PmsOrganization organization=new PmsOrganization();organization.setCompany(company);organization.setName("Versioned contracts");organization.setType(OrganizationType.COMPANY);
        organization=organizationRepository.saveAndFlush(organization);Long organizationId=organization.getId();
        byte[] original="%PDF-1.4\noriginal signed terms\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] replacement="%PDF-1.4\nrevised signed terms\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var first=documentService.upload(company,organizationId,ratePlan.getId(),new org.springframework.mock.web.MockMultipartFile("file","contract-v1.pdf","application/pdf",original),"version-tester");
        var second=documentService.upload(company,organizationId,ratePlan.getId(),first.id(),new org.springframework.mock.web.MockMultipartFile("file","contract-v2.pdf","application/pdf",replacement),"version-tester");
        entityManager.flush();entityManager.clear();
        assertThat(second.documentVersion()).isEqualTo(2);assertThat(first.documentVersion()).isEqualTo(1);assertThat(second.versionGroup()).isEqualTo(first.versionGroup());assertThat(second.sha256()).isNotEqualTo(first.sha256());
        try(var oldContent=documentService.payload(documentService.download(company,first.id())).getInputStream();var newContent=documentService.payload(documentService.download(company,second.id())).getInputStream()){
            assertThat(oldContent.readAllBytes()).isEqualTo(original);assertThat(newContent.readAllBytes()).isEqualTo(replacement);
        }
        documentService.delete(company,first.id());entityManager.flush();entityManager.clear();
        assertThat(documentService.download(company,first.id()).isArchived()).isTrue();
        try(var archived=documentService.payload(documentService.download(company,first.id())).getInputStream()){assertThat(archived.readAllBytes()).isEqualTo(original);}
        assertThat(documentService.list(company,organizationId)).singleElement().satisfies(entry->assertThat(entry.id()).isEqualTo(second.id()));
        assertThatThrownBy(()->documentService.upload(company,organizationId,ratePlan.getId(),first.id(),new org.springframework.mock.web.MockMultipartFile("file","stale-v2.pdf","application/pdf",replacement),"version-tester"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409").hasMessageContaining("neuere Version");
        assertThat(documentService.list(company,organizationId)).singleElement().satisfies(entry->assertThat(entry.id()).isEqualTo(second.id()));
    }

    @Test
    void anotherTenantCannotDownloadOrUseAContractVersionAsPredecessor() {
        PmsOrganization organization=new PmsOrganization();organization.setCompany(company);organization.setName("Private version");organization.setType(OrganizationType.COMPANY);organization=organizationRepository.saveAndFlush(organization);
        var file=new org.springframework.mock.web.MockMultipartFile("file","private.pdf","application/pdf","%PDF-1.4\nprivate".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var document=documentService.upload(company,organization.getId(),null,file,"test");
        Company other=companyRepository.save(new Company("Foreign version tenant"));PmsOrganization foreign=new PmsOrganization();foreign.setCompany(other);foreign.setType(OrganizationType.COMPANY);foreign.setName("Foreign company");foreign=organizationRepository.saveAndFlush(foreign);Long foreignId=foreign.getId();
        assertThatThrownBy(()->documentService.download(other,document.id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(()->documentService.upload(other,foreignId,null,document.id(),file,"foreign-user")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(documentService.list(other,foreignId)).isEmpty();
    }

    @Test
    void rejectsContractAttachmentForRateNegotiatedWithAnotherOrganization() {
        PmsOrganization first = new PmsOrganization(); first.setCompany(company);first.setName("First company");first.setType(OrganizationType.COMPANY);
        first = organizationRepository.save(first);
        PmsOrganization second = new PmsOrganization(); second.setCompany(company);second.setName("Second company");second.setType(OrganizationType.COMPANY);
        second = organizationRepository.save(second);
        ratePlan.setOrganization(second);
        Long targetOrganizationId = first.getId();
        var file = new org.springframework.mock.web.MockMultipartFile("file","contract.pdf","application/pdf","%PDF-1.4\ncontract".getBytes());
        assertThatThrownBy(() -> documentService.upload(company,targetOrganizationId,ratePlan.getId(),file,"Tester"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("anderen Firma");
        assertThat(documentService.list(company,targetOrganizationId)).isEmpty();
    }

    private Room room(String number) {
        Room value = new Room();
        value.setProperty(property);
        value.setRoomType(roomType);
        value.setNumber(number);
        value.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        return roomRepository.save(value);
    }

    private GuestProfile guest(String firstName, String lastName, String email) {
        GuestProfile value = new GuestProfile();
        value.setCompany(company);
        value.setFirstName(firstName);
        value.setLastName(lastName);
        value.setEmail(email);
        return guestRepository.save(value);
    }

    private CreateGroupBookingRequest groupRequest(List<CreateGroupBookingRequest.RoomingEntry> rooms) {
        return new CreateGroupBookingRequest(
                property.getId(), guest.getId(), null, "TEAM-26", "Team Zürich",
                today.plusDays(1), today.plusDays(3), GroupBookingStatus.CONFIRMED, null, rooms);
    }

    private CreateGroupBookingRequest.RoomingEntry rooming(GuestProfile roomGuest, Room room) {
        return new CreateGroupBookingRequest.RoomingEntry(
                roomGuest.getId(), roomType.getId(), room.getId(), ratePlan.getId(),
                1, 0, ReservationSource.DIRECT, null);
    }

    private UpsertReservationRequest reservationRequest() {
        return new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), room101.getId(), ratePlan.getId(),
                today.plusDays(1), today.plusDays(3), 1, 0, ReservationStatus.CONFIRMED,
                ReservationSource.DIRECT, null);
    }
}
