package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsEventOfferDtos.*;
import com.chrono.chrono.dto.pms.PmsEventOrderDto;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
@Service @RequiredArgsConstructor @Transactional
public class PmsEventOfferService {
 private final PmsEventOfferRepository offers;private final ResourceBookingRepository bookings;private final HotelPropertyRepository properties;
 private final PmsPropertyAccessService access;private final PmsEventOrderService orders;private final PmsAuditWriter audit;private final ObjectMapper json;private final EntityManager em;
 @Transactional(readOnly=true) public List<View> list(String user,Long propertyId,Long bookingId){booking(user,propertyId,bookingId,false);return offers.findTop100ByBooking_IdOrderByOfferVersionDesc(bookingId).stream().map(this::view).toList();}
 public View create(String user,Long propertyId,Long bookingId,Create input) {
  ResourceBooking booking=booking(user,propertyId,bookingId,true);LocalDateTime now=now(booking);
  if(input==null||input.validUntil()==null||!input.validUntil().isAfter(now)||input.validUntil().isAfter(now.plusDays(365))||input.validUntil().isAfter(booking.getStartAt())||input.terms()==null||input.terms().isBlank()||input.terms().length()>16000)throw bad("Gültigkeit vor Veranstaltungsbeginn (höchstens ein Jahr) und Angebotsbedingungen bis 16.000 Zeichen erforderlich.");
  var order=orders.get(booking.getProperty().getCompany(),propertyId,bookingId);
  if(order.lines().isEmpty()||!"DRAFT".equals(order.status()))throw conflict("Ein gespeicherter, noch nicht verbuchter Veranstaltungsauftrag ist erforderlich.");
  if(booking.getStatus()==ResourceBookingStatus.CANCELLED)throw conflict("Die Veranstaltung wurde storniert.");
  var previous=offers.findFirstByBooking_IdOrderByOfferVersionDesc(bookingId).orElse(null);
  if(previous!=null&&"OPEN".equals(previous.getStatus()))previous.setStatus("SUPERSEDED");
  PmsEventOffer offer=new PmsEventOffer();offer.setBooking(booking);offer.setResourceId(booking.getResource().getId());offer.setOfferVersion(previous==null?1:previous.getOfferVersion()+1);offer.setTerms(input.terms().trim());offer.setValidUntil(input.validUntil());offer.setCreatedAt(now);offer.setCreatedBy(user);offer.setSnapshotJson(encode(order));
  offer.setContentHash(hash(offer.getSnapshotJson()+"\n"+offer.getTerms()+"\n"+offer.getValidUntil()));offers.saveAndFlush(offer);
  audit.append(booking.getProperty(),"event_offer.created","event_offer",offer.getId().toString(),"{\"version\":"+offer.getOfferVersion()+",\"contentHash\":\""+offer.getContentHash()+"\"}");return view(offer);
 }
 public Link link(String user,Long propertyId,Long bookingId,Long offerId){ResourceBooking booking=booking(user,propertyId,bookingId,true);PmsEventOffer offer=require(booking,offerId);requireOpen(offer);byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);offer.setTokenHash(hash(token));audit.append(booking.getProperty(),"event_offer.link_replaced","event_offer",offerId.toString(),"{}");return new Link(offerId,token,offer.getValidUntil());}
 @Transactional(readOnly=true) public View publicView(String token){return view(token(token));}
 public View decide(String token,Decision input) {
  PmsEventOffer candidate=token(token);ResourceBooking booking=candidate.getBooking();properties.findByIdAndCompany_IdForUpdate(booking.getProperty().getId(),booking.getProperty().getCompany().getId()).orElseThrow();
  em.refresh(candidate);em.refresh(booking);if(!Objects.equals(candidate.getTokenHash(),hash(token)))throw gone();requireOpen(candidate);
  if(input==null||!Set.of("ACCEPTED","DECLINED").contains(input.decision())||input.signatureName()==null||input.signatureName().isBlank()||input.signatureName().length()>180||input.note()!=null&&input.note().length()>1000||"ACCEPTED".equals(input.decision())&&!input.consent())throw bad("Entscheidung, vollständiger Name und bei Annahme die ausdrückliche Zustimmung erforderlich.");
  if(booking.getStatus()==ResourceBookingStatus.CANCELLED)throw conflict("Die Veranstaltung wurde storniert.");
  candidate.setStatus(input.decision());candidate.setSignatureName(input.signatureName().trim());candidate.setDecisionNote(input.note());candidate.setDecidedAt(now(booking));
  candidate.setAcceptanceHash(hash(candidate.getContentHash()+"\n"+candidate.getStatus()+"\n"+candidate.getSignatureName()+"\n"+candidate.getDecidedAt()+"\n"+input.consent()));
  audit.append(booking.getProperty(),"event_offer.decided","event_offer",candidate.getId().toString(),"{\"decision\":\""+candidate.getStatus()+"\",\"acceptanceHash\":\""+candidate.getAcceptanceHash()+"\"}");return view(candidate);
 }
 public View apply(String user,Long propertyId,Long bookingId,Long offerId) {
  ResourceBooking booking=booking(user,propertyId,bookingId,true);PmsEventOffer offer=require(booking,offerId);
  if(!"ACCEPTED".equals(offer.getStatus()))throw conflict("Nur ein angenommenes Angebot kann in den Bankettauftrag übernommen werden.");
  if(offer.getAppliedAt()!=null)return view(offer);
  var latestAccepted=offers.findFirstByBooking_IdAndStatusOrderByOfferVersionDesc(bookingId,"ACCEPTED").orElseThrow();if(!latestAccepted.getId().equals(offerId))throw conflict("Eine neuere Angebotsversion wurde bereits angenommen.");
  var frozen=decode(offer);if(!Objects.equals(offer.getResourceId(),booking.getResource().getId())||!Objects.equals(frozen.startAt(),booking.getStartAt())||!Objects.equals(frozen.endAt(),booking.getEndAt())||!Objects.equals(frozen.resourceName(),booking.getResource().getName())||!Objects.equals(frozen.currencyCode(),booking.getResource().getCurrencyCode()))throw conflict("Zeit, Ressource oder Währung haben sich seit dem Angebot geändert. Ein neues Angebot erstellen.");
  orders.save(booking.getProperty().getCompany(),propertyId,bookingId,new PmsEventOrderDto.Save(frozen.setupMinutes(),frozen.teardownMinutes(),frozen.agenda(),frozen.setupInstructions(),frozen.cateringNotes(),frozen.lines().stream().map(l->new PmsEventOrderDto.Line(l.description(),l.quantity(),l.netUnitPrice(),l.taxRate(),l.type())).toList()));
  offer.setAppliedAt(now(booking));booking.setStatus(ResourceBookingStatus.CONFIRMED);audit.append(booking.getProperty(),"event_offer.applied","event_offer",offerId.toString(),"{}");return view(offer);
 }
 /** Prevent changed BEO lines from being charged under an earlier accepted contract. */
 public void assertAcceptedOrderMatches(Long bookingId,PmsEventOrderDto.View current) {
  var accepted=offers.findFirstByBooking_IdAndStatusOrderByOfferVersionDesc(bookingId,"ACCEPTED").orElse(null);if(accepted==null)return;
  if(accepted.getAppliedAt()==null||!Objects.equals(accepted.getResourceId(),accepted.getBooking().getResource().getId())||!economicSnapshot(decode(accepted)).equals(economicSnapshot(current)))throw conflict("Der Bankettauftrag weicht vom angenommenen Angebot ab. Angenommene Version übernehmen oder neues Änderungsangebot annehmen lassen.");
 }
 private String economicSnapshot(PmsEventOrderDto.View value){return encode(List.of(value.title(),value.resourceName(),value.organizerName(),value.startAt(),value.endAt(),value.attendees(),value.currencyCode(),value.setupMinutes(),value.teardownMinutes(),Objects.toString(value.agenda(),""),Objects.toString(value.setupInstructions(),""),Objects.toString(value.cateringNotes(),""),value.lines().stream().map(l->List.of(l.description(),l.quantity().stripTrailingZeros().toPlainString(),l.netUnitPrice().stripTrailingZeros().toPlainString(),l.taxRate().stripTrailingZeros().toPlainString(),l.type())).toList()));}
 private ResourceBooking booking(String user,Long id,Long bookingId,boolean write){var actor=access.access(user);access.require(actor,id,"FRONT_DESK",write);if(write)properties.findByIdAndCompany_IdForUpdate(id,actor.companyId()).orElseThrow(()->gone());return bookings.findByIdAndProperty_Company_Id(bookingId,actor.companyId()).filter(b->b.getProperty().getId().equals(id)).orElseThrow(()->gone());}
 private PmsEventOffer require(ResourceBooking b,Long id){return offers.findById(id).filter(o->o.getBooking().getId().equals(b.getId())).orElseThrow(()->gone());}
 private PmsEventOffer token(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw gone();PmsEventOffer offer=offers.findByTokenHash(hash(token)).orElseThrow(()->gone());if(offer.getValidUntil().isBefore(now(offer.getBooking()))&&"OPEN".equals(offer.getStatus()))throw gone();return offer;}
 private void requireOpen(PmsEventOffer offer){if(!"OPEN".equals(offer.getStatus())||!offer.getValidUntil().isAfter(now(offer.getBooking())))throw conflict("Dieses Angebot ist abgelaufen, ersetzt oder bereits beantwortet.");}
 private View view(PmsEventOffer offer){String status="OPEN".equals(offer.getStatus())&&!offer.getValidUntil().isAfter(now(offer.getBooking()))?"EXPIRED":offer.getStatus();return new View(offer.getId(),offer.getOfferVersion(),status,offer.getTerms(),offer.getValidUntil(),offer.getCreatedAt(),offer.getCreatedBy(),offer.getContentHash(),offer.getDecidedAt(),offer.getSignatureName(),offer.getDecisionNote(),offer.getAcceptanceHash(),offer.getAppliedAt(),decode(offer));}
 private PmsEventOrderDto.View decode(PmsEventOffer offer){try{return json.readValue(offer.getSnapshotJson(),PmsEventOrderDto.View.class);}catch(Exception failure){throw new IllegalStateException(failure);}}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception failure){throw new IllegalStateException(failure);}}
 private LocalDateTime now(ResourceBooking b){return LocalDateTime.now(ZoneId.of(b.getProperty().getTimezone()));}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
 private ResponseStatusException gone(){return new ResponseStatusException(HttpStatus.GONE,"Angebot nicht verfügbar oder Link abgelaufen.");}
}
