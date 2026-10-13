package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsRevenuePlanningDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PmsRevenuePlanningService {
 private final HotelPropertyRepository properties;
 private final PmsRevenueSnapshotRepository snapshots;
 private final PmsRevenueBudgetRepository budgets;
 private final PmsPropertyAccessService access;
 private final PmsFinancialPeriodService periods;
 private final PmsAuditWriter audit;
 private final EntityManager em;
 public PmsRevenuePlanningService(HotelPropertyRepository properties,PmsRevenueSnapshotRepository snapshots,
         PmsRevenueBudgetRepository budgets,PmsPropertyAccessService access,PmsFinancialPeriodService periods,PmsAuditWriter audit,EntityManager em) {
  this.properties=properties;this.snapshots=snapshots;this.budgets=budgets;this.access=access;this.periods=periods;this.audit=audit;this.em=em;
 }
 @Transactional(readOnly=true)
 public Report report(String username,Long propertyId,LocalDate fromInput,LocalDate toInput,Long comparisonId) {
  HotelProperty property=property(username,propertyId,false);LocalDate businessDate=periods.readBusinessDate(property);
  LocalDate from=fromInput==null?businessDate:fromInput;LocalDate to=toInput==null?from.plusDays(30):toInput;
  validateRange(from,to,businessDate);
  List<PmsRevenueSnapshot> available=snapshots.findTop366ByProperty_IdOrderByAsOfDateDesc(propertyId);
  PmsRevenueSnapshot previous=comparisonId==null?available.stream().filter(s->!s.getAsOfDate().isAfter(businessDate)).findFirst().orElse(null)
          :snapshots.findByIdAndProperty_Id(comparisonId,propertyId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Vergleichsstand nicht gefunden."));
  List<Day> current=live(property,from,to);Map<LocalDate,PmsRevenueSnapshotDay> prior=new HashMap<>();
  if(previous!=null)previous.getDays().forEach(day->prior.put(day.getStayDate(),day));
  List<Day> days=current.stream().map(day->{var old=prior.get(day.date());return new Day(day.date(),day.roomNights(),day.netRoomRevenue(),day.unknownRevenueRoomNights(),
          old==null?null:old.getRoomNights(),old==null?null:old.getNetRoomRevenue(),old==null?null:day.roomNights()-old.getRoomNights(),
          old==null||old.getNetRoomRevenue()==null||day.netRoomRevenue()==null?null:day.netRoomRevenue().subtract(old.getNetRoomRevenue()));}).toList();
  Map<LocalDate,PmsRevenueBudget> planned=new HashMap<>();budgets.findByProperty_IdAndMonthStartGreaterThanEqualAndMonthStartLessThanOrderByMonthStartAsc(
          propertyId,from.withDayOfMonth(1),to.minusDays(1).withDayOfMonth(1).plusMonths(1)).forEach(b->planned.put(b.getMonthStart(),b));
  List<Month> months=new ArrayList<>();
  for(LocalDate month=from.withDayOfMonth(1);month.isBefore(to);month=month.plusMonths(1)) {
   LocalDate key=month;List<Day> slice=days.stream().filter(day->day.date().withDayOfMonth(1).equals(key)).toList();Summary sum=summary(slice);
   PmsRevenueBudget budget=planned.get(month);BigDecimal budgetRevenue=budget==null?null:PmsMoney.round(budget.getNetRoomRevenue().multiply(BigDecimal.valueOf(slice.size()))
           .divide(BigDecimal.valueOf(month.lengthOfMonth()),8,RoundingMode.HALF_UP),property.getCurrencyCode());
   BigDecimal budgetNights=budget==null?null:BigDecimal.valueOf(budget.getRoomNights()).multiply(BigDecimal.valueOf(slice.size()))
           .divide(BigDecimal.valueOf(month.lengthOfMonth()),2,RoundingMode.HALF_UP);
   months.add(new Month(month,slice.size(),month.lengthOfMonth(),sum.roomNights(),sum.netRoomRevenue(),sum.previousRoomNights(),sum.previousNetRoomRevenue(),
           sum.pickupRoomNights(),sum.pickupNetRoomRevenue(),budgetNights,budgetRevenue,
           budgetRevenue==null||sum.netRoomRevenue()==null?null:sum.netRoomRevenue().subtract(budgetRevenue),budget==null?null:budgetView(budget)));
  }
  return new Report(propertyId,property.getCurrencyCode(),businessDate,from,to,previous==null?null:snapshotView(previous),
          available.stream().map(this::snapshotView).toList(),summary(days),days,List.copyOf(months));
 }
 @Transactional
 public SnapshotView capture(String username,Long propertyId) {
  return captureLocked(property(username,propertyId,true),username);
 }
 SnapshotView captureLocked(HotelProperty property,String username) {
  Long propertyId=property.getId();LocalDate businessDate=periods.currentBusinessDate(property);
  var existing=snapshots.findByProperty_IdAndAsOfDate(propertyId,businessDate).orElse(null);
  if(existing!=null)return snapshotView(existing);
  PmsRevenueSnapshot snapshot=new PmsRevenueSnapshot();snapshot.setProperty(property);snapshot.setAsOfDate(businessDate);
  snapshot.setToExclusive(businessDate.plusDays(366));snapshot.setCurrencyCode(property.getCurrencyCode());snapshot.setCapturedBy(username);
  snapshot.setCapturedAt(LocalDateTime.now(ZoneId.of(property.getTimezone())));
  for(Day day:live(property,businessDate,snapshot.getToExclusive())) {
   PmsRevenueSnapshotDay row=new PmsRevenueSnapshotDay();row.setSnapshot(snapshot);row.setStayDate(day.date());row.setRoomNights(day.roomNights());
   row.setNetRoomRevenue(day.netRoomRevenue());row.setUnknownRevenueRoomNights(day.unknownRevenueRoomNights());snapshot.getDays().add(row);
  }
  snapshots.saveAndFlush(snapshot);audit.append(property,"revenue_snapshot.created","revenue_snapshot",snapshot.getId().toString(),"{\"asOfDate\":\""+businessDate+"\"}");
  return snapshotView(snapshot);
 }
 @Transactional
 public BudgetView saveBudget(String username,Long propertyId,String monthText,BudgetInput input) {
  HotelProperty property=property(username,propertyId,true);LocalDate month;
  try {month=YearMonth.parse(monthText).atDay(1);}catch(RuntimeException invalid){throw error(HttpStatus.BAD_REQUEST,"Budgetmonat muss YYYY-MM entsprechen.");}
  if(input==null||input.roomNights()<0)throw error(HttpStatus.BAD_REQUEST,"Budgetwerte dürfen nicht negativ sein.");
  BigDecimal amount=PmsMoney.require(input.netRoomRevenue(),property.getCurrencyCode());if(amount.signum()<0)throw error(HttpStatus.BAD_REQUEST,"Budgetwerte dürfen nicht negativ sein.");
  PmsRevenueBudget row=budgets.findByProperty_IdAndMonthStart(propertyId,month).orElse(null);
  if(row!=null && !Objects.equals(input.expectedVersion(),row.getVersion()))throw error(HttpStatus.CONFLICT,"Das Monatsbudget wurde inzwischen geändert. Bitte neu laden.");
  if(row==null){row=new PmsRevenueBudget();row.setProperty(property);row.setMonthStart(month);row.setCurrencyCode(property.getCurrencyCode());}
  row.setNetRoomRevenue(amount);row.setRoomNights(input.roomNights());row.setUpdatedBy(username);row.setUpdatedAt(LocalDateTime.now(ZoneId.of(property.getTimezone())));
  budgets.saveAndFlush(row);audit.append(property,"revenue_budget.updated","revenue_budget",row.getId().toString(),"{\"month\":\""+monthText+"\",\"roomNights\":"+row.getRoomNights()+",\"netRoomRevenue\":"+amount.toPlainString()+"}");
  return budgetView(row);
 }
 private HotelProperty property(String username,Long id,boolean write) {
  var actor=access.access(username);access.require(actor,id,"REPORTS",write);
  return (write?properties.findByIdAndCompany_IdForUpdate(id,actor.companyId()):properties.findByIdAndCompany_Id(id,actor.companyId()))
          .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));
 }
 private void validateRange(LocalDate from,LocalDate to,LocalDate businessDate) {
  if(from==null||to==null||!to.isAfter(from)||ChronoUnit.DAYS.between(from,to)>366||from.isBefore(businessDate))
   throw error(HttpStatus.BAD_REQUEST,"Ein zukünftiger Zeitraum ab dem offenen Betriebstag "+businessDate+" mit höchstens 366 Tagen ist erforderlich.");
 }
 @SuppressWarnings("unchecked")
 List<Day> live(HotelProperty property,LocalDate from,LocalDate to) {
  // Date deltas count room nights without hydrating guests or walking every stay night.
  Map<LocalDate,Long> deltas=new HashMap<>();
  List<Object[]> reservations=em.createQuery("select r.arrivalDate,r.departureDate from Reservation r where r.property.id=:property and r.status in :statuses and r.arrivalDate<:to and r.departureDate>:from",Object[].class)
          .setParameter("property",property.getId()).setParameter("statuses",List.of(ReservationStatus.CONFIRMED,ReservationStatus.CHECKED_IN)).setParameter("from",from).setParameter("to",to).getResultList();
  for(Object[] row:reservations){LocalDate start=((LocalDate)row[0]).isBefore(from)?from:(LocalDate)row[0];LocalDate end=((LocalDate)row[1]).isAfter(to)?to:(LocalDate)row[1];deltas.merge(start,1L,Long::sum);deltas.merge(end,-1L,Long::sum);}
  // Aggregate ROOM ledger values, including dated room credits, retaining original source reservations after routing.
  // Generated-charge coverage separately detects missing or duplicated priced nights.
  List<Object[]> charges=em.createNativeQuery("""
          SELECT stay.service_date,
                 SUM(CASE WHEN stay.generated_count=1 AND stay.missing_tax=0 THEN 1 ELSE 0 END),
                 SUM(stay.net_amount)
          FROM (
          SELECT i.service_date,r.id AS source_id,
                 SUM(CASE WHEN i.rate_generated=TRUE THEN 1 ELSE 0 END) AS generated_count,
                 SUM(CASE WHEN i.tax_rate IS NULL THEN 1 ELSE 0 END) AS missing_tax,
                 SUM(CASE WHEN i.tax_rate IS NULL THEN 0 WHEN i.tax_included=TRUE
                     THEN ROUND(i.total_amount/(1+i.tax_rate/100),:digits) ELSE i.total_amount END) AS net_amount
          FROM pms_folio_items i JOIN pms_folios f ON f.id=i.folio_id
          JOIN pms_reservations r ON r.id=COALESCE(i.source_reservation_id,f.reservation_id)
          WHERE r.property_id=:property AND r.status IN ('CONFIRMED','CHECKED_IN')
            AND i.type='ROOM' AND i.service_date>=:from AND i.service_date<:to
            AND i.service_date>=r.arrival_date AND i.service_date<r.departure_date
          GROUP BY i.service_date,r.id
          ) stay GROUP BY stay.service_date ORDER BY stay.service_date
          """).setParameter("property",property.getId()).setParameter("from",from).setParameter("to",to).setParameter("digits",PmsMoney.digits(property.getCurrencyCode())).getResultList();
  Map<LocalDate,Object[]> perDay=new HashMap<>();for(Object[] row:charges){LocalDate date=row[0] instanceof LocalDate d?d:((java.sql.Date)row[0]).toLocalDate();perDay.put(date,row);}
  long count=0;List<Day> result=new ArrayList<>();
  for(LocalDate date=from;date.isBefore(to);date=date.plusDays(1)) {
   count+=deltas.getOrDefault(date,0L);Object[] row=perDay.get(date);long priced=row==null?0:((Number)row[1]).longValue();
   long unknown=Math.max(0,count-priced);
   BigDecimal net=unknown>0?null:PmsMoney.round(row==null?BigDecimal.ZERO:new BigDecimal(row[2].toString()),property.getCurrencyCode());
   result.add(new Day(date,count,net,unknown,null,null,null,null));
  }
  return result;
 }
 private Summary summary(List<Day> days) {
  long nights=days.stream().mapToLong(Day::roomNights).sum();long unknown=days.stream().mapToLong(Day::unknownRevenueRoomNights).sum();
  int covered=(int)days.stream().filter(d->d.previousRoomNights()!=null).count();boolean full=covered==days.size();
  BigDecimal net=days.stream().anyMatch(d->d.netRoomRevenue()==null)?null:days.stream().map(Day::netRoomRevenue).reduce(BigDecimal.ZERO,BigDecimal::add);
  Long previous=full?days.stream().mapToLong(Day::previousRoomNights).sum():null;
  BigDecimal prior=!full||days.stream().anyMatch(d->d.previousNetRoomRevenue()==null)?null:days.stream().map(Day::previousNetRoomRevenue).reduce(BigDecimal.ZERO,BigDecimal::add);
  return new Summary(nights,net,previous,prior,previous==null?null:nights-previous,net==null||prior==null?null:net.subtract(prior),unknown,covered,days.size());
 }
 private BudgetView budgetView(PmsRevenueBudget row){return new BudgetView(row.getId(),row.getVersion(),row.getMonthStart(),row.getNetRoomRevenue(),row.getRoomNights(),row.getUpdatedAt(),row.getUpdatedBy());}
 private SnapshotView snapshotView(PmsRevenueSnapshot row){return new SnapshotView(row.getId(),row.getAsOfDate(),row.getToExclusive(),row.getCapturedAt(),row.getCapturedBy());}
 private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
