package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRevenueAutomationDtos.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
/** Explicit comparable-day baseline, not a claim of calibrated statistical confidence. */
public final class PmsDemandForecast {
 private PmsDemandForecast() {}
 public static Prediction predict(LocalDate date,LocalDate asOf,long booked,BigDecimal bookedRevenue,long capacity,List<Observation> history,List<Long> pickup,int minimum,String currency) {
  List<Observation> sample=history.stream().filter(o->o.date().isBefore(asOf)&&o.date().getDayOfWeek()==date.getDayOfWeek())
   .filter(o->!o.date().isBefore(asOf.minusDays(84))||Math.abs(ChronoUnit.DAYS.between(date.minusYears(1),o.date()))<=35).toList();
  int seasonal=(int)sample.stream().filter(o->Math.abs(ChronoUnit.DAYS.between(date.minusYears(1),o.date()))<=35).count();
  if(sample.size()<minimum) return new Prediction(date,booked,null,null,null,null,sample.size(),seasonal,pickup.size(),"INSUFFICIENT_HISTORY");
  double mean=sample.stream().mapToLong(Observation::roomNights).average().orElseThrow();
  boolean usePickup=pickup.size()>=minimum;
  double estimate=usePickup?(mean+booked+pickup.stream().mapToLong(Long::longValue).average().orElseThrow())/2:mean;
  long expected=Math.max(booked,Math.min(capacity,Math.round(estimate)));
  long low=Math.max(booked,Math.min(capacity,sample.stream().mapToLong(Observation::roomNights).min().orElse(booked)));
  long high=Math.max(expected,Math.min(capacity,sample.stream().mapToLong(Observation::roomNights).max().orElse(booked)));
  low=Math.min(low,expected);
  List<Observation> priced=sample.stream().filter(o->o.roomNights()>0&&o.netRoomRevenue()!=null&&o.netRoomRevenue().signum()>=0).toList();
  BigDecimal revenue=null;
  if(bookedRevenue!=null&&priced.size()>=minimum) {
   BigDecimal adr=priced.stream().map(Observation::netRoomRevenue).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(priced.stream().mapToLong(Observation::roomNights).sum()),8,RoundingMode.HALF_UP);
   revenue=PmsMoney.round(bookedRevenue.add(adr.multiply(BigDecimal.valueOf(Math.max(0,expected-booked)))),currency);
  }
  return new Prediction(date,booked,expected,low,high,revenue,sample.size(),seasonal,pickup.size(),usePickup?"WEEKDAY_SEASON_PICKUP":"WEEKDAY_SEASON");
 }
 public static BigDecimal price(BigDecimal base,BigDecimal current,Rule rule,long expected,long capacity,String currency) {
  if(capacity<=0) return current;
  double occupancy=100d*expected/capacity;
  BigDecimal adjustment=occupancy>=rule.highOccupancy()?rule.adjustmentPercent():occupancy<=rule.lowOccupancy()?rule.adjustmentPercent().negate():BigDecimal.ZERO;
  BigDecimal target=base.multiply(BigDecimal.ONE.add(adjustment.movePointLeft(2)));
  BigDecimal delta=current.multiply(rule.maxChangePercent().movePointLeft(2));
  BigDecimal lower=current.subtract(delta).max(rule.minPrice()),upper=current.add(delta).min(rule.maxPrice());
  if(lower.compareTo(upper)>0)return current; // No price can satisfy both constraints; do not invent an exception.
  return PmsMoney.round(target.max(lower).min(upper),currency);
 }
}
