package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRevenueAutomationDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class PmsRevenueAutomationService {
 private final EntityManager em;
 private final HotelPropertyRepository properties;
 private final PmsPropertyAccessService access;
 private final PmsFinancialPeriodService periods;
 private final PmsRevenuePlanningService planning;
 private final RatePlanRepository rates;
 private final RateOverrideRepository overrides;
 private final PmsAuditWriter audit;
 private final ObjectMapper json;
 @Transactional(readOnly=true) public Config config(String user,Long id) {return config(property(user,id,"REPORTS",false));}
 public Config save(String user,Long id,Config input) {
  HotelProperty hotel=property(user,id,"RATES",true);
  if(input==null||input.horizonDays()<1||input.horizonDays()>90||input.minSamples()<4||input.minSamples()>52||input.rules()==null||input.rules().size()>50)throw bad("Horizont 1–90 Tage, mindestens 4–52 Vergleichstage und höchstens 50 Preisregeln erforderlich.");
  if(input.pricingEnabled()) access.requireMaster(user);
  Set<Long> unique=new HashSet<>();
  for(Rule rule:input.rules()) {
   if(rule==null||rule.ratePlanId()==null||!unique.add(rule.ratePlanId()))throw bad("Jede Rate darf nur eine Preisregel haben.");
   rate(hotel,rule.ratePlanId());
   if(rule.minPrice()==null||rule.maxPrice()==null||rule.maxChangePercent()==null||rule.adjustmentPercent()==null
    ||rule.minPrice().signum()<0||rule.maxPrice().compareTo(rule.minPrice())<0||rule.maxChangePercent().signum()<0||rule.maxChangePercent().compareTo(new BigDecimal("25"))>0
    ||rule.adjustmentPercent().signum()<0||rule.adjustmentPercent().compareTo(new BigDecimal("25"))>0||rule.lowOccupancy()<0||rule.highOccupancy()>100||rule.lowOccupancy()>=rule.highOccupancy())throw bad("Preisgrenzen und Belegungsschwellen prüfen; Anpassungen sind auf 25 Prozent begrenzt.");
   PmsMoney.require(rule.minPrice(),hotel.getCurrencyCode());PmsMoney.require(rule.maxPrice(),hotel.getCurrencyCode());
  }
  PmsRevenueAutomation row=em.find(PmsRevenueAutomation.class,id);
  if(row!=null&&row.getVersion()!=input.version())throw conflict("Die Revenue-Regeln wurden inzwischen geändert.");
  if(row==null){row=new PmsRevenueAutomation();row.setPropertyId(id);em.persist(row);}
  row.setSnapshotsEnabled(input.snapshotsEnabled());row.setPricingEnabled(input.pricingEnabled());row.setHorizonDays(input.horizonDays());row.setMinSamples(input.minSamples());
  try{row.setRulesJson(json.writeValueAsString(input.rules()));}catch(Exception failure){throw new IllegalStateException(failure);}
  audit.append(hotel,"revenue.automation_configured","property",id.toString(),"{\"automaticPricing\":"+input.pricingEnabled()+"}");em.flush();return config(hotel);
 }
 @Transactional(readOnly=true) public Forecast forecast(String user,Long id,LocalDate from,int days){return forecast(property(user,id,"REPORTS",false),from,days);}
 public void apply(String user,Long id,Apply input) {
  HotelProperty hotel=property(user,id,"RATES",true);
  if(input==null||input.stayDate()==null||input.expectedCurrentPrice()==null||input.proposedPrice()==null)throw bad("Preisvorschlag unvollständig.");
  Suggestion current=forecast(hotel,input.stayDate(),1).suggestions().stream().filter(s->s.ratePlanId().equals(input.ratePlanId())).findFirst().orElseThrow(()->conflict("Für diese Rate liegt kein ausreichend belegter Preisvorschlag vor."));
  if(current.currentPrice().compareTo(input.expectedCurrentPrice())!=0||current.proposedPrice().compareTo(input.proposedPrice())!=0)throw conflict("Preis oder Prognose hat sich geändert. Den aktuellen Vorschlag erneut prüfen.");
  apply(hotel,current,user,false);
 }
 /** Called by a separate scheduler bean, one property transaction at a time. Property lock + unique keys serialize instances. */
 public void runProperty(Long id) {
  HotelProperty candidate=em.find(HotelProperty.class,id);if(candidate==null||!candidate.isActive())return;
  HotelProperty hotel=properties.findByIdAndCompany_IdForUpdate(id,candidate.getCompany().getId()).orElseThrow();Config config=config(hotel);
  if(config.snapshotsEnabled())planning.captureLocked(hotel,"system:daily-revenue");
  LocalDate day=periods.currentBusinessDate(hotel);
  if(!config.pricingEnabled()||em.createQuery("select count(r) from PmsRevenuePriceRun r where r.propertyId=:id and r.businessDate=:date",Long.class).setParameter("id",id).setParameter("date",day).getSingleResult()>0)return;
  Forecast forecast=forecast(hotel,day,config.horizonDays());int applied=0;
  for(Suggestion suggestion:forecast.suggestions()) if(suggestion.automaticEligible()&&suggestion.currentPrice().compareTo(suggestion.proposedPrice())!=0){apply(hotel,suggestion,"system:revenue-pricing",true);applied++;}
  PmsRevenuePriceRun run=new PmsRevenuePriceRun();run.setPropertyId(id);run.setBusinessDate(day);run.setApplied(applied);em.persist(run);
 }
 private Forecast forecast(HotelProperty hotel,LocalDate requested,int days) {
  LocalDate day=periods.readBusinessDate(hotel),from=requested==null?day:requested;
  if(from.isBefore(day)||days<1||days>90||from.isAfter(day.plusDays(366)))throw bad("Prognose ab dem offenen Betriebstag, höchstens 90 Tage und maximal ein Jahr voraus.");
  Config config=config(hotel);long capacity=em.createQuery("select count(r) from Room r where r.property.id=:id and r.active=true and r.operationalStatus=:status",Long.class).setParameter("id",hotel.getId()).setParameter("status",RoomOperationalStatus.IN_SERVICE).getSingleResult();
  List<NightAudit> closed=em.createQuery("select n from NightAudit n where n.property.id=:id and n.businessDate>=:start and n.businessDate<:end order by n.businessDate",NightAudit.class).setParameter("id",hotel.getId()).setParameter("start",day.minusDays(730)).setParameter("end",day).getResultList();
  // Net ledger is used only when all source taxes are known; audit dates define observed days, including real zero occupancy.
  List<Object[]> net=em.createQuery("select i.serviceDate,sum(i.totalAmount/(1+i.taxRate/100)),sum(case when i.taxRate is null then 1 else 0 end) from FolioItem i where i.folio.reservation.property.id=:id and i.type=:type and i.serviceDate>=:start and i.serviceDate<:end group by i.serviceDate",Object[].class)
   .setParameter("id",hotel.getId()).setParameter("type",FolioItemType.ROOM).setParameter("start",day.minusDays(730)).setParameter("end",day).getResultList();
  Map<LocalDate,BigDecimal> revenue=new HashMap<>();for(Object[] row:net)revenue.put((LocalDate)row[0],((Number)row[2]).longValue()>0||row[1]==null?null:new BigDecimal(row[1].toString()));
  List<Observation> history=closed.stream().map(n->new Observation(n.getBusinessDate(),n.getInHouseCount(),revenue.get(n.getBusinessDate()))).toList();
  Map<LocalDate,Long> actual=new HashMap<>();history.forEach(o->actual.put(o.date(),o.roomNights()));
  List<Object[]> snapshotDays=em.createQuery("select d.stayDate,d.snapshot.asOfDate,d.roomNights from PmsRevenueSnapshotDay d where d.snapshot.property.id=:id and d.stayDate>=:start and d.stayDate<:end and d.snapshot.asOfDate<=d.stayDate",Object[].class).setParameter("id",hotel.getId()).setParameter("start",day.minusDays(180)).setParameter("end",day).getResultList();
  List<Prediction> predictions=new ArrayList<>();
  for(var live:planning.live(hotel,from,from.plusDays(days))) {
   long lead=ChronoUnit.DAYS.between(day,live.date());List<Long> pickup=new ArrayList<>();
   for(Object[] old:snapshotDays) if(ChronoUnit.DAYS.between((LocalDate)old[1],(LocalDate)old[0])==lead&&((LocalDate)old[0]).getDayOfWeek()==live.date().getDayOfWeek()&&actual.containsKey(old[0]))pickup.add(actual.get(old[0])-((Number)old[2]).longValue());
   predictions.add(PmsDemandForecast.predict(live.date(),day,live.roomNights(),live.netRoomRevenue(),capacity,history,pickup,config.minSamples(),hotel.getCurrencyCode()));
  }
  List<Suggestion> suggestions=new ArrayList<>();
  for(Rule rule:config.rules()) {
   RatePlan rate=rate(hotel,rule.ratePlanId());
   Map<LocalDate,RateOverride> byDate=new HashMap<>();overrides.findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(rate.getId(),from,from.plusDays(days-1)).forEach(o->byDate.put(o.getStayDate(),o));
   for(Prediction prediction:predictions) if(prediction.expectedRoomNights()!=null) {
    RateOverride override=byDate.get(prediction.date());BigDecimal current=override==null?rate.getNightlyRate():override.getPrice();
    BigDecimal proposed=PmsDemandForecast.price(rate.getNightlyRate(),current,rule,prediction.expectedRoomNights(),capacity,hotel.getCurrencyCode());
    boolean eligible=(override==null||override.isRevenueManaged())&&rate.isActive()&&current.compareTo(rule.minPrice())>=0&&current.compareTo(rule.maxPrice())<=0&&proposed.compareTo(rate.getBreakfastAmount())>=0;
    suggestions.add(new Suggestion(rate.getId(),rate.getName(),prediction.date(),current,proposed,override!=null&&!override.isRevenueManaged()?"Manueller Tagespreis bleibt vor Automatik geschützt":"Hotelweite Belegungsprognose; Basisrate, Preisgrenzen und Änderungslimit",eligible));
   }
  }
  return new Forecast(hotel.getId(),hotel.getCurrencyCode(),day,capacity,history.size(),"Vergleich abgeschlossener Wochentage (84 Tage) und Vorjahressaison (±35 Tage); bei ausreichenden Daten zusätzlich Pickup gleicher Vorlaufzeit. Band = beobachtete Spanne, kein statistisches Konfidenzintervall.",predictions,suggestions);
 }
 private void apply(HotelProperty hotel,Suggestion suggestion,String user,boolean automatic) {
  RatePlan rate=rate(hotel,suggestion.ratePlanId());RateOverride row=overrides.findByRatePlan_IdAndStayDate(rate.getId(),suggestion.stayDate()).orElse(null);
  if(automatic&&row!=null&&!row.isRevenueManaged())return;
  if(suggestion.proposedPrice().compareTo(rate.getBreakfastAmount())<0)throw conflict("Preisvorschlag liegt unter dem enthaltenen Frühstücksanteil.");
  if(row==null){row=new RateOverride();row.setRatePlan(rate);row.setStayDate(suggestion.stayDate());row.setMinStay(rate.getMinStay());}
  row.setPrice(suggestion.proposedPrice());row.setRevenueManaged(automatic);overrides.save(row);
  audit.append(hotel,"revenue.price_applied","rate_plan",rate.getId().toString(),"{\"date\":\""+suggestion.stayDate()+"\",\"from\":"+suggestion.currentPrice()+",\"to\":"+suggestion.proposedPrice()+",\"automatic\":"+automatic+",\"actor\":\""+user.replace("\"","")+"\"}");
 }
 private Config config(HotelProperty hotel){PmsRevenueAutomation row=em.find(PmsRevenueAutomation.class,hotel.getId());if(row==null)return new Config(hotel.getId(),0,true,false,30,8,List.of());try{return new Config(hotel.getId(),row.getVersion(),row.isSnapshotsEnabled(),row.isPricingEnabled(),row.getHorizonDays(),row.getMinSamples(),json.readValue(row.getRulesJson(),new TypeReference<List<Rule>>(){}));}catch(Exception failure){throw new IllegalStateException("Ungültige gespeicherte Revenue-Regeln",failure);}}
 private HotelProperty property(String user,Long id,String module,boolean write){var actor=access.access(user);access.require(actor,id,module,write);return(write?properties.findByIdAndCompany_IdForUpdate(id,actor.companyId()):properties.findByIdAndCompany_Id(id,actor.companyId())).orElseThrow(()->bad("Hotel nicht gefunden."));}
 private RatePlan rate(HotelProperty hotel,Long id){return rates.findByIdAndProperty_Company_Id(id,hotel.getCompany().getId()).filter(r->r.getProperty().getId().equals(hotel.getId())).orElseThrow(()->bad("Preisregel gehört nicht zu diesem Hotel."));}
 private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
