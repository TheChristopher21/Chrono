package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRateInheritanceDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.BeanWrapperImpl;
import java.math.BigDecimal;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class PmsRateInheritanceService {
 private static final List<String> POLICY=List.of("minStay","maxStay","refundable","validFrom","validTo","bookingFrom","bookingTo","minAdvanceDays","maxAdvanceDays","includedAdults","cancellationDeadlineHours","cancellationFeePercent","depositPercent","paymentDueDays","noShowFeePercent","depositDueDaysBeforeArrival","cancellationPolicy","paymentPolicy");
 private final PmsRateInheritanceRepository rules;private final RatePlanRepository rates;private final HotelPropertyRepository properties;private final PmsPropertyAccessService access;private final PmsAuditWriter audit;private final EntityManager em;
 @Transactional(readOnly=true) public List<View> list(String user,Long propertyId){var actor=access.access(user);access.require(actor,propertyId,"RATES",false);return rules.findByTarget_Property_IdOrderByIdAsc(propertyId).stream().map(this::view).toList();}
 public View save(String user,Long propertyId,Input input) {
  var actor=access.requireMaster(user);
  if(input==null||input.sourceRatePlanId()==null||input.targetRatePlanId()==null||input.sourceRatePlanId().equals(input.targetRatePlanId()))throw bad("Unterschiedliche Eltern- und Zielrate auswählen.");
  RatePlan source=rate(actor.companyId(),input.sourceRatePlanId()),target=rate(actor.companyId(),input.targetRatePlanId());if(!target.getProperty().getId().equals(propertyId))throw bad("Zielrate gehört nicht zum Hotel.");
  lock(source,target);em.refresh(source);em.refresh(target);
  validate(input,source,target);assertAcyclic(source.getId(),target.getId());
  PmsRateInheritance row=rules.findByTarget_Id(target.getId()).orElse(null);
  if(row!=null&&!Objects.equals(input.expectedVersion(),row.getVersion()))throw conflict("Die Vererbungsregel wurde inzwischen geändert.");
  if(row==null){row=new PmsRateInheritance();row.setTarget(target);}row.setSource(source);row.setEnabled(input.enabled());row.setFrozen(input.frozen());row.setInheritPolicy(input.inheritPolicy());row.setManualFxRate(input.manualFxRate());row.setFxDate(input.fxDate());row.setFxValidUntil(input.fxValidUntil());row.setFxReference(input.fxReference().trim());row.setAdjustmentPercent(input.adjustmentPercent());row.setMinPrice(PmsMoney.require(input.minPrice(),target.getCurrencyCode()));row.setMaxPrice(PmsMoney.require(input.maxPrice(),target.getCurrencyCode()));row.setLastSourceHash(null);row.setLastResult("Regel gespeichert; noch nicht angewendet.");rules.saveAndFlush(row);
  audit.append(target.getProperty(),"rate_inheritance.configured","rate_plan",target.getId().toString(),"{\"source\":"+source.getId()+",\"enabled\":"+row.isEnabled()+",\"frozen\":"+row.isFrozen()+",\"fx\":"+row.getManualFxRate()+"}");return view(row);
 }
 public View freeze(String user,Long propertyId,Long id,boolean frozen,Long expectedVersion){var actor=access.access(user);access.require(actor,propertyId,"RATES",true);PmsRateInheritance row=require(actor.companyId(),propertyId,id);lock(row.getSource(),row.getTarget());em.refresh(row);if(expectedVersion==null||row.getVersion()!=expectedVersion)throw conflict("Die Regel wurde inzwischen geändert.");row.setFrozen(frozen);if(!frozen)row.setLastSourceHash(null);audit.append(row.getTarget().getProperty(),"rate_inheritance.freeze_changed","rate_plan",row.getTarget().getId().toString(),"{\"frozen\":"+frozen+"}");em.flush();return view(row);}
 public View apply(String user,Long propertyId,Long id){var actor=access.requireMaster(user);PmsRateInheritance row=require(actor.companyId(),propertyId,id);applyLocked(row,false);return view(row);}
 public void scheduled(Long id){PmsRateInheritance row=rules.findById(id).orElse(null);if(row!=null&&row.isEnabled()&&!row.isFrozen())applyLocked(row,true);}
 /** Called under the existing property write lock when a staff member edits a local rate. */
 public void freezeOnManualEdit(Long rateId){rules.findByTarget_Id(rateId).ifPresent(row->{if(!row.isFrozen()){row.setFrozen(true);row.setLastResult("Automatisch eingefroren nach manueller Änderung der Zielrate.");audit.append(row.getTarget().getProperty(),"rate_inheritance.frozen_by_local_edit","rate_plan",rateId.toString(),"{}");}});}
 private void applyLocked(PmsRateInheritance row,boolean automatic) {
  lock(row.getSource(),row.getTarget());em.refresh(row);em.refresh(row.getSource());em.refresh(row.getTarget());
  if(row.isFrozen())throw conflict("Die Zielrate ist als lokale Ausnahme eingefroren.");
  if(automatic&&!row.isEnabled())return;
  RatePlan source=row.getSource(),target=row.getTarget();LocalDate today=LocalDate.now(ZoneId.of(target.getProperty().getTimezone()));
  if(!source.getCurrencyCode().equals(target.getCurrencyCode())&&(today.isBefore(row.getFxDate())||today.isAfter(row.getFxValidUntil()))){row.setLastResult("Nicht angewendet: manueller Wechselkurs ist noch nicht gültig oder abgelaufen.");if(!automatic)throw conflict(row.getLastResult());return;}
  if(source.isTaxIncluded()!=target.isTaxIncluded())throw conflict("Eltern- und Zielrate müssen dieselbe Netto-/Brutto-Preisbasis verwenden.");
  if(target.getVatRate()==null)throw conflict("Der lokale Steuersatz der Zielrate fehlt.");
  String hash=sourceHash(row);if(automatic&&hash.equals(row.getLastSourceHash()))return;
  BigDecimal old=target.getNightlyRate(),price=converted(source.getNightlyRate(),row,true),breakfast=converted(source.getBreakfastAmount(),row,false);
  if(breakfast.compareTo(price)>0)throw conflict("Der vererbte Frühstücksanteil übersteigt den begrenzten Nachtpreis.");
  if(source.isBreakfastIncluded()&&target.getBreakfastVatRate()==null)throw conflict("Der lokale Frühstückssteuersatz fehlt.");
  if(row.isInheritPolicy()&&(positive(source.getCancellationFeePercent())||positive(source.getNoShowFeePercent()))&&target.getPolicyFeeTaxRate()==null)throw conflict("Der lokale Gebührensatz fehlt.");
  target.setNightlyRate(price);target.setBreakfastAmount(breakfast);target.setBreakfastIncluded(source.isBreakfastIncluded());target.setExtraAdultRate(converted(source.getExtraAdultRate(),row,false));target.setChildRate(converted(source.getChildRate(),row,false));
  if(row.isInheritPolicy()){var from=new BeanWrapperImpl(source);var to=new BeanWrapperImpl(target);for(String name:POLICY)to.setPropertyValue(name,from.getPropertyValue(name));}
  row.setLastSourceHash(hash);row.setLastAppliedAt(LocalDateTime.now(ZoneId.of(target.getProperty().getTimezone())));row.setLastResult("Übernommen; lokale Steuersätze, Zimmerkategorie und akzeptierte Reservierungen bleiben erhalten.");
  audit.append(target.getProperty(),"rate_inheritance.applied","rate_plan",target.getId().toString(),"{\"source\":"+source.getId()+",\"oldPrice\":"+old+",\"newPrice\":"+price+",\"fx\":"+row.getManualFxRate()+",\"automatic\":"+automatic+"}");
 }
 private void validate(Input in,RatePlan source,RatePlan target){LocalDate today=LocalDate.now(ZoneId.of(target.getProperty().getTimezone()));
  if(in.manualFxRate()==null||in.manualFxRate().signum()<=0||in.manualFxRate().scale()>8||in.fxDate()==null||in.fxValidUntil()==null||in.fxDate().isAfter(today)||in.fxValidUntil().isBefore(today)||in.fxValidUntil().isAfter(in.fxDate().plusDays(365))||in.fxReference()==null||in.fxReference().isBlank()||in.fxReference().length()>240||in.adjustmentPercent()==null||in.adjustmentPercent().compareTo(new BigDecimal("-90"))<0||in.adjustmentPercent().compareTo(new BigDecimal("200"))>0||in.minPrice()==null||in.minPrice().signum()<0||in.maxPrice()==null||in.maxPrice().compareTo(in.minPrice())<0)throw bad("Expliziten positiven Wechselkurs mit Datum, begrenzter Gültigkeit und Quelle sowie gültige Preisgrenzen angeben.");
  if(source.getCurrencyCode().equals(target.getCurrencyCode())&&in.manualFxRate().compareTo(BigDecimal.ONE)!=0)throw bad("Bei gleicher Währung muss der Wechselkurs 1 sein.");
 }
 private void assertAcyclic(Long source,Long target){Set<Long> seen=new HashSet<>();Long next=source;for(int depth=0;next!=null;depth++){if(depth>=20||next.equals(target)||!seen.add(next))throw conflict("Ratenvererbung darf keinen Kreis bilden und höchstens 20 Ebenen enthalten.");var parent=rules.findByTarget_Id(next).orElse(null);next=parent==null?null:parent.getSource().getId();}}
 private BigDecimal converted(BigDecimal amount,PmsRateInheritance rule,boolean capped){BigDecimal value=amount.multiply(rule.getManualFxRate()).multiply(BigDecimal.ONE.add(rule.getAdjustmentPercent().movePointLeft(2)));if(capped)value=value.max(rule.getMinPrice()).min(rule.getMaxPrice());return PmsMoney.round(value,rule.getTarget().getCurrencyCode());}
 private String sourceHash(PmsRateInheritance row){var wrapper=new BeanWrapperImpl(row.getSource());StringBuilder state=new StringBuilder();for(String name:List.of("nightlyRate","breakfastAmount","breakfastIncluded","extraAdultRate","childRate","taxIncluded"))state.append(name).append('=').append(wrapper.getPropertyValue(name)).append('\n');if(row.isInheritPolicy())for(String name:POLICY)state.append(name).append('=').append(wrapper.getPropertyValue(name)).append('\n');try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}}
 private void lock(RatePlan source,RatePlan target){java.util.stream.Stream.of(source.getProperty(),target.getProperty()).sorted(Comparator.comparing(HotelProperty::getId)).map(HotelProperty::getId).distinct().forEach(id->properties.findByIdAndCompany_IdForUpdate(id,target.getProperty().getCompany().getId()).orElseThrow());}
 private RatePlan rate(Long company,Long id){return rates.findByIdAndProperty_Company_Id(id,company).orElseThrow(()->bad("Rate nicht gefunden."));}
 private PmsRateInheritance require(Long company,Long hotel,Long id){return rules.findById(id).filter(r->r.getTarget().getProperty().getId().equals(hotel)&&r.getTarget().getProperty().getCompany().getId().equals(company)).orElseThrow(()->bad("Regel nicht gefunden."));}
 private View view(PmsRateInheritance r){return new View(r.getId(),r.getVersion(),r.getSource().getId(),r.getSource().getName(),r.getSource().getProperty().getName(),r.getSource().getCurrencyCode(),r.getTarget().getId(),r.getTarget().getName(),r.getTarget().getCurrencyCode(),r.isEnabled(),r.isFrozen(),r.isInheritPolicy(),r.getManualFxRate(),r.getFxDate(),r.getFxValidUntil(),r.getFxReference(),r.getAdjustmentPercent(),r.getMinPrice(),r.getMaxPrice(),converted(r.getSource().getNightlyRate(),r,true),r.getLastAppliedAt(),r.getLastResult());}
 private boolean positive(BigDecimal value){return value!=null&&value.signum()>0;}
 private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
