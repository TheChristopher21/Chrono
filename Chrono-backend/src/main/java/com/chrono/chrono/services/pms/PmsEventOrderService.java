package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsEventOrderDto;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class PmsEventOrderService {
    private final HotelPropertyRepository properties;
    private final HotelResourceRepository resources;
    private final ResourceBookingRepository bookings;
    private final PmsEventOrderRepository orders;
    private final PmsEventOrderLineRepository lines;
    private final FolioRepository folios;
    private final FolioItemRepository items;
    private final PmsFinancialPeriodService periods;
    private final PmsAuditWriter audit;
    @org.springframework.beans.factory.annotation.Value("${app.pms.invoice.font-path:}")
    private String fontPath;
    private org.springframework.beans.factory.ObjectProvider<PmsEventOfferService> offerService;
    private PmsEventOfferRepository offerRepository;
    @org.springframework.beans.factory.annotation.Autowired
    public void setOfferService(org.springframework.beans.factory.ObjectProvider<PmsEventOfferService> provider){this.offerService=provider;}
    @org.springframework.beans.factory.annotation.Autowired
    public void setOfferRepository(PmsEventOfferRepository repository){this.offerRepository=repository;}

    @Transactional(readOnly=true)
    public PmsEventOrderDto.View get(Company company,Long propertyId,Long bookingId) {
        ResourceBooking booking=booking(company,propertyId,bookingId,false);
        return view(booking,orders.findByResourceBooking_Id(bookingId).orElse(null));
    }

    public PmsEventOrderDto.View save(Company company,Long propertyId,Long bookingId,PmsEventOrderDto.Save request) {
        ResourceBooking booking=booking(company,propertyId,bookingId,true);
        if(booking.getStatus()==ResourceBookingStatus.CANCELLED) throw conflict("Die Veranstaltung ist storniert.");
        PmsEventOrder order=orders.findByResourceBooking_Id(bookingId).orElseGet(PmsEventOrder::new);
        if(order.getPostedAt()!=null) throw conflict("Der verbuchte Veranstaltungsauftrag ist unveränderlich. Leistungen über die Rechnungskorrektur berichtigen.");
        if(request.setupMinutes()<0 || request.setupMinutes()>1440 || request.teardownMinutes()<0 || request.teardownMinutes()>1440
                || request.lines()==null || request.lines().isEmpty() || request.lines().size()>500) throw bad("Bitte Aufbau, Abbau und 1 bis 500 Leistungen angeben.");
        LocalDateTime from=booking.getStartAt().minusMinutes(request.setupMinutes()),to=booking.getEndAt().plusMinutes(request.teardownMinutes());
        if(bookings.countBufferedOverlap(booking.getResource().getId(),bookingId,from,to)>0)
            throw conflict("Aufbau oder Abbau überschneiden sich mit einer weiteren Ressourcenbuchung.");
        String currency=booking.getResource().getCurrencyCode();
        order.setResourceBooking(booking);order.setSetupMinutes(request.setupMinutes());order.setTeardownMinutes(request.teardownMinutes());
        order.setAgenda(request.agenda());order.setSetupInstructions(request.setupInstructions());order.setCateringNotes(request.cateringNotes());
        orders.save(order);
        if(order.getId()!=null) lines.deleteAll(lines.findAllByEventOrder_IdOrderByIdAsc(order.getId()));
        BigDecimal gross=BigDecimal.ZERO;
        for(var input:request.lines()) {
            if(input.description()==null || input.description().isBlank() || input.description().length()>240 || input.type()==null
                    || input.quantity()==null || input.quantity().signum()<=0 || input.quantity().scale()>2
                    || input.taxRate()==null || input.taxRate().signum()<0 || input.taxRate().compareTo(new BigDecimal("100"))>0)
                throw bad("Jede Leistung benötigt Beschreibung, Menge, Leistungsart und einen gültigen Steuersatz.");
            BigDecimal unit=PmsMoney.require(input.netUnitPrice(),currency);
            if(unit.signum()<0) throw bad("Der Nettopreis darf nicht negativ sein.");
            PmsEventOrderLine line=new PmsEventOrderLine();line.setEventOrder(order);line.setDescription(input.description().trim());line.setType(input.type());
            line.setQuantity(input.quantity());line.setNetUnitPrice(unit);line.setTaxRate(input.taxRate());
            BigDecimal net=PmsMoney.round(unit.multiply(input.quantity()),currency);
            BigDecimal tax=PmsMoney.round(net.multiply(input.taxRate()).movePointLeft(2),currency);
            line.setNetAmount(net);line.setTaxAmount(tax);line.setGrossAmount(net.add(tax));lines.save(line);gross=gross.add(line.getGrossAmount());
        }
        booking.setOccupiedFrom(from);booking.setOccupiedUntil(to);booking.setTotalAmount(gross);
        audit.append(booking.getProperty(),"event_order.saved","resource_booking",bookingId.toString(),"{\"lineCount\":"+request.lines().size()+"}");
        return view(booking,order);
    }

    public PmsEventOrderDto.View post(Company company,Long propertyId,Long bookingId,PmsEventOrderDto.Post request,String username) {
        ResourceBooking booking=booking(company,propertyId,bookingId,true);
        PmsEventOrder order=orders.findByResourceBooking_Id(bookingId).orElseThrow(()->conflict("Zuerst den Veranstaltungsauftrag mit Leistungen speichern."));
        if(order.getPostedAt()!=null) {
            if(request.folioId()!=null && !request.folioId().equals(order.getPostedFolio().getId())) throw conflict("Die Veranstaltung wurde bereits auf ein anderes Konto gebucht.");
            return view(booking,order);
        }
        if(booking.getStatus()!=ResourceBookingStatus.CONFIRMED) throw conflict("Nur bestätigte Veranstaltungen können verbucht werden.");
        if(offerService!=null && offerRepository.findFirstByBooking_IdAndStatusOrderByOfferVersionDesc(bookingId,"ACCEPTED").isPresent())
            offerService.getObject().assertAcceptedOrderMatches(bookingId,view(booking,order));
        Folio target=request.folioId()==null ? booking.getGroupBooking()==null?null:folios.findByGroupBooking_IdAndGroupMasterTrue(booking.getGroupBooking().getId()).orElse(null)
                : folios.findByIdAndReservation_Property_Company_Id(request.folioId(),company.getId()).orElse(null);
        if(target==null || !target.getReservation().getProperty().getId().equals(propertyId)) throw bad("Bitte ein Gastkonto oder ein eingerichtetes Gruppenhauptkonto wählen.");
        if(target.getStatus()!=FolioStatus.OPEN) throw conflict("Das Zielkonto ist geschlossen.");
        if(!target.getCurrencyCode().equals(booking.getResource().getCurrencyCode())) throw conflict("Veranstaltung und Gastkonto benötigen dieselbe Währung.");
        if(booking.getGroupBooking()!=null && (target.getReservation().getGroupBooking()==null
                || !target.getReservation().getGroupBooking().getId().equals(booking.getGroupBooking().getId()))) throw conflict("Das Gastkonto gehört nicht zur Veranstaltungsgruppe.");
        periods.assertPostingOpen(booking.getProperty(),booking.getStartAt().toLocalDate());
        for(PmsEventOrderLine source:lines.findAllByEventOrder_IdOrderByIdAsc(order.getId())) {
            FolioItem item=new FolioItem();item.setFolio(target);item.setServiceDate(booking.getStartAt().toLocalDate());item.setType(source.getType());
            String description=booking.getTitle()+" · "+source.getDescription();item.setDescription(description.substring(0,Math.min(240,description.length())));
            item.setQuantity(source.getQuantity());item.setUnitPrice(PmsMoney.round(source.getGrossAmount().divide(source.getQuantity(),8,RoundingMode.HALF_UP),target.getCurrencyCode()));
            item.setTotalAmount(source.getGrossAmount());item.setTaxRate(source.getTaxRate());item.setTaxIncluded(true);items.save(item);source.setFolioItem(item);
        }
        order.setPostedFolio(target);order.setPostedAt(LocalDateTime.now());order.setPostedBy(username);
        audit.append(booking.getProperty(),"event_order.posted","resource_booking",bookingId.toString(),"{\"folioId\":"+target.getId()+"}");
        return view(booking,order);
    }

    @Transactional(readOnly=true)
    public byte[] beoPdf(Company company,Long propertyId,Long bookingId) {
        var value=get(company,propertyId,bookingId);
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            Document document=new Document(PageSize.A4,36,36,40,40);PdfWriter.getInstance(document,output);document.open();
            BaseFont base=fontPath==null || fontPath.isBlank()?BaseFont.createFont(BaseFont.HELVETICA,BaseFont.CP1252,BaseFont.NOT_EMBEDDED)
                    :BaseFont.createFont(fontPath,BaseFont.IDENTITY_H,BaseFont.EMBEDDED);
            if(value.toString().codePoints().anyMatch(c->!Character.isISOControl(c) && !base.charExists(c)))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Für diese Zeichen eine passende Unicode-Schrift unter app.pms.invoice.font-path konfigurieren.");
            Font text=new Font(base,10),title=new Font(base,18,Font.BOLD);
            document.add(new Paragraph("Veranstaltungsauftrag / BEO",title));
            document.add(new Paragraph(value.title(),new Font(base,14,Font.BOLD)));
            document.add(new Paragraph("Organisation: "+value.organizerName()+"\nRessource: "+value.resourceName()+"\nGäste: "+value.attendees()
                    +"\nVeranstaltung: "+value.startAt()+" bis "+value.endAt()+"\nBelegt inkl. Aufbau/Abbau: "+value.occupiedFrom()+" bis "+value.occupiedUntil()
                    +"\nStatus: "+value.status(),text));
            section(document,"Ablauf",value.agenda(),text);section(document,"Aufbau und Technik",value.setupInstructions(),text);section(document,"Bewirtung",value.cateringNotes(),text);
            PdfPTable table=new PdfPTable(new float[]{5,1,2,1.5f,2});table.setWidthPercentage(100);table.setSpacingBefore(16);
            for(String heading:List.of("Leistung","Menge","Netto/Einheit","Steuer %","Brutto")) table.addCell(new Phrase(heading,text));
            table.setHeaderRows(1);
            for(var line:value.lines()) for(String cell:List.of(line.description(),line.quantity().stripTrailingZeros().toPlainString(),line.netUnitPrice().toPlainString(),line.taxRate().stripTrailingZeros().toPlainString(),line.grossAmount().toPlainString())) table.addCell(new Phrase(cell,text));
            document.add(table);document.add(new Paragraph("Netto: "+value.netAmount()+" / Steuer: "+value.taxAmount()+" / Gesamt: "+value.grossAmount()+" "+value.currencyCode(),text));
            document.close();return output.toByteArray();
        } catch(ResponseStatusException exception) { throw exception; }
        catch(Exception exception) { throw new IllegalStateException("Veranstaltungsauftrag konnte nicht gedruckt werden.",exception); }
    }
    private void section(Document document,String heading,String value,Font font) throws DocumentException {
        if(value==null || value.isBlank()) return;document.add(new Paragraph("\n"+heading,font));document.add(new Paragraph(value,font));
    }
    private ResourceBooking booking(Company company,Long propertyId,Long bookingId,boolean lock) {
        if(lock) properties.findByIdAndCompany_IdForUpdate(propertyId,company.getId()).orElseThrow(()->missing());
        ResourceBooking booking=bookings.findByIdAndProperty_Company_Id(bookingId,company.getId())
                .filter(b->b.getProperty().getId().equals(propertyId)).orElseThrow(()->missing());
        if(lock) resources.findByIdAndProperty_Company_IdForUpdate(booking.getResource().getId(),company.getId()).orElseThrow(()->missing());
        return booking;
    }
    private PmsEventOrderDto.View view(ResourceBooking b,PmsEventOrder order) {
        List<PmsEventOrderLine> values=order==null?List.of():lines.findAllByEventOrder_IdOrderByIdAsc(order.getId());
        return new PmsEventOrderDto.View(b.getId(),b.getTitle(),b.getResource().getName(),b.getOrganizerName(),b.getStartAt(),b.getEndAt(),b.getOccupiedFrom(),b.getOccupiedUntil(),b.getAttendees(),b.getResource().getCurrencyCode(),
                b.getGroupBooking()==null?null:b.getGroupBooking().getId(),order!=null && order.getPostedAt()!=null?"POSTED":"DRAFT",
                order==null || order.getPostedFolio()==null?null:order.getPostedFolio().getId(),order==null?null:order.getPostedAt(),order==null?0:order.getSetupMinutes(),order==null?0:order.getTeardownMinutes(),
                order==null?null:order.getAgenda(),order==null?null:order.getSetupInstructions(),order==null?null:order.getCateringNotes(),
                values.stream().map(PmsEventOrderLine::getNetAmount).reduce(BigDecimal.ZERO,BigDecimal::add),values.stream().map(PmsEventOrderLine::getTaxAmount).reduce(BigDecimal.ZERO,BigDecimal::add),values.stream().map(PmsEventOrderLine::getGrossAmount).reduce(BigDecimal.ZERO,BigDecimal::add),
                values.stream().map(l->new PmsEventOrderDto.LineView(l.getId(),l.getDescription(),l.getQuantity(),l.getNetUnitPrice(),l.getTaxRate(),l.getType(),l.getNetAmount(),l.getTaxAmount(),l.getGrossAmount())).toList());
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Veranstaltung nicht gefunden.");}
}
