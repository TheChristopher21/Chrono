package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.qrcode.EncodeHintType;
import com.itextpdf.text.pdf.qrcode.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class PmsAdvancedService {

    @Value("${app.pms.provider-gateway.enabled:false}")
    private boolean providerGatewayEnabled;

    private final HotelPropertyRepository propertyRepository;
    private final PmsOrganizationRepository organizationRepository;
    private final GroupBookingRepository groupRepository;
    private final ReservationRepository reservationRepository;
    private final GuestProfileRepository guestRepository;
    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final PaymentRepository paymentRepository;
    private final PmsInvoiceRepository invoiceRepository;
    private final PmsInvoiceLineRepository invoiceLineRepository;
    private final NightAuditRepository nightAuditRepository;
    private final RoomRepository roomRepository;
    private final HousekeepingTaskRepository housekeepingRepository;
    private final CommunicationTemplateRepository templateRepository;
    private final GuestCommunicationRepository communicationRepository;
    private final IntegrationOutboxRepository outboxRepository;
    private final ExternalBookingReferenceRepository externalBookingRepository;
    private final ChannelConnectionRepository channelConnectionRepository;
    private final ChannelMappingRepository channelMappingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final GuestRegistrationRepository guestRegistrationRepository;
    private final HotelResourceRepository hotelResourceRepository;
    private final ResourceBookingRepository resourceBookingRepository;
    private final PmsAuditEventRepository auditEventRepository;
    private final PmsAuditWriter auditWriter;
    private final PmsOperationsService operationsService;

    public PmsAdvancedService(HotelPropertyRepository propertyRepository,
                              PmsOrganizationRepository organizationRepository,
                              GroupBookingRepository groupRepository,
                              ReservationRepository reservationRepository,
                              GuestProfileRepository guestRepository,
                              FolioRepository folioRepository,
                              FolioItemRepository folioItemRepository,
                              PaymentRepository paymentRepository,
                              PmsInvoiceRepository invoiceRepository,
                              PmsInvoiceLineRepository invoiceLineRepository,
                              NightAuditRepository nightAuditRepository,
                              RoomRepository roomRepository,
                              HousekeepingTaskRepository housekeepingRepository,
                              CommunicationTemplateRepository templateRepository,
                              GuestCommunicationRepository communicationRepository,
                              IntegrationOutboxRepository outboxRepository,
                              ExternalBookingReferenceRepository externalBookingRepository,
                              ChannelConnectionRepository channelConnectionRepository,
                              ChannelMappingRepository channelMappingRepository,
                              RoomTypeRepository roomTypeRepository,
                              RatePlanRepository ratePlanRepository,
                              GuestRegistrationRepository guestRegistrationRepository,
                              HotelResourceRepository hotelResourceRepository,
                              ResourceBookingRepository resourceBookingRepository,
                              PmsAuditEventRepository auditEventRepository,
                              PmsAuditWriter auditWriter,
                              PmsOperationsService operationsService) {
        this.propertyRepository = propertyRepository;
        this.organizationRepository = organizationRepository;
        this.groupRepository = groupRepository;
        this.reservationRepository = reservationRepository;
        this.guestRepository = guestRepository;
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.nightAuditRepository = nightAuditRepository;
        this.roomRepository = roomRepository;
        this.housekeepingRepository = housekeepingRepository;
        this.templateRepository = templateRepository;
        this.communicationRepository = communicationRepository;
        this.outboxRepository = outboxRepository;
        this.externalBookingRepository = externalBookingRepository;
        this.channelConnectionRepository = channelConnectionRepository;
        this.channelMappingRepository = channelMappingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.guestRegistrationRepository = guestRegistrationRepository;
        this.hotelResourceRepository = hotelResourceRepository;
        this.resourceBookingRepository = resourceBookingRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditWriter = auditWriter;
        this.operationsService = operationsService;
    }

    @Transactional(readOnly = true)
    public PmsAdvancedResponse getAdvanced(Company company, Long propertyId, LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse createHotelResource(Company company,
                                                   Long propertyId,
                                                   UpsertHotelResourceRequest request,
                                                   LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        String code = required(request.code()).toUpperCase(Locale.ROOT);
        if (hotelResourceRepository.existsByProperty_IdAndCodeIgnoreCase(propertyId, code)) {
            throw conflict("Der Ressourcencode ist bereits vergeben.");
        }
        HotelResource resource = new HotelResource();
        resource.setProperty(property);
        resource.setType(request.type());
        resource.setCode(code);
        resource.setName(required(request.name()));
        resource.setLocation(clean(request.location()));
        resource.setCapacity(request.capacity());
        resource.setHourlyRate(request.hourlyRate().setScale(2, RoundingMode.HALF_UP));
        resource.setCurrencyCode(clean(request.currencyCode()) == null
                ? property.getCurrencyCode()
                : request.currencyCode().toUpperCase(Locale.ROOT));
        resource.setActive(request.active());
        hotelResourceRepository.save(resource);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse createResourceBooking(Company company,
                                                     Long propertyId,
                                                     CreateResourceBookingRequest request,
                                                     String username,
                                                     LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        HotelResource resource = hotelResourceRepository
                .findByIdAndProperty_Company_IdForUpdate(request.resourceId(), company.getId())
                .orElseThrow(() -> notFound("Hotelressource nicht gefunden."));
        if (!resource.getProperty().getId().equals(propertyId) || !resource.isActive()) {
            throw conflict("Die Hotelressource ist nicht aktiv.");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw badRequest("Das Ende muss nach dem Beginn liegen.");
        }
        if (request.attendees() > resource.getCapacity()) {
            throw badRequest("Die Teilnehmerzahl überschreitet die Ressourcenkapazität.");
        }
        if (request.status() != ResourceBookingStatus.CANCELLED
                && resourceBookingRepository.countOverlapping(
                resource.getId(), request.startAt(), request.endAt(),
                List.of(ResourceBookingStatus.CANCELLED)) > 0) {
            throw conflict("Die Ressource ist in diesem Zeitraum bereits gebucht.");
        }
        GroupBooking group = null;
        if (request.groupBookingId() != null) {
            group = groupRepository.findById(request.groupBookingId())
                    .filter(value -> value.getProperty().getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> notFound("Gruppenbuchung nicht gefunden."));
            if (!group.getProperty().getId().equals(propertyId)) {
                throw notFound("Gruppenbuchung nicht gefunden.");
            }
        }
        ResourceBooking booking = new ResourceBooking();
        booking.setProperty(property);
        booking.setResource(resource);
        booking.setGroupBooking(group);
        booking.setTitle(required(request.title()));
        booking.setOrganizerName(required(request.organizerName()));
        booking.setStartAt(request.startAt());
        booking.setEndAt(request.endAt());
        booking.setAttendees(request.attendees());
        booking.setStatus(request.status());
        booking.setTotalAmount(request.totalAmount().setScale(2, RoundingMode.HALF_UP));
        booking.setNotes(clean(request.notes()));
        booking.setCreatedBy(clean(username) == null ? "system" : clean(username));
        resourceBookingRepository.save(booking);
        emit(property, "resource.booking_created", "resource_booking", booking.getId().toString(),
                "{\"resourceId\":" + resource.getId() + "}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse cancelResourceBooking(Company company,
                                                     Long propertyId,
                                                     Long bookingId,
                                                     LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        ResourceBooking booking = resourceBookingRepository
                .findByIdAndProperty_Company_Id(bookingId, company.getId())
                .orElseThrow(() -> notFound("Ressourcenbuchung nicht gefunden."));
        if (!booking.getProperty().getId().equals(propertyId)) {
            throw notFound("Ressourcenbuchung nicht gefunden.");
        }
        booking.setStatus(ResourceBookingStatus.CANCELLED);
        resourceBookingRepository.save(booking);
        emit(property, "resource.booking_cancelled", "resource_booking", booking.getId().toString(), "{}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse createOrganization(Company company,
                                                  Long propertyId,
                                                  UpsertOrganizationRequest request,
                                                  LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        PmsOrganization organization = new PmsOrganization();
        organization.setCompany(company);
        applyOrganization(organization, request);
        organizationRepository.save(organization);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse updateOrganization(Company company,
                                                  Long propertyId,
                                                  Long organizationId,
                                                  UpsertOrganizationRequest request,
                                                  LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        PmsOrganization organization = organizationRepository.findByIdAndCompany_Id(organizationId, company.getId())
                .orElseThrow(() -> notFound("Firma oder Reisebüro nicht gefunden."));
        applyOrganization(organization, request);
        organizationRepository.save(organization);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse createGroupBooking(Company company,
                                                  CreateGroupBookingRequest request,
                                                  String username,
                                                  LocalDate businessDate) {
        HotelProperty property = lockProperty(company, request.propertyId());
        validateStay(request.arrivalDate(), request.departureDate());
        String groupCode = required(request.groupCode()).toUpperCase(Locale.ROOT);
        if (groupRepository.existsByProperty_IdAndGroupCodeIgnoreCase(property.getId(), groupCode)) {
            throw conflict("Der Gruppencode ist bereits vergeben.");
        }
        GuestProfile contact = requireGuest(company, request.contactGuestId());
        PmsOrganization organization = request.organizationId() == null
                ? null
                : requireOrganization(company, request.organizationId());

        GroupBooking group = new GroupBooking();
        group.setProperty(property);
        group.setContactGuest(contact);
        group.setOrganization(organization);
        group.setGroupCode(groupCode);
        group.setName(required(request.name()));
        group.setArrivalDate(request.arrivalDate());
        group.setDepartureDate(request.departureDate());
        group.setStatus(request.status() == null ? GroupBookingStatus.CONFIRMED : request.status());
        group.setNotes(clean(request.notes()));
        groupRepository.save(group);

        ReservationStatus reservationStatus = group.getStatus() == GroupBookingStatus.OPTION
                ? ReservationStatus.TENTATIVE
                : ReservationStatus.CONFIRMED;
        for (CreateGroupBookingRequest.RoomingEntry room : request.rooms()) {
            UpsertReservationRequest reservationRequest = new UpsertReservationRequest(
                    property.getId(), room.guestId(), room.roomTypeId(), room.roomId(), room.ratePlanId(),
                    request.arrivalDate(), request.departureDate(), room.adults(), room.children(),
                    reservationStatus, room.source() == null ? ReservationSource.DIRECT : room.source(),
                    clean(room.notes())
            );
            Reservation reservation = operationsService.createReservationRecord(company, reservationRequest, username);
            reservation.setGroupBooking(group);
            reservationRepository.save(reservation);
        }
        emit(property, "group_booking.created", "group_booking", group.getId().toString(),
                "{\"groupCode\":\"" + json(group.getGroupCode()) + "\",\"roomCount\":" + request.rooms().size() + "}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsOperationsResponse createSplitFolio(Company company,
                                                  Long propertyId,
                                                  CreateSplitFolioRequest request,
                                                  LocalDate businessDate) {
        requireProperty(company, propertyId);
        Reservation reservation = requireReservation(company, request.reservationId());
        if (!reservation.getProperty().getId().equals(propertyId)) {
            throw notFound("Reservierung nicht gefunden.");
        }
        Folio folio = new Folio();
        folio.setReservation(reservation);
        folio.setCurrencyCode(reservation.getCurrencyCode());
        folio.setLabel(required(request.label()));
        folio.setOrganization(request.organizationId() == null
                ? null
                : requireOrganization(company, request.organizationId()));
        folio.setStatus(FolioStatus.OPEN);
        folioRepository.save(folio);
        return operationsService.getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse moveFolioItems(Company company,
                                                Long propertyId,
                                                Long sourceFolioId,
                                                MoveFolioItemsRequest request,
                                                LocalDate businessDate) {
        requireProperty(company, propertyId);
        Folio source = requireFolio(company, sourceFolioId);
        Folio target = requireFolio(company, request.targetFolioId());
        if (!source.getReservation().getProperty().getId().equals(propertyId)
                || !target.getReservation().getProperty().getId().equals(propertyId)
                || !source.getReservation().getId().equals(target.getReservation().getId())) {
            throw badRequest("Folio-Positionen können nur innerhalb derselben Reservierung verschoben werden.");
        }
        if (source.getStatus() != FolioStatus.OPEN || target.getStatus() != FolioStatus.OPEN) {
            throw conflict("Nur offene Folios können aufgeteilt werden.");
        }
        for (Long itemId : request.itemIds()) {
            FolioItem item = folioItemRepository.findById(itemId)
                    .orElseThrow(() -> notFound("Folio-Position nicht gefunden."));
            if (!item.getFolio().getId().equals(source.getId())) {
                throw badRequest("Mindestens eine Position gehört nicht zum Quellfolio.");
            }
            item.setFolio(target);
            folioItemRepository.save(item);
        }
        return operationsService.getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsAdvancedResponse createInvoice(Company company,
                                             Long propertyId,
                                             CreateInvoiceRequest request,
                                             LocalDate businessDate) {
        HotelProperty property = lockProperty(company, propertyId);
        Folio folio = requireFolio(company, request.folioId());
        if (!folio.getReservation().getProperty().getId().equals(propertyId)) {
            throw notFound("Folio nicht gefunden.");
        }
        List<FolioItem> items = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId());
        if (items.isEmpty()) {
            throw conflict("Eine Rechnung benötigt mindestens eine Folio-Position.");
        }
        LocalDate issueDate = effectiveDate(property, businessDate);
        if (request.dueDate().isBefore(issueDate)) {
            throw badRequest("Das Fälligkeitsdatum darf nicht vor dem Rechnungsdatum liegen.");
        }
        String iban = normalizeIban(request.creditorIban());
        String reference = clean(request.qrReference());
        validateQrPaymentData(iban, reference);
        BigDecimal gross = money(items.stream().map(FolioItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (gross.signum() <= 0) {
            throw conflict("Der Rechnungsbetrag muss positiv sein.");
        }
        BigDecimal divisor = BigDecimal.ONE.add(request.vatRate().movePointLeft(2));
        BigDecimal net = money(gross.divide(divisor, 8, RoundingMode.HALF_UP));
        BigDecimal vat = money(gross.subtract(net));

        PmsInvoice invoice = new PmsInvoice();
        invoice.setProperty(property);
        invoice.setFolio(folio);
        invoice.setType(InvoiceType.INVOICE);
        invoice.setInvoiceNumber("INV-" + issueDate.getYear() + "-"
                + String.format(Locale.ROOT, "%05d", invoiceRepository.countByProperty_Id(propertyId) + 1));
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(request.dueDate());
        invoice.setRecipientName(required(request.recipientName()));
        invoice.setRecipientAddress(clean(request.recipientAddress()));
        invoice.setRecipientPostalCode(clean(request.recipientPostalCode()));
        invoice.setRecipientCity(clean(request.recipientCity()));
        invoice.setRecipientCountryCode(country(request.recipientCountryCode()));
        invoice.setCurrencyCode(folio.getCurrencyCode());
        invoice.setNetAmount(net);
        invoice.setVatAmount(vat);
        invoice.setGrossAmount(gross);
        invoice.setVatRate(request.vatRate().setScale(2, RoundingMode.HALF_UP));
        invoice.setCreditorIban(iban);
        invoice.setQrReference(reference);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoiceRepository.save(invoice);

        for (FolioItem item : items) {
            BigDecimal lineGross = money(item.getTotalAmount());
            BigDecimal lineNet = money(lineGross.divide(divisor, 8, RoundingMode.HALF_UP));
            PmsInvoiceLine line = new PmsInvoiceLine();
            line.setInvoice(invoice);
            line.setDescription(item.getDescription());
            line.setQuantity(item.getQuantity());
            line.setGrossAmount(lineGross);
            line.setNetAmount(lineNet);
            line.setVatAmount(money(lineGross.subtract(lineNet)));
            invoiceLineRepository.save(line);
        }
        emit(property, "invoice.issued", "invoice", invoice.getId().toString(),
                "{\"invoiceNumber\":\"" + json(invoice.getInvoiceNumber()) + "\",\"grossAmount\":\""
                        + invoice.getGrossAmount() + "\"}");
        return response(company, property, issueDate);
    }

    @Transactional
    public PmsAdvancedResponse correctInvoice(Company company,
                                              Long propertyId,
                                              Long invoiceId,
                                              CorrectInvoiceRequest request,
                                              String username,
                                              LocalDate businessDate) {
        HotelProperty property = lockProperty(company, propertyId);
        PmsInvoice original = invoiceRepository.findByIdAndProperty_Company_Id(invoiceId, company.getId())
                .orElseThrow(() -> notFound("Rechnung nicht gefunden."));
        if (!original.getProperty().getId().equals(propertyId)) {
            throw notFound("Rechnung nicht gefunden.");
        }
        if (original.getType() != InvoiceType.INVOICE || original.getStatus() != InvoiceStatus.ISSUED) {
            throw conflict("Nur eine ausgestellte, noch nicht korrigierte Rechnung kann gutgeschrieben werden.");
        }
        String reason = required(request.reason());
        LocalDate issueDate = effectiveDate(property, businessDate);

        PmsInvoice credit = new PmsInvoice();
        credit.setProperty(property);
        credit.setFolio(original.getFolio());
        credit.setType(InvoiceType.CREDIT_NOTE);
        credit.setOriginalInvoice(original);
        credit.setInvoiceNumber("CN-" + issueDate.getYear() + "-"
                + String.format(Locale.ROOT, "%05d", invoiceRepository.countByProperty_Id(propertyId) + 1));
        credit.setIssueDate(issueDate);
        credit.setDueDate(issueDate);
        credit.setRecipientName(original.getRecipientName());
        credit.setRecipientAddress(original.getRecipientAddress());
        credit.setRecipientPostalCode(original.getRecipientPostalCode());
        credit.setRecipientCity(original.getRecipientCity());
        credit.setRecipientCountryCode(original.getRecipientCountryCode());
        credit.setCurrencyCode(original.getCurrencyCode());
        credit.setNetAmount(original.getNetAmount().negate());
        credit.setVatAmount(original.getVatAmount().negate());
        credit.setGrossAmount(original.getGrossAmount().negate());
        credit.setVatRate(original.getVatRate());
        credit.setStatus(InvoiceStatus.ISSUED);
        credit.setCorrectionReason(reason);
        credit.setCorrectedAt(LocalDateTime.now());
        credit.setCorrectedBy(clean(username) == null ? "system" : clean(username));
        invoiceRepository.save(credit);

        for (PmsInvoiceLine source : invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(original.getId())) {
            PmsInvoiceLine line = new PmsInvoiceLine();
            line.setInvoice(credit);
            line.setDescription("Korrektur: " + source.getDescription());
            line.setQuantity(source.getQuantity());
            line.setNetAmount(source.getNetAmount().negate());
            line.setVatAmount(source.getVatAmount().negate());
            line.setGrossAmount(source.getGrossAmount().negate());
            invoiceLineRepository.save(line);
        }
        original.setStatus(InvoiceStatus.CREDITED);
        original.setCorrectionReason(reason);
        original.setCorrectedAt(LocalDateTime.now());
        original.setCorrectedBy(clean(username) == null ? "system" : clean(username));
        invoiceRepository.save(original);
        emit(property, "invoice.credited", "invoice", original.getId().toString(),
                "{\"invoiceNumber\":\"" + json(original.getInvoiceNumber())
                        + "\",\"creditNoteNumber\":\"" + json(credit.getInvoiceNumber()) + "\"}");
        return response(company, property, issueDate);
    }

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Company company, Long invoiceId) {
        PmsInvoice invoice = invoiceRepository.findByIdAndProperty_Company_Id(invoiceId, company.getId())
                .orElseThrow(() -> notFound("Rechnung nicht gefunden."));
        return renderInvoice(invoice, invoiceLineRepository.findAllByInvoice_IdOrderByIdAsc(invoiceId));
    }

    @Transactional
    public PmsAdvancedResponse closeNightAudit(Company company,
                                               Long propertyId,
                                               CloseNightAuditRequest request,
                                               String username) {
        HotelProperty property = lockProperty(company, propertyId);
        if (request.businessDate().isAfter(effectiveDate(property, null))) {
            throw badRequest("Ein zukünftiger Betriebstag kann nicht abgeschlossen werden.");
        }
        if (nightAuditRepository.findByProperty_IdAndBusinessDate(propertyId, request.businessDate()).isPresent()) {
            throw conflict("Dieser Betriebstag wurde bereits abgeschlossen.");
        }
        List<Reservation> reservations = reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        propertyId, request.businessDate().plusDays(1), request.businessDate().minusDays(1));
        if (request.markPendingArrivalsAsNoShow()) {
            reservations.stream()
                    .filter(reservation -> reservation.getArrivalDate().equals(request.businessDate()))
                    .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
                    .forEach(reservation -> {
                        reservation.setStatus(ReservationStatus.NO_SHOW);
                        reservationRepository.save(reservation);
                        emit(property, "reservation.no_show", "reservation", reservation.getId().toString(),
                                "{\"confirmationCode\":\"" + json(reservation.getConfirmationCode()) + "\"}");
                    });
        }
        long arrivals = reservations.stream()
                .filter(reservation -> reservation.getArrivalDate().equals(request.businessDate()))
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED).count();
        long departures = reservations.stream()
                .filter(reservation -> reservation.getDepartureDate().equals(request.businessDate()))
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED).count();
        long inHouse = reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN).count();
        long noShows = reservations.stream()
                .filter(reservation -> reservation.getArrivalDate().equals(request.businessDate()))
                .filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW).count();
        BigDecimal openBalance = folioRepository.findAllByReservation_Property_IdOrderByCreatedAtDesc(propertyId)
                .stream().filter(folio -> folio.getStatus() == FolioStatus.OPEN)
                .map(this::folioBalance).filter(amount -> amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NightAudit audit = new NightAudit();
        audit.setProperty(property);
        audit.setBusinessDate(request.businessDate());
        audit.setArrivalsCount(arrivals);
        audit.setDeparturesCount(departures);
        audit.setInHouseCount(inHouse);
        audit.setNoShowCount(noShows);
        audit.setOpenBalance(money(openBalance));
        audit.setClosedBy(clean(username) == null ? "system" : clean(username));
        audit.setClosedAt(LocalDateTime.now());
        nightAuditRepository.save(audit);
        emit(property, "night_audit.closed", "night_audit", audit.getId().toString(),
                "{\"businessDate\":\"" + audit.getBusinessDate() + "\"}");
        return response(company, property, request.businessDate());
    }

    @Transactional
    public PmsOperationsResponse createHousekeepingTask(Company company,
                                                        Long propertyId,
                                                        CreateHousekeepingTaskRequest request,
                                                        LocalDate businessDate) {
        requireProperty(company, propertyId);
        Room room = roomRepository.findByIdAndProperty_Company_Id(request.roomId(), company.getId())
                .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
        if (!room.getProperty().getId().equals(propertyId)) {
            throw notFound("Zimmer nicht gefunden.");
        }
        HousekeepingTask task = housekeepingRepository.findByRoom_IdAndServiceDate(room.getId(), request.serviceDate())
                .orElseGet(HousekeepingTask::new);
        task.setProperty(room.getProperty());
        task.setRoom(room);
        task.setServiceDate(request.serviceDate());
        task.setType(request.type());
        task.setStatus(room.getHousekeepingStatus());
        task.setPriority(request.priority());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.setNotes(clean(request.notes()));
        task.setAssignedTo(clean(request.assignedTo()));
        housekeepingRepository.save(task);
        return operationsService.getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsAdvancedResponse createTemplate(Company company,
                                              Long propertyId,
                                              UpsertCommunicationTemplateRequest request,
                                              LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        if (templateRepository.existsByProperty_IdAndCodeIgnoreCase(propertyId, required(request.code()))) {
            throw conflict("Der Vorlagencode ist bereits vergeben.");
        }
        CommunicationTemplate template = new CommunicationTemplate();
        template.setProperty(property);
        applyTemplate(template, request);
        templateRepository.save(template);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse updateTemplate(Company company,
                                              Long propertyId,
                                              Long templateId,
                                              UpsertCommunicationTemplateRequest request,
                                              LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        CommunicationTemplate template = templateRepository.findByIdAndProperty_Company_Id(templateId, company.getId())
                .orElseThrow(() -> notFound("Kommunikationsvorlage nicht gefunden."));
        if (!template.getProperty().getId().equals(propertyId)) {
            throw notFound("Kommunikationsvorlage nicht gefunden.");
        }
        applyTemplate(template, request);
        templateRepository.save(template);
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse queueCommunication(Company company,
                                                  Long propertyId,
                                                  QueueCommunicationRequest request,
                                                  LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        GuestProfile guest = requireGuest(company, request.guestId());
        Reservation reservation = request.reservationId() == null ? null
                : requireReservation(company, request.reservationId());
        if (reservation != null && (!reservation.getProperty().getId().equals(propertyId)
                || !reservation.getGuest().getId().equals(guest.getId()))) {
            throw badRequest("Reservierung und Gast passen nicht zusammen.");
        }
        CommunicationTemplate template = templateRepository.findByIdAndProperty_Company_Id(
                        request.templateId(), company.getId())
                .orElseThrow(() -> notFound("Kommunikationsvorlage nicht gefunden."));
        if (!template.getProperty().getId().equals(propertyId) || !template.isActive()) {
            throw conflict("Die Kommunikationsvorlage ist nicht aktiv.");
        }
        String recipient = clean(request.recipient()) == null ? clean(guest.getEmail()) : clean(request.recipient());
        if (recipient == null) {
            throw badRequest("Für die Kommunikation wird eine E-Mail-Adresse benötigt.");
        }
        GuestCommunication communication = new GuestCommunication();
        communication.setProperty(property);
        communication.setGuest(guest);
        communication.setReservation(reservation);
        communication.setRecipient(recipient);
        communication.setChannel(CommunicationChannel.EMAIL);
        communication.setDirection(CommunicationDirection.OUTBOUND);
        communication.setSubject(render(template.getSubject(), property, guest, reservation));
        communication.setBody(render(template.getBody(), property, guest, reservation));
        communication.setStatus(CommunicationStatus.QUEUED);
        communicationRepository.save(communication);
        emit(property, "communication.queued", "guest_communication", communication.getId().toString(),
                "{\"recipient\":\"" + json(recipient) + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse recordInboundCommunication(Company company,
                                                           Long propertyId,
                                                           PostInboundCommunicationRequest request,
                                                           LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        GuestProfile guest = requireGuest(company, request.guestId());
        Reservation reservation = request.reservationId() == null ? null
                : requireReservation(company, request.reservationId());
        validateCommunicationReservation(propertyId, guest, reservation);

        GuestCommunication communication = new GuestCommunication();
        communication.setProperty(property);
        communication.setGuest(guest);
        communication.setReservation(reservation);
        communication.setChannel(request.channel());
        communication.setDirection(CommunicationDirection.INBOUND);
        communication.setSender(required(request.sender()));
        communication.setRecipient(clean(property.getEmail()) == null ? property.getName() : clean(property.getEmail()));
        communication.setSubject(clean(request.subject()) == null ? "Nachricht" : clean(request.subject()));
        communication.setBody(required(request.body()));
        communication.setExternalThreadId(clean(request.externalThreadId()));
        communication.setStatus(CommunicationStatus.RECEIVED);
        communicationRepository.save(communication);
        emit(property, "communication.received", "guest_communication", communication.getId().toString(),
                "{\"channel\":\"" + communication.getChannel() + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse queueInboxReply(Company company,
                                                Long propertyId,
                                                QueueInboxReplyRequest request,
                                                LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        GuestProfile guest = requireGuest(company, request.guestId());
        Reservation reservation = request.reservationId() == null ? null
                : requireReservation(company, request.reservationId());
        validateCommunicationReservation(propertyId, guest, reservation);

        GuestCommunication communication = new GuestCommunication();
        communication.setProperty(property);
        communication.setGuest(guest);
        communication.setReservation(reservation);
        communication.setChannel(request.channel());
        communication.setDirection(CommunicationDirection.OUTBOUND);
        communication.setRecipient(required(request.recipient()));
        communication.setSubject(clean(request.subject()) == null ? "Antwort" : clean(request.subject()));
        communication.setBody(required(request.body()));
        communication.setExternalThreadId(clean(request.externalThreadId()));
        communication.setStatus(CommunicationStatus.QUEUED);
        communicationRepository.save(communication);
        emit(property, "communication.reply_queued", "guest_communication", communication.getId().toString(),
                "{\"channel\":\"" + communication.getChannel() + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse markCommunicationRead(Company company,
                                                      Long propertyId,
                                                      Long communicationId,
                                                      LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        GuestCommunication communication = communicationRepository
                .findByIdAndProperty_Company_Id(communicationId, company.getId())
                .orElseThrow(() -> notFound("Nachricht nicht gefunden."));
        if (!communication.getProperty().getId().equals(propertyId)) {
            throw notFound("Nachricht nicht gefunden.");
        }
        if (communication.getDirection() != CommunicationDirection.INBOUND) {
            throw badRequest("Nur eingehende Nachrichten können als gelesen markiert werden.");
        }
        if (communication.getReadAt() == null) {
            communication.setReadAt(LocalDateTime.now());
            communicationRepository.save(communication);
        }
        return response(company, property, effectiveDate(property, businessDate));
    }

    private void validateCommunicationReservation(Long propertyId,
                                                   GuestProfile guest,
                                                   Reservation reservation) {
        if (reservation != null && (!reservation.getProperty().getId().equals(propertyId)
                || !reservation.getGuest().getId().equals(guest.getId()))) {
            throw badRequest("Reservierung und Gast passen nicht zusammen.");
        }
    }

    @Transactional
    public PmsAdvancedResponse acknowledgeOutboxEvent(Company company,
                                                      Long propertyId,
                                                      Long eventId,
                                                      LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        IntegrationOutboxEvent event = outboxRepository.findByIdAndProperty_Company_Id(eventId, company.getId())
                .orElseThrow(() -> notFound("Integrationsereignis nicht gefunden."));
        if (!event.getProperty().getId().equals(propertyId)) {
            throw notFound("Integrationsereignis nicht gefunden.");
        }
        event.setStatus(OutboxStatus.DELIVERED);
        event.setDeliveredAt(LocalDateTime.now());
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setNextAttemptAt(null);
        event.setLastError(null);
        event.setLockedAt(null);
        event.setLockOwner(null);
        outboxRepository.save(event);
        auditWriter.append(property, "integration.outbox_acknowledged", "outbox",
                String.valueOf(event.getId()), "{\"eventType\":\"" + event.getEventType() + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse retryOutboxEvent(Company company,
                                                Long propertyId,
                                                Long eventId,
                                                LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        IntegrationOutboxEvent event = outboxRepository.findByIdAndProperty_Company_Id(eventId, company.getId())
                .orElseThrow(() -> notFound("Integrationsereignis nicht gefunden."));
        if (!event.getProperty().getId().equals(propertyId)) {
            throw notFound("Integrationsereignis nicht gefunden.");
        }
        if (event.getStatus() != OutboxStatus.FAILED && event.getStatus() != OutboxStatus.DEAD_LETTER) {
            throw conflict("Nur fehlgeschlagene Ereignisse können erneut eingeplant werden.");
        }
        event.setStatus(OutboxStatus.PENDING);
        event.setNextAttemptAt(LocalDateTime.now().withNano(0));
        event.setLastError(null);
        event.setLockedAt(null);
        event.setLockOwner(null);
        outboxRepository.save(event);
        auditWriter.append(property, "integration.outbox_retried", "outbox",
                String.valueOf(event.getId()), "{\"eventType\":\"" + event.getEventType() + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsOperationsResponse importExternalBooking(Company company,
                                                       ExternalBookingRequest request,
                                                       String username,
                                                       LocalDate businessDate) {
        UpsertReservationRequest booking = request.reservation();
        HotelProperty property = lockProperty(company, booking.propertyId());
        String channel = required(request.channel()).toUpperCase(Locale.ROOT);
        String externalId = required(request.externalId());
        ExternalBookingReference existing = externalBookingRepository
                .findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(property.getId(), channel, externalId)
                .orElse(null);
        if (existing != null) {
            return operationsService.getOperations(company, property.getId(), businessDate, null, null);
        }
        Reservation reservation = operationsService.createReservationRecord(
                company, booking, clean(username) == null ? "channel:" + channel : username);
        ExternalBookingReference reference = new ExternalBookingReference();
        reference.setProperty(property);
        reference.setReservation(reservation);
        reference.setChannelCode(channel);
        reference.setExternalId(externalId);
        externalBookingRepository.save(reference);
        emit(property, "booking.imported", "reservation", reservation.getId().toString(),
                "{\"channel\":\"" + json(channel) + "\",\"externalId\":\"" + json(externalId) + "\"}");
        return operationsService.getOperations(company, property.getId(), businessDate, null, null);
    }

    @Transactional
    public PmsAdvancedResponse createChannelConnection(
            Company company,
            Long propertyId,
            CreateChannelConnectionRequest request,
            LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        String providerCode = required(request.providerCode()).toUpperCase(Locale.ROOT);
        if (channelConnectionRepository.existsByProperty_IdAndProviderCodeIgnoreCase(propertyId, providerCode)) {
            throw conflict("Für diesen Provider besteht bereits eine Verbindung.");
        }
        if (request.environment() == ChannelEnvironment.LIVE
                && !isValidSecretReference(clean(request.secretReference()))) {
            throw badRequest("Eine Live-Verbindung benötigt eine Secret-Referenz im Format env:NAME.");
        }
        ChannelConnection connection = new ChannelConnection();
        connection.setProperty(property);
        connection.setProviderCode(providerCode);
        connection.setDisplayName(required(request.displayName()));
        connection.setEnvironment(request.environment());
        connection.setSecretReference(clean(request.secretReference()));
        connection.setStatus(ChannelConnectionStatus.READY);
        channelConnectionRepository.save(connection);
        for (CreateChannelConnectionRequest.Mapping mapping : request.mappings()) {
            RoomType roomType = roomTypeRepository.findByIdAndProperty_Company_Id(mapping.roomTypeId(), company.getId())
                    .orElseThrow(() -> notFound("Zimmertyp nicht gefunden."));
            RatePlan ratePlan = ratePlanRepository.findByIdAndProperty_Company_Id(mapping.ratePlanId(), company.getId())
                    .orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
            if (!roomType.getProperty().getId().equals(propertyId)
                    || !ratePlan.getProperty().getId().equals(propertyId)
                    || !ratePlan.getRoomType().getId().equals(roomType.getId())) {
                throw badRequest("Channel-Mapping, Zimmertyp und Rate passen nicht zusammen.");
            }
            ChannelMapping value = new ChannelMapping();
            value.setConnection(connection);
            value.setRoomType(roomType);
            value.setRatePlan(ratePlan);
            value.setExternalRoomCode(required(mapping.externalRoomCode()));
            value.setExternalRateCode(required(mapping.externalRateCode()));
            channelMappingRepository.save(value);
        }
        emit(property, "channel.connection_created", "channel_connection", connection.getId().toString(),
                "{\"provider\":\"" + json(providerCode) + "\",\"environment\":\""
                        + connection.getEnvironment() + "\"}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse syncChannelConnection(
            Company company,
            Long propertyId,
            Long connectionId,
            LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        ChannelConnection connection = channelConnectionRepository
                .findByIdAndProperty_Company_Id(connectionId, company.getId())
                .orElseThrow(() -> notFound("Channel-Verbindung nicht gefunden."));
        if (!connection.getProperty().getId().equals(propertyId)) {
            throw notFound("Channel-Verbindung nicht gefunden.");
        }
        if (connection.getEnvironment() == ChannelEnvironment.LIVE && !providerGatewayEnabled) {
            throw conflict("Der Live-Adapter benötigt ein konfiguriertes Provider-Gateway.");
        }
        connection.setStatus(ChannelConnectionStatus.READY);
        connection.setLastSyncAt(LocalDateTime.now());
        connection.setLastSyncMessage(connection.getEnvironment() == ChannelEnvironment.LIVE
                ? "Live-Snapshot zur signierten Provider-Zustellung eingeplant."
                : "Sandbox-Snapshot erfolgreich in die Outbox geschrieben.");
        channelConnectionRepository.save(connection);
        emit(property, "channel.inventory_snapshot_ready", "channel_connection", connection.getId().toString(),
                "{\"provider\":\"" + json(connection.getProviderCode()) + "\",\"mappingCount\":"
                        + channelMappingRepository.findAllByConnection_IdOrderByExternalRoomCodeAsc(connectionId).size()
                        + "}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public PmsAdvancedResponse completeGuestRegistration(
            Company company,
            Long propertyId,
            Long reservationId,
            CompleteGuestRegistrationRequest request,
            String username,
            LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        Reservation reservation = requireReservation(company, reservationId);
        if (!reservation.getProperty().getId().equals(propertyId)) {
            throw notFound("Reservierung nicht gefunden.");
        }
        String documentNumber = required(request.documentNumber());
        GuestRegistration registration = guestRegistrationRepository.findByReservation_Id(reservationId)
                .orElseGet(GuestRegistration::new);
        registration.setReservation(reservation);
        applyCompletedRegistration(registration, request, documentNumber, username);
        registration.setTokenHash(null);
        guestRegistrationRepository.save(registration);
        emit(property, "guest.registration_completed", "guest_registration",
                registration.getId().toString(), "{\"reservationId\":" + reservationId + "}");
        return response(company, property, effectiveDate(property, businessDate));
    }

    @Transactional
    public GuestRegistrationInviteResponse issueGuestRegistrationInvite(
            Company company,
            Long propertyId,
            Long reservationId,
            String username) {
        HotelProperty property = requireProperty(company, propertyId);
        Reservation reservation = requireReservation(company, reservationId);
        if (!reservation.getProperty().getId().equals(propertyId)) {
            throw notFound("Reservierung nicht gefunden.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW
                || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw conflict("Für diese Reservierung kann kein Check-in-Link mehr erstellt werden.");
        }
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime now = LocalDateTime.now();
        GuestRegistration registration = guestRegistrationRepository.findByReservation_Id(reservationId)
                .orElseGet(GuestRegistration::new);
        if (registration.getStatus() == GuestRegistrationStatus.COMPLETED) {
            throw conflict("Der Meldeschein wurde bereits abgeschlossen.");
        }
        registration.setReservation(reservation);
        registration.setStatus(GuestRegistrationStatus.PENDING);
        registration.setTokenHash(sha256(token));
        registration.setInvitedAt(now);
        registration.setInvitedBy(clean(username) == null ? "system" : clean(username));
        registration.setExpiresAt(now.plusDays(7));
        registration.setRuleCode(registrationRuleCode(property));
        registration.setRuleVersion(1);
        guestRegistrationRepository.save(registration);
        emit(property, "guest.registration_invited", "guest_registration",
                registration.getId().toString(), "{\"reservationId\":" + reservationId + "}");
        return new GuestRegistrationInviteResponse(
                registration.getId(), reservationId, token,
                "/guest-check-in/" + token, registration.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public PublicGuestRegistrationResponse getPublicGuestRegistration(String token) {
        GuestRegistration registration = requireValidRegistrationToken(token, false);
        return publicRegistrationView(registration);
    }

    @Transactional
    public PublicGuestRegistrationResponse completePublicGuestRegistration(
            String token,
            CompleteGuestRegistrationRequest request) {
        GuestRegistration registration = requireValidRegistrationToken(token, true);
        String documentNumber = required(request.documentNumber());
        applyCompletedRegistration(registration, request, documentNumber, "guest-portal");
        registration.setTokenHash(null);
        guestRegistrationRepository.save(registration);
        emit(registration.getReservation().getProperty(), "guest.registration_completed",
                "guest_registration", registration.getId().toString(),
                "{\"reservationId\":" + registration.getReservation().getId() + "}");
        return publicRegistrationView(registration);
    }

    private void applyCompletedRegistration(GuestRegistration registration,
                                            CompleteGuestRegistrationRequest request,
                                            String documentNumber,
                                            String username) {
        HotelProperty property = registration.getReservation().getProperty();
        registration.setStatus(GuestRegistrationStatus.COMPLETED);
        registration.setAddressLine(required(request.addressLine()));
        registration.setPostalCode(required(request.postalCode()));
        registration.setCity(required(request.city()));
        registration.setCountryCode(country(request.countryCode()));
        registration.setNationalityCode(country(request.nationalityCode()));
        registration.setDocumentHash(sha256(documentNumber));
        registration.setDocumentLastFour(documentNumber.substring(documentNumber.length() - 4));
        registration.setVehiclePlate(clean(request.vehiclePlate()));
        registration.setSignatureName(required(request.signatureName()));
        registration.setPrivacyConsentAt(LocalDateTime.now());
        registration.setCompletedAt(LocalDateTime.now());
        registration.setCompletedBy(clean(username) == null ? "system" : clean(username));
        if (clean(registration.getRuleCode()) == null) {
            registration.setRuleCode(registrationRuleCode(property));
            registration.setRuleVersion(1);
        }
    }

    private GuestRegistration requireValidRegistrationToken(String token, boolean requirePending) {
        String cleaned = required(token);
        GuestRegistration registration = guestRegistrationRepository.findByTokenHash(sha256(cleaned))
                .orElseThrow(() -> notFound("Check-in-Link ist ungültig."));
        if (registration.getExpiresAt() == null || !registration.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Check-in-Link ist abgelaufen.");
        }
        if (requirePending && registration.getStatus() != GuestRegistrationStatus.PENDING) {
            throw conflict("Der Meldeschein wurde bereits abgeschlossen.");
        }
        return registration;
    }

    private PublicGuestRegistrationResponse publicRegistrationView(GuestRegistration registration) {
        Reservation reservation = registration.getReservation();
        return new PublicGuestRegistrationResponse(
                registration.getId(), registration.getStatus(), reservation.getProperty().getName(),
                guestName(reservation.getGuest()), reservation.getConfirmationCode(),
                reservation.getArrivalDate(), reservation.getDepartureDate(),
                registration.getRuleCode(), registration.getRuleVersion(),
                registrationRequiredFields(registration.getRuleCode()), registration.getExpiresAt());
    }

    private String registrationRuleCode(HotelProperty property) {
        return switch (country(property.getCountryCode())) {
            case "CH" -> "CH-MELDESCHEIN";
            case "DE" -> "DE-MELDESCHEIN";
            default -> "GLOBAL-REGISTRATION";
        };
    }

    private List<String> registrationRequiredFields(String ruleCode) {
        return List.of("addressLine", "postalCode", "city", "countryCode", "nationalityCode",
                "documentNumber", "signatureName", "privacyConsent");
    }

    private PmsAdvancedResponse response(Company company, HotelProperty property, LocalDate businessDate) {
        return new PmsAdvancedResponse(
                property.getId(), businessDate,
                organizationRepository.findAllByCompany_IdOrderByNameAsc(company.getId()).stream()
                        .map(this::organizationView).toList(),
                groupRepository.findAllByProperty_IdOrderByArrivalDateDesc(property.getId()).stream()
                        .map(this::groupView).toList(),
                invoiceRepository.findAllByProperty_IdOrderByIssueDateDescIdDesc(property.getId()).stream()
                        .map(this::invoiceView).toList(),
                nightAuditRepository.findAllByProperty_IdOrderByBusinessDateDesc(property.getId()).stream()
                        .map(this::nightAuditView).toList(),
                templateRepository.findAllByProperty_IdOrderByNameAsc(property.getId()).stream()
                        .map(this::templateView).toList(),
                communicationRepository.findAllByProperty_IdOrderByCreatedAtDesc(property.getId()).stream()
                        .map(this::communicationView).toList(),
                outboxRepository.findAllByProperty_IdOrderByCreatedAtDesc(property.getId()).stream().limit(100)
                        .map(this::outboxView).toList(),
                channelConnectionRepository.findAllByProperty_IdOrderByDisplayNameAsc(property.getId()).stream()
                        .map(this::channelConnectionView).toList(),
                guestRegistrationRepository
                        .findAllByReservation_Property_IdOrderByCompletedAtDesc(property.getId()).stream()
                        .map(this::guestRegistrationView).toList(),
                hotelResourceRepository.findAllByProperty_IdOrderByTypeAscNameAsc(property.getId()).stream()
                        .map(this::hotelResourceView).toList(),
                resourceBookingRepository.findAllByProperty_IdOrderByStartAtDesc(property.getId()).stream()
                        .map(this::resourceBookingView).toList(),
                auditEventRepository.findTop100ByProperty_IdOrderByCreatedAtDesc(property.getId()).stream()
                        .map(this::auditEventView).toList()
        );
    }

    private PmsAdvancedResponse.OrganizationView organizationView(PmsOrganization value) {
        return new PmsAdvancedResponse.OrganizationView(
                value.getId(), value.getType(), value.getName(), value.getVatNumber(), value.getAddressLine1(),
                value.getPostalCode(), value.getCity(), value.getCountryCode(), value.getEmail(), value.getPhone(),
                value.getBillingEmail(), value.getPaymentTermsDays(), value.getNotes(), value.isActive());
    }

    private PmsAdvancedResponse.GroupBookingView groupView(GroupBooking group) {
        return new PmsAdvancedResponse.GroupBookingView(
                group.getId(), group.getGroupCode(), group.getName(), group.getArrivalDate(), group.getDepartureDate(),
                group.getStatus(), group.getContactGuest().getId(), guestName(group.getContactGuest()),
                group.getOrganization() == null ? null : group.getOrganization().getId(),
                group.getOrganization() == null ? null : group.getOrganization().getName(), group.getNotes(),
                reservationRepository.findAllByGroupBooking_IdOrderByGuest_LastNameAsc(group.getId()).stream()
                        .map(reservation -> new PmsAdvancedResponse.GroupMemberView(
                                reservation.getId(), reservation.getConfirmationCode(), reservation.getGuest().getId(),
                                guestName(reservation.getGuest()), reservation.getRoomType().getId(),
                                reservation.getRoomType().getName(),
                                reservation.getRoom() == null ? null : reservation.getRoom().getId(),
                                reservation.getRoom() == null ? null : reservation.getRoom().getNumber(),
                                reservation.getStatus(), money(reservation.getTotalAmount()))).toList());
    }

    private PmsAdvancedResponse.InvoiceView invoiceView(PmsInvoice invoice) {
        return new PmsAdvancedResponse.InvoiceView(
                invoice.getId(), invoice.getFolio().getId(), invoice.getInvoiceNumber(), invoice.getType(),
                invoice.getOriginalInvoice() == null ? null : invoice.getOriginalInvoice().getId(),
                invoice.getOriginalInvoice() == null ? null : invoice.getOriginalInvoice().getInvoiceNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(), invoice.getRecipientName(), invoice.getCurrencyCode(), invoice.getNetAmount(),
                invoice.getVatAmount(), invoice.getGrossAmount(), invoice.getVatRate(), invoice.getStatus(),
                clean(invoice.getCreditorIban()) != null, invoice.getCorrectionReason(), invoice.getCorrectedAt(),
                invoice.getCorrectedBy());
    }

    private PmsAdvancedResponse.NightAuditView nightAuditView(NightAudit audit) {
        return new PmsAdvancedResponse.NightAuditView(
                audit.getId(), audit.getBusinessDate(), audit.getArrivalsCount(), audit.getDeparturesCount(),
                audit.getInHouseCount(), audit.getNoShowCount(), audit.getOpenBalance(), audit.getClosedBy(),
                audit.getClosedAt());
    }

    private PmsAdvancedResponse.CommunicationTemplateView templateView(CommunicationTemplate template) {
        return new PmsAdvancedResponse.CommunicationTemplateView(
                template.getId(), template.getCode(), template.getName(), template.getSubject(), template.getBody(),
                template.getLanguageCode(), template.isActive());
    }

    private PmsAdvancedResponse.GuestCommunicationView communicationView(GuestCommunication communication) {
        return new PmsAdvancedResponse.GuestCommunicationView(
                communication.getId(), communication.getGuest().getId(), guestName(communication.getGuest()),
                communication.getReservation() == null ? null : communication.getReservation().getId(),
                communication.getRecipient(), communication.getSender(), communication.getChannel(),
                communication.getDirection(), communication.getExternalThreadId(),
                communication.getSubject(), communication.getBody(),
                communication.getStatus(), communication.getCreatedAt(), communication.getSentAt(),
                communication.getReadAt());
    }

    private PmsAdvancedResponse.OutboxEventView outboxView(IntegrationOutboxEvent event) {
        return new PmsAdvancedResponse.OutboxEventView(
                event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                event.getPayload(), event.getStatus(), event.getAttemptCount(), event.getCreatedAt(),
                event.getDeliveredAt(), event.getNextAttemptAt(), event.getLastAttemptAt(), event.getLastError());
    }

    private PmsAdvancedResponse.AuditEventView auditEventView(PmsAuditEvent event) {
        return new PmsAdvancedResponse.AuditEventView(
                event.getId(), event.getActor(), event.getEventType(), event.getAggregateType(),
                event.getAggregateId(), event.getDetails(), event.getIntegrityHash(), event.getCreatedAt());
    }

    private PmsAdvancedResponse.ChannelConnectionView channelConnectionView(ChannelConnection connection) {
        return new PmsAdvancedResponse.ChannelConnectionView(
                connection.getId(), connection.getProviderCode(), connection.getDisplayName(),
                connection.getEnvironment(), connection.getStatus(), connection.getSecretReference(),
                connection.getLastSyncAt(), connection.getLastSyncMessage(),
                channelMappingRepository.findAllByConnection_IdOrderByExternalRoomCodeAsc(connection.getId())
                        .stream()
                        .map(mapping -> new PmsAdvancedResponse.ChannelMappingView(
                                mapping.getId(), mapping.getRoomType().getId(), mapping.getRoomType().getName(),
                                mapping.getRatePlan().getId(), mapping.getRatePlan().getName(),
                                mapping.getExternalRoomCode(), mapping.getExternalRateCode(), mapping.isActive()))
                        .toList());
    }

    private PmsAdvancedResponse.GuestRegistrationView guestRegistrationView(GuestRegistration registration) {
        Reservation reservation = registration.getReservation();
        return new PmsAdvancedResponse.GuestRegistrationView(
                registration.getId(), reservation.getId(), reservation.getConfirmationCode(),
                guestName(reservation.getGuest()), registration.getStatus(), registration.getAddressLine(),
                registration.getPostalCode(), registration.getCity(), registration.getCountryCode(),
                registration.getNationalityCode(), registration.getDocumentLastFour(),
                registration.getVehiclePlate(), registration.getSignatureName(),
                registration.getPrivacyConsentAt(), registration.getCompletedAt(), registration.getCompletedBy());
    }

    private PmsAdvancedResponse.HotelResourceView hotelResourceView(HotelResource resource) {
        return new PmsAdvancedResponse.HotelResourceView(
                resource.getId(), resource.getType(), resource.getCode(), resource.getName(),
                resource.getLocation(), resource.getCapacity(), resource.getHourlyRate(),
                resource.getCurrencyCode(), resource.isActive());
    }

    private PmsAdvancedResponse.ResourceBookingView resourceBookingView(ResourceBooking booking) {
        return new PmsAdvancedResponse.ResourceBookingView(
                booking.getId(), booking.getResource().getId(), booking.getResource().getName(),
                booking.getGroupBooking() == null ? null : booking.getGroupBooking().getId(),
                booking.getGroupBooking() == null ? null : booking.getGroupBooking().getName(),
                booking.getTitle(), booking.getOrganizerName(), booking.getStartAt(), booking.getEndAt(),
                booking.getAttendees(), booking.getStatus(), booking.getTotalAmount(), booking.getNotes(),
                booking.getCreatedBy(), booking.getCreatedAt());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }

    private void applyOrganization(PmsOrganization organization, UpsertOrganizationRequest request) {
        organization.setType(request.type());
        organization.setName(required(request.name()));
        organization.setVatNumber(clean(request.vatNumber()));
        organization.setAddressLine1(clean(request.addressLine1()));
        organization.setPostalCode(clean(request.postalCode()));
        organization.setCity(clean(request.city()));
        organization.setCountryCode(country(request.countryCode()));
        organization.setEmail(clean(request.email()));
        organization.setPhone(clean(request.phone()));
        organization.setBillingEmail(clean(request.billingEmail()));
        organization.setPaymentTermsDays(request.paymentTermsDays());
        organization.setNotes(clean(request.notes()));
        organization.setActive(request.active());
    }

    private void applyTemplate(CommunicationTemplate template, UpsertCommunicationTemplateRequest request) {
        template.setCode(required(request.code()).toUpperCase(Locale.ROOT));
        template.setName(required(request.name()));
        template.setSubject(required(request.subject()));
        template.setBody(required(request.body()));
        template.setLanguageCode(clean(request.languageCode()) == null ? "de" : clean(request.languageCode()));
        template.setActive(request.active());
    }

    private String render(String value, HotelProperty property, GuestProfile guest, Reservation reservation) {
        return value.replace("{{guestName}}", guestName(guest)).replace("{{hotelName}}", property.getName())
                .replace("{{confirmationCode}}", reservation == null ? "" : reservation.getConfirmationCode())
                .replace("{{arrivalDate}}", reservation == null ? "" : reservation.getArrivalDate().toString())
                .replace("{{departureDate}}", reservation == null ? "" : reservation.getDepartureDate().toString());
    }

    private byte[] renderInvoice(PmsInvoice invoice, List<PmsInvoiceLine> lines) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, output);
            document.open();
            HotelProperty property = invoice.getProperty();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            document.add(new Paragraph(property.getLegalName() == null ? property.getName() : property.getLegalName(), title));
            document.add(new Paragraph(address(property.getAddressLine1(), property.getPostalCode(), property.getCity())));
            document.add(Chunk.NEWLINE);
            String documentLabel = invoice.getType() == InvoiceType.CREDIT_NOTE ? "Gutschrift" : "Rechnung";
            document.add(new Paragraph(documentLabel + " " + invoice.getInvoiceNumber(), title));
            if (invoice.getOriginalInvoice() != null) {
                document.add(new Paragraph("Korrektur zu Rechnung "
                        + invoice.getOriginalInvoice().getInvoiceNumber()));
            }
            document.add(new Paragraph("Dokumentdatum: " + invoice.getIssueDate() + "    Fällig: " + invoice.getDueDate()));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(invoice.getRecipientName(), heading));
            document.add(new Paragraph(address(invoice.getRecipientAddress(), invoice.getRecipientPostalCode(), invoice.getRecipientCity())));
            document.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(new float[]{5f, 1f, 1.7f, 1.7f});
            table.setWidthPercentage(100);
            addCell(table, "Leistung", true);
            addCell(table, "Menge", true);
            addCell(table, "Netto", true);
            addCell(table, "Brutto", true);
            for (PmsInvoiceLine line : lines) {
                addCell(table, line.getDescription(), false);
                addCell(table, line.getQuantity().stripTrailingZeros().toPlainString(), false);
                addCell(table, amount(line.getNetAmount(), invoice.getCurrencyCode()), false);
                addCell(table, amount(line.getGrossAmount(), invoice.getCurrencyCode()), false);
            }
            document.add(table);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Netto: " + amount(invoice.getNetAmount(), invoice.getCurrencyCode())));
            document.add(new Paragraph("MWST " + invoice.getVatRate().stripTrailingZeros().toPlainString()
                    + "%: " + amount(invoice.getVatAmount(), invoice.getCurrencyCode())));
            document.add(new Paragraph("Total: " + amount(invoice.getGrossAmount(), invoice.getCurrencyCode()), heading));
            if (invoice.getType() == InvoiceType.INVOICE && clean(invoice.getCreditorIban()) != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Swiss QR-Zahlteil", heading));
                Image qrImage = swissQrImage(invoice);
                qrImage.scaleAbsolute(145, 145);
                document.add(qrImage);
                document.add(new Paragraph("IBAN: " + invoice.getCreditorIban()));
                document.add(new Paragraph("Betrag: " + amount(invoice.getGrossAmount(), invoice.getCurrencyCode())));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Die Rechnung konnte nicht als PDF erstellt werden.", exception);
        }
    }

    String swissQrPayload(PmsInvoice invoice) {
        HotelProperty property = invoice.getProperty();
        String creditorName = clean(property.getLegalName()) == null ? property.getName() : property.getLegalName();
        StreetParts creditorAddress = splitStreet(property.getAddressLine1());
        StreetParts debtorAddress = splitStreet(invoice.getRecipientAddress());
        return String.join("\n",
                "SPC", "0200", "1", invoice.getCreditorIban(), "S", qr(creditorName),
                creditorAddress.street(), creditorAddress.buildingNumber(),
                qr(property.getPostalCode()), qr(property.getCity()), property.getCountryCode(),
                "", "", "", "", "", "", "",
                invoice.getGrossAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                invoice.getCurrencyCode(), "S", qr(invoice.getRecipientName()), debtorAddress.street(),
                debtorAddress.buildingNumber(), qr(invoice.getRecipientPostalCode()), qr(invoice.getRecipientCity()),
                invoice.getRecipientCountryCode(), clean(invoice.getQrReference()) == null ? "NON" : "SCOR",
                qr(invoice.getQrReference()), "Rechnung " + invoice.getInvoiceNumber(), "EPD", "", "", "");
    }

    private Image swissQrImage(PmsInvoice invoice) throws BadElementException, java.io.IOException {
        java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BarcodeQRCode qrCode = new BarcodeQRCode(swissQrPayload(invoice), 420, 420, hints);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                420, 420, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(qrCode.createAwtImage(java.awt.Color.BLACK, java.awt.Color.WHITE), 0, 0, null);
        int center = image.getWidth() / 2;
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(center - 40, center - 40, 80, 80);
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(center - 32, center - 32, 64, 64);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(center - 20, center - 7, 40, 14);
        graphics.fillRect(center - 7, center - 20, 14, 40);
        graphics.dispose();
        return Image.getInstance(image, null);
    }

    private void addCell(PdfPTable table, String value, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value,
                header ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)
                        : FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void validateQrPaymentData(String iban, String reference) {
        if (iban == null && reference != null) {
            throw badRequest("Eine QR-Referenz benötigt eine IBAN.");
        }
        if (iban != null && (!iban.matches("(CH|LI)[0-9A-Z]{13,32}") || !validMod97(iban))) {
            throw badRequest("Die IBAN ist ungültig.");
        }
        if (reference != null && (!reference.matches("RF[0-9A-Z]{2,23}") || !validMod97(reference))) {
            throw badRequest("Als QR-Referenz wird eine ISO-Creditor-Referenz (RF…) erwartet.");
        }
    }

    private boolean validMod97(String value) {
        String rearranged = value.substring(4) + value.substring(0, 4);
        int remainder = 0;
        for (char character : rearranged.toCharArray()) {
            String digits = Character.isDigit(character)
                    ? Character.toString(character)
                    : Integer.toString(Character.toUpperCase(character) - 'A' + 10);
            for (char digit : digits.toCharArray()) {
                remainder = (remainder * 10 + Character.digit(digit, 10)) % 97;
            }
        }
        return remainder == 1;
    }

    private StreetParts splitStreet(String value) {
        String address = qr(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(.*?)(?:\\s+)([0-9][0-9A-Za-z./-]*)$")
                .matcher(address);
        return matcher.matches()
                ? new StreetParts(matcher.group(1), matcher.group(2))
                : new StreetParts(address, "");
    }

    private BigDecimal folioBalance(Folio folio) {
        BigDecimal charges = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId()).stream()
                .map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payments = paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(folio.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.POSTED)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(charges.subtract(payments));
    }

    private void emit(HotelProperty property, String eventType, String aggregateType, String aggregateId, String payload) {
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setProperty(property);
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        event.setStatus(OutboxStatus.PENDING);
        outboxRepository.save(event);
        auditWriter.append(property, eventType, aggregateType, aggregateId, payload);
    }

    private boolean isValidSecretReference(String reference) {
        return reference != null && reference.matches("env:[A-Z][A-Z0-9_]{2,100}");
    }

    private HotelProperty requireProperty(Company company, Long propertyId) {
        return propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotelbetrieb nicht gefunden."));
    }

    private HotelProperty lockProperty(Company company, Long propertyId) {
        return propertyRepository.findByIdAndCompany_IdForUpdate(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotelbetrieb nicht gefunden."));
    }

    private Reservation requireReservation(Company company, Long reservationId) {
        return reservationRepository.findByIdAndProperty_Company_Id(reservationId, company.getId())
                .orElseThrow(() -> notFound("Reservierung nicht gefunden."));
    }

    private GuestProfile requireGuest(Company company, Long guestId) {
        return guestRepository.findByIdAndCompany_Id(guestId, company.getId())
                .orElseThrow(() -> notFound("Gast nicht gefunden."));
    }

    private PmsOrganization requireOrganization(Company company, Long organizationId) {
        return organizationRepository.findByIdAndCompany_Id(organizationId, company.getId())
                .orElseThrow(() -> notFound("Firma oder Reisebüro nicht gefunden."));
    }

    private Folio requireFolio(Company company, Long folioId) {
        return folioRepository.findByIdAndReservation_Property_Company_Id(folioId, company.getId())
                .orElseThrow(() -> notFound("Folio nicht gefunden."));
    }

    private LocalDate effectiveDate(HotelProperty property, LocalDate date) {
        return date == null ? LocalDate.now(ZoneId.of(property.getTimezone())) : date;
    }

    private void validateStay(LocalDate arrival, LocalDate departure) {
        if (arrival == null || departure == null || !departure.isAfter(arrival)) {
            throw badRequest("Das Abreisedatum muss nach dem Anreisedatum liegen.");
        }
    }

    private String guestName(GuestProfile guest) {
        return guest.getFirstName() + " " + guest.getLastName();
    }

    private String normalizeIban(String iban) {
        return clean(iban) == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String country(String value) {
        return clean(value) == null ? "CH" : clean(value).toUpperCase(Locale.ROOT);
    }

    private String address(String line, String postalCode, String city) {
        String first = clean(line) == null ? "" : clean(line);
        String second = ((clean(postalCode) == null ? "" : clean(postalCode) + " ")
                + (clean(city) == null ? "" : clean(city))).trim();
        return second.isEmpty() ? first : first + (first.isEmpty() ? "" : "\n") + second;
    }

    private String amount(BigDecimal value, String currency) {
        return currency + " " + money(value).toPlainString();
    }

    private String qr(String value) {
        return clean(value) == null ? "" : clean(value);
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String required(String value) {
        String result = clean(value);
        if (result == null) {
            throw badRequest("Ein Pflichtfeld ist leer.");
        }
        return result;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record StreetParts(String street, String buildingNumber) {
    }
}
