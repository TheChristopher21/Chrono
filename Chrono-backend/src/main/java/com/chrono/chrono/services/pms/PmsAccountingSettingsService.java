package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsAccountingSettingsDto;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PmsAccountingSettingsService {
    private final PmsAccountingSettingsRepository settings;
    private final HotelPropertyRepository properties;
    private final PmsAuditWriter audit;
    private static final Map<String,String> REVENUE_DEFAULTS=Map.of("ROOM","3200","BREAKFAST","3210","SERVICE","3220","OTHER","3220","TAX","3600","DISCOUNT","3290");
    private static final Map<String,String> PAYMENT_DEFAULTS=Map.of("CASH","1000","CARD","1020","BANK_TRANSFER","1021","VOUCHER","1090","OTHER","1099");
    public PmsAccountingSettingsDto get(Company company,Long propertyId) {
        HotelProperty property=properties.findByIdAndCompany_Id(propertyId,company.getId()).orElseThrow(()->missing());
        return forProperty(property);
    }
    public PmsAccountingSettingsDto forProperty(HotelProperty property) {
        Map<String,String> stored=settings.findById(property.getId()).map(PmsAccountingSettings::getAccounts).orElse(Map.of());
        Map<String,String> revenue=new LinkedHashMap<>(),payment=new LinkedHashMap<>();
        REVENUE_DEFAULTS.forEach((key,value)->revenue.put(key,stored.getOrDefault("REVENUE_"+key,value)));
        PAYMENT_DEFAULTS.forEach((key,value)->payment.put(key,stored.getOrDefault("PAYMENT_"+key,value)));
        return new PmsAccountingSettingsDto(stored.getOrDefault("GUEST_RECEIVABLE","1100"),stored.getOrDefault("CORPORATE_RECEIVABLE","1105"),
                stored.getOrDefault("CORPORATE_BANK","1021"),stored.getOrDefault("POS_REVENUE","3220"),Map.copyOf(revenue),Map.copyOf(payment));
    }
    @Transactional
    public PmsAccountingSettingsDto update(Company company,Long propertyId,PmsAccountingSettingsDto request,String username) {
        HotelProperty property=properties.findByIdAndCompany_IdForUpdate(propertyId,company.getId()).orElseThrow(()->missing());
        if(request.revenueAccounts()==null || !request.revenueAccounts().keySet().equals(REVENUE_DEFAULTS.keySet())
                || request.paymentAccounts()==null || !request.paymentAccounts().keySet().equals(PAYMENT_DEFAULTS.keySet()))
            throw invalid("Bitte sämtliche bekannten Leistungsarten und Zahlungsarten zuordnen.");
        Map<String,String> values=new LinkedHashMap<>();
        values.put("GUEST_RECEIVABLE",account(request.guestReceivableAccount()));values.put("CORPORATE_RECEIVABLE",account(request.corporateReceivableAccount()));
        values.put("CORPORATE_BANK",account(request.bankAccount()));values.put("POS_REVENUE",account(request.posRevenueAccount()));
        request.revenueAccounts().forEach((key,value)->values.put("REVENUE_"+key,account(value)));
        request.paymentAccounts().forEach((key,value)->values.put("PAYMENT_"+key,account(value)));
        if(request.guestReceivableAccount().equals(request.corporateReceivableAccount())) throw invalid("Gastkonten und Firmenforderungen benötigen getrennte Konten für die nachvollziehbare Übernahme.");
        PmsAccountingSettings stored=settings.findById(propertyId).orElseGet(PmsAccountingSettings::new);
        stored.setProperty(property);stored.setAccounts(values);stored.setUpdatedAt(LocalDateTime.now());stored.setUpdatedBy(username);settings.save(stored);
        String detail=values.entrySet().stream().map(e->"\""+e.getKey()+"\":\""+e.getValue()+"\"").collect(java.util.stream.Collectors.joining(",","{","}"));
        audit.append(property,"accounting.accounts_updated","property",propertyId.toString(),detail);
        return forProperty(property);
    }
    public static String paymentAccount(PmsAccountingSettingsDto settings,PaymentMethod method) {
        return method==PaymentMethod.DIRECT_BILL?settings.corporateReceivableAccount():settings.paymentAccounts().get(method.name());
    }
    private String account(String value) {
        if(value==null || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,31}")) throw invalid("Kontonummern benötigen 1 bis 32 Buchstaben oder Ziffern, optional Punkt, Bindestrich, Schrägstrich oder Unterstrich.");
        return value;
    }
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel nicht gefunden.");}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
