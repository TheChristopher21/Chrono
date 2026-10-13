package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsRatePublicationDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PmsRatePublicationService {
    private final RatePlanRepository rates;
    private final HotelPropertyRepository properties;
    private final RoomTypeRepository roomTypes;
    private final PmsPropertyAccessService access;
    private final PmsAuditWriter audit;
    public PmsRatePublicationService(RatePlanRepository rates, HotelPropertyRepository properties,
            RoomTypeRepository roomTypes, PmsPropertyAccessService access, PmsAuditWriter audit) {
        this.rates=rates;this.properties=properties;this.roomTypes=roomTypes;this.access=access;this.audit=audit;
    }
    @Transactional
    public Published publish(String username, Long sourceRatePlanId, Publish request) {
        var actor=access.access(username);
        if (!actor.master()) throw error(HttpStatus.FORBIDDEN,"Für die zentrale Ratenverteilung ist ein PMS-Master erforderlich.");
        if (request == null || request.targets() == null || request.targets().isEmpty() || request.targets().size()>100)
            throw error(HttpStatus.BAD_REQUEST,"Zwischen einem und 100 explizite Zielraten auswählen.");
        RatePlan source=rates.findByIdAndProperty_Company_Id(sourceRatePlanId,actor.companyId())
                .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Quellrate nicht gefunden."));
        // Stable lock order makes a multi-hotel publication atomic alongside concurrent inventory/pricing work.
        Map<Long,HotelProperty> targets=new HashMap<>();
        request.targets().stream().map(Target::propertyId).distinct().sorted().forEach(id -> targets.put(id,
                properties.findByIdAndCompany_IdForUpdate(id,actor.companyId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Zielhotel nicht gefunden."))));
        Set<String> seen=new HashSet<>();Set<Long> seenIds=new HashSet<>();List<PublishedTarget> published=new ArrayList<>();
        for(Target input:request.targets()) {
            HotelProperty hotel=targets.get(input.propertyId());
            if (!hotel.getCurrencyCode().equalsIgnoreCase(input.currencyCode()))
                throw error(HttpStatus.CONFLICT,"Zielwährung entspricht nicht der Hotelwährung; Preise müssen ausdrücklich in der Zielwährung angegeben werden.");
            RoomType roomType=roomTypes.findByIdAndProperty_Company_Id(input.roomTypeId(),actor.companyId())
                    .filter(type->type.getProperty().getId().equals(hotel.getId()))
                    .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Ziel-Zimmertyp nicht gefunden."));
            String code=input.code().trim().toUpperCase(Locale.ROOT);
            if (!seen.add(hotel.getId()+":"+code) || input.targetRatePlanId()!=null && !seenIds.add(input.targetRatePlanId()))
                throw error(HttpStatus.BAD_REQUEST,"Jede Zielrate darf nur einmal ausgewählt werden.");
            RatePlan target=input.targetRatePlanId()==null?new RatePlan():rates.findByIdAndProperty_Company_Id(input.targetRatePlanId(),actor.companyId())
                    .filter(rate->rate.getProperty().getId().equals(hotel.getId()) && rate.getRoomType().getId().equals(roomType.getId()))
                    .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Die ausgewählte Zielrate gehört nicht zu diesem Hotel und Zimmertyp."));
            if (Objects.equals(target.getId(),sourceRatePlanId)) throw error(HttpStatus.CONFLICT,"Die Quellrate kann nicht zugleich Ziel der Veröffentlichung sein.");
            boolean duplicate=target.getId()==null?rates.existsByProperty_IdAndCodeIgnoreCase(hotel.getId(),code)
                    :rates.existsByProperty_IdAndCodeIgnoreCaseAndIdNot(hotel.getId(),code,target.getId());
            if (duplicate) throw error(HttpStatus.CONFLICT,"Der Zielratencode existiert bereits; die bestehende Zielrate ausdrücklich auswählen.");
            BeanUtils.copyProperties(source,target,"id","property","roomType","code","name","currencyCode",
                    "nightlyRate","breakfastAmount","extraAdultRate","childRate","vatRate","breakfastVatRate","policyFeeTaxRate");
            target.setProperty(hotel);target.setRoomType(roomType);target.setCode(code);target.setName(input.name().trim());target.setCurrencyCode(hotel.getCurrencyCode());
            target.setNightlyRate(nonNegative(input.nightlyRate(),hotel.getCurrencyCode()));
            target.setBreakfastAmount(nonNegative(input.breakfastAmount(),hotel.getCurrencyCode()));
            target.setExtraAdultRate(nonNegative(input.extraAdultRate(),hotel.getCurrencyCode()));
            target.setChildRate(nonNegative(input.childRate(),hotel.getCurrencyCode()));
            target.setVatRate(tax(input.vatRate(),true));target.setBreakfastVatRate(tax(input.breakfastVatRate(),source.isBreakfastIncluded()));
            target.setPolicyFeeTaxRate(tax(input.policyFeeTaxRate(),positive(source.getCancellationFeePercent()) || positive(source.getNoShowFeePercent())));
            if (target.getBreakfastAmount().compareTo(target.getNightlyRate())>0) throw error(HttpStatus.BAD_REQUEST,"Frühstücksanteil übersteigt den Zielpreis.");
            rates.saveAndFlush(target);
            audit.append(hotel,"rate_plan.published","rate_plan",target.getId().toString(),"{\"sourceRatePlanId\":"+sourceRatePlanId+",\"nightlyRate\":"+target.getNightlyRate().toPlainString()+",\"currency\":\""+hotel.getCurrencyCode()+"\"}");
            published.add(new PublishedTarget(hotel.getId(),target.getId(),target.getCode(),target.getCurrencyCode()));
        }
        return new Published(sourceRatePlanId,LocalDateTime.now(),List.copyOf(published));
    }
    private BigDecimal nonNegative(BigDecimal value,String currency) {
        value=PmsMoney.require(value,currency);if(value.signum()<0)throw error(HttpStatus.BAD_REQUEST,"Preise dürfen nicht negativ sein.");return value;
    }
    private boolean positive(BigDecimal value) {return value!=null&&value.signum()>0;}
    private BigDecimal tax(BigDecimal value,boolean required) {
        if(value==null&&!required)return null;
        if(value==null||value.signum()<0||value.compareTo(new BigDecimal("100"))>0)throw error(HttpStatus.BAD_REQUEST,"Lokale Steuersätze ausdrücklich angeben (0 bei steuerfrei).");
        return value;
    }
    private ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
}
