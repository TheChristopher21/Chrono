package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsReceivablesDto.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/** Corporate credit is a debtor transfer, never a fictitious cash/bank receipt. */
@Service @Transactional
public class PmsReceivablesService {
    private final HotelPropertyRepository properties;
    private final PmsOrganizationRepository organizations;
    private final PmsCreditAccountRepository accounts;
    private final PmsReceivableRepository receivables;
    private final PmsReceivableSettlementRepository settlements;
    private final PmsInvoiceRepository invoices;
    private final PmsInvoiceLineRepository invoiceLines;
    private final FolioItemRepository items;
    private final PaymentRepository payments;
    private final PmsFinancialPeriodService periods;
    private final PmsAuditWriter audit;

    public PmsReceivablesService(HotelPropertyRepository properties, PmsOrganizationRepository organizations,
            PmsCreditAccountRepository accounts, PmsReceivableRepository receivables, PmsReceivableSettlementRepository settlements,
            PmsInvoiceRepository invoices, PmsInvoiceLineRepository invoiceLines, FolioItemRepository items,
            PaymentRepository payments, PmsFinancialPeriodService periods, PmsAuditWriter audit) {
        this.properties=properties; this.organizations=organizations; this.accounts=accounts; this.receivables=receivables;
        this.settlements=settlements; this.invoices=invoices; this.invoiceLines=invoiceLines; this.items=items;
        this.payments=payments; this.periods=periods; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public List<CreditView> accounts(Company company, Long propertyId) {
        HotelProperty property=readProperty(company,propertyId);
        var totals=receivables.outstandingByOrganization(propertyId).stream().collect(java.util.stream.Collectors.toMap(PmsReceivableRepository.AccountOutstanding::getOrganizationId,PmsReceivableRepository.AccountOutstanding::getOutstanding));
        return accounts.findAllByProperty_IdOrderByOrganization_NameAsc(propertyId).stream().map(a ->
                new CreditView(a.getOrganization().getId(),a.getOrganization().getName(),a.isEnabled(),a.getCreditLimit(),a.getPaymentTermsDays(),totals.getOrDefault(a.getOrganization().getId(),BigDecimal.ZERO),property.getCurrencyCode())).toList();
    }

    public CreditView configure(Company company,Long propertyId,Long organizationId,CreditSettings request,String actor) {
        HotelProperty property=property(company,propertyId);
        PmsOrganization organization=organizations.findByIdAndCompany_Id(organizationId,company.getId())
                .filter(PmsOrganization::isActive).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Aktive Firma nicht gefunden."));
        BigDecimal limit=PmsMoney.require(request.creditLimit(),property.getCurrencyCode());
        if(limit.signum()<0 || request.paymentTermsDays()<0 || request.paymentTermsDays()>365) throw error(HttpStatus.BAD_REQUEST,"Ungültige Kreditbedingungen.");
        PmsCreditAccount account=accounts.findByProperty_IdAndOrganization_Id(propertyId,organizationId).orElseGet(PmsCreditAccount::new);
        account.setProperty(property); account.setOrganization(organization); account.setEnabled(request.enabled());
        account.setCreditLimit(limit); account.setPaymentTermsDays(request.paymentTermsDays()); accounts.save(account);
        audit.append(property,"receivable.credit_settings","organization",organizationId.toString(),"{\"enabled\":"+request.enabled()+",\"limit\":"+limit+"}");
        return creditView(account,property);
    }

    public ReceivableView directBill(Company company,Long propertyId,Long invoiceId,DirectBill request,String actor) {
        HotelProperty property=property(company,propertyId);
        PmsInvoice invoice=invoices.findByIdAndProperty_Company_Id(invoiceId,company.getId())
                .filter(i -> i.getProperty().getId().equals(propertyId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Rechnung nicht gefunden."));
        if(invoice.getType()!=InvoiceType.INVOICE || invoice.getStatus()!=InvoiceStatus.ISSUED) throw conflict("Nur eine ausgestellte Rechnung kann auf Firmenkredit übertragen werden.");
        Folio folio=invoice.getFolio();
        if(folio.getOrganization()==null || !folio.getOrganization().getId().equals(request.organizationId())) throw conflict("Die Firma muss dem Rechnungsgastkonto zugeordnet sein.");
        PmsCreditAccount account=accounts.findByProperty_IdAndOrganization_Id(propertyId,request.organizationId())
                .filter(PmsCreditAccount::isEnabled).filter(a -> a.getOrganization().isActive())
                .orElseThrow(() -> conflict("Für diese Firma ist kein freigegebener Hotelkredit eingerichtet."));
        BigDecimal balance=folioBalance(folio);
        PmsReceivable existing=receivables.findByInvoice_Id(invoiceId).orElse(null);
        if(balance.signum()==0 && existing!=null) return view(existing,periods.currentBusinessDate(property));
        if(folio.getStatus()!=FolioStatus.OPEN || balance.signum()<=0) throw conflict("Das offene Gastkonto benötigt einen positiven offenen Betrag.");
        balance=balance.min(availableForTransfer(invoice,existing));
        if(balance.signum()<=0) throw conflict("Die Leistungen dieser Rechnung sind bereits ausgeglichen oder auf Firmenkredit übertragen.");
        if(outstanding(propertyId,request.organizationId()).add(balance).compareTo(account.getCreditLimit())>0) throw conflict("Das freigegebene Firmenkreditlimit würde überschritten.");
        LocalDate postingDate=periods.currentBusinessDate(property);
        periods.assertPostingOpen(property,postingDate);
        PmsReceivable receivable=existing==null ? new PmsReceivable() : existing;
        if(existing!=null && existing.getCreditedAmount().signum()>0) throw conflict("Eine gutgeschriebene Forderung kann nicht erneut belastet werden.");
        receivable.setProperty(property); receivable.setOrganization(account.getOrganization()); receivable.setInvoice(invoice);
        receivable.setAmount((existing==null ? BigDecimal.ZERO : existing.getAmount()).add(balance));
        if(existing==null) { receivable.setDueDate(postingDate.plusDays(account.getPaymentTermsDays())); receivable.setCreatedAt(LocalDateTime.now()); receivable.setCreatedBy(actor); }
        receivable=receivables.saveAndFlush(receivable);
        transfer(folio,balance,receivable,postingDate,actor,"Firmenübernahme");
        audit.append(property,"receivable.transferred","receivable",receivable.getId().toString(),"{\"amount\":"+balance+"}");
        return view(receivable,postingDate);
    }

    @Transactional(readOnly=true)
    public List<ReceivableView> list(Company company,Long propertyId,Long organizationId,boolean openOnly) {
        return listPage(company,propertyId,organizationId,openOnly,false,0,50,null).items();
    }
    @Transactional(readOnly=true)
    public Page listPage(Company company,Long propertyId,Long organizationId,boolean openOnly,boolean overdueOnly,int page,int size,String query) {
        HotelProperty property=readProperty(company,propertyId); LocalDate date=periods.readBusinessDate(property);
        if(page<0 || page>10000 || size<1 || size>100) throw error(HttpStatus.BAD_REQUEST,"Ungültige Seite oder Seitengröße.");
        String search=query==null?"":query.trim().toLowerCase(java.util.Locale.ROOT);
        if(search.length()>120) throw error(HttpStatus.BAD_REQUEST,"Suchtext ist zu lang.");
        if(!search.isEmpty()) search="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var result=receivables.searchPage(propertyId,organizationId,openOnly,overdueOnly,date,search,org.springframework.data.domain.PageRequest.of(page,size));
        var totals=receivables.summary(propertyId,date);
        return new Page(result.getContent().stream().map(r -> view(r,date)).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.hasNext(),totals.getTotalOutstanding(),totals.getTotalOverdue());
    }

    public ReceivableView settle(Company company,Long propertyId,Long id,Settlement request,String actor,boolean refund) {
        HotelProperty property=property(company,propertyId);
        PmsReceivable receivable=receivables.findByIdAndProperty_Id(id,propertyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Forderung nicht gefunden."));
        BigDecimal amount=PmsMoney.require(request.amount(),receivable.getInvoice().getCurrencyCode());
        if(amount.signum()<=0 || request.bankReference()==null || request.bankReference().isBlank() || request.requestId()==null || request.requestId().isBlank()) throw error(HttpStatus.BAD_REQUEST,"Betrag, Bankreferenz und Vorgangs-ID sind erforderlich.");
        BigDecimal signed=refund ? amount.negate() : amount;
        PmsReceivableSettlement previous=settlements.findByProperty_IdAndRequestId(propertyId,request.requestId()).orElse(null);
        if(previous!=null) {
            if(!previous.getReceivable().getId().equals(id) || previous.getAmount().compareTo(signed)!=0 || !previous.getBankReference().equals(request.bankReference().trim())) throw conflict("Diese Vorgangs-ID wurde bereits für andere Zahlungsdaten verwendet.");
            return view(receivable,periods.currentBusinessDate(property));
        }
        if(receivable.getInvoice().getStatus()==InvoiceStatus.CREDITED && "REISSUE".equals(receivable.getInvoice().getCorrectionMode())) throw conflict("Die korrigierte Rechnung muss zuerst neu ausgestellt werden.");
        if(refund ? receivable.balance().negate().compareTo(amount)<0 : receivable.balance().compareTo(amount)<0) throw conflict(refund ? "Die Erstattung übersteigt das verfügbare Firmenguthaben." : "Die Zahlung übersteigt die offene Forderung.");
        LocalDate date=periods.currentBusinessDate(property); periods.assertPostingOpen(property,date);
        PmsReceivableSettlement settlement=new PmsReceivableSettlement(); settlement.setProperty(property); settlement.setReceivable(receivable);
        settlement.setAmount(signed); settlement.setPostingDate(date); settlement.setRequestId(request.requestId()); settlement.setBankReference(request.bankReference().trim());
        settlement.setCreatedAt(LocalDateTime.now()); settlement.setCreatedBy(actor); settlements.save(settlement);
        receivable.setSettledAmount(receivable.getSettledAmount().add(signed)); receivables.save(receivable);
        if(receivable.getInvoice().getStatus()!=InvoiceStatus.CREDITED) { receivable.getInvoice().setStatus(receivable.balance().signum()==0 ? InvoiceStatus.PAID : InvoiceStatus.ISSUED); invoices.save(receivable.getInvoice()); }
        audit.append(property,refund?"receivable.refunded":"receivable.settled","receivable",id.toString(),"{\"amount\":"+signed+"}");
        return view(receivable,date);
    }

    public ReceivableView remind(Company company,Long propertyId,Long id,Reminder request,String actor) {
        HotelProperty property=property(company,propertyId); LocalDate date=periods.currentBusinessDate(property);
        PmsReceivable receivable=receivables.findByIdAndProperty_Id(id,propertyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Forderung nicht gefunden."));
        if(receivable.balance().signum()<=0 || !receivable.getDueDate().isBefore(date)) throw conflict("Nur überfällige offene Forderungen können gemahnt werden.");
        receivable.setReminderLevel(receivable.getReminderLevel()+1); receivable.setLastReminderAt(LocalDateTime.now()); receivables.save(receivable);
        audit.append(property,"receivable.reminder_recorded","receivable",id.toString(),"{\"level\":"+receivable.getReminderLevel()+",\"note\":\""+json(request.note())+"\"}");
        return view(receivable,date);
    }

    /** Cancellation reverses debtor transfer; received company money remains as refundable AR credit. */
    public void creditForCancelledServices(PmsInvoice invoice,LocalDate date,String actor) {
        PmsReceivable receivable=receivables.findByInvoice_Id(invoice.getId()).orElse(null);
        if(receivable==null) return;
        receivable.setCreditedAmount(receivable.getAmount()); receivables.save(receivable);
        transfer(invoice.getFolio(),receivable.getAmount().negate(),receivable,date,actor,"Firmenforderung gutgeschrieben");
        audit.append(invoice.getProperty(),"receivable.credited","receivable",receivable.getId().toString(),"{\"amount\":"+receivable.getAmount()+"}");
    }

    /** A document-only reissue carries the existing debtor ledger to its replacement. */
    public void bindReplacement(PmsInvoice replacement,List<FolioItem> sourceItems) {
        List<PmsReceivable> pending=receivables.findAllByInvoice_Folio_Id(replacement.getFolio().getId()).stream()
                .filter(r -> r.getInvoice().getStatus()==InvoiceStatus.CREDITED && "REISSUE".equals(r.getInvoice().getCorrectionMode())).toList();
        if(pending.isEmpty()) return;
        if(pending.size()!=1) throw conflict("Mehrere korrigierte Firmenrechnungen müssen einzeln neu ausgestellt werden.");
        PmsReceivable receivable=pending.get(0);
        Set<Long> selected=sourceItems.stream().map(FolioItem::getId).collect(java.util.stream.Collectors.toSet());
        if(invoiceLines.findAllByInvoice_IdOrderByIdAsc(receivable.getInvoice().getId()).stream().anyMatch(l -> l.getSourceItem()!=null && !selected.contains(l.getSourceItem().getId()))) throw conflict("Die Neuausstellung muss sämtliche Leistungen der Firmenrechnung enthalten.");
        receivable.setInvoice(replacement); receivables.save(receivable);
        if(receivable.balance().signum()==0 && folioBalance(replacement.getFolio()).signum()==0) replacement.setStatus(InvoiceStatus.PAID);
    }

    private void transfer(Folio folio,BigDecimal amount,PmsReceivable receivable,LocalDate date,String actor,String reason) {
        Payment payment=new Payment(); payment.setFolio(folio); payment.setAmount(amount); payment.setMethod(PaymentMethod.DIRECT_BILL);
        payment.setStatus(PaymentStatus.POSTED); payment.setKind(amount.signum()>0?PaymentKind.PAYMENT:PaymentKind.REFUND);
        payment.setReference("AR-"+receivable.getId()); payment.setReason(reason); payment.setCreatedBy(actor);
        payment.setPostingDate(date);
        payment.setReceivedAt(date.atTime(LocalDateTime.now().toLocalTime())); payments.save(payment);
    }
    private HotelProperty property(Company company,Long id) { return properties.findByIdAndCompany_IdForUpdate(id,company.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Hotelbetrieb nicht gefunden.")); }
    private HotelProperty readProperty(Company company,Long id) { return properties.findByIdAndCompany_Id(id,company.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Hotelbetrieb nicht gefunden.")); }
    private BigDecimal outstanding(Long propertyId,Long organizationId) { return receivables.sumOutstanding(propertyId,organizationId); }
    private BigDecimal folioBalance(Folio folio) {
        BigDecimal charges=items.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId()).stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal paid=payments.findAllByFolio_IdOrderByReceivedAtAsc(folio.getId()).stream().filter(p -> p.getStatus()==PaymentStatus.POSTED).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return PmsMoney.round(charges.subtract(paid),folio.getCurrencyCode());
    }
    private BigDecimal availableForTransfer(PmsInvoice invoice,PmsReceivable existing) {
        BigDecimal paid=payments.findAllByFolio_IdOrderByReceivedAtAsc(invoice.getFolio().getId()).stream()
                .filter(p -> p.getStatus()==PaymentStatus.POSTED && p.getMethod()!=PaymentMethod.DIRECT_BILL)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal priorCapacity=invoices.findAllByFolio_IdOrderByIssueDateDesc(invoice.getFolio().getId()).stream()
                .filter(i -> i.getType()==InvoiceType.INVOICE && (i.getStatus()==InvoiceStatus.ISSUED || i.getStatus()==InvoiceStatus.PAID) && i.getId()<invoice.getId())
                .map(i -> i.getGrossAmount().subtract(receivables.findByInvoice_Id(i.getId()).map(PmsReceivable::getAmount).orElse(BigDecimal.ZERO)).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal alreadyTransferred=existing==null?BigDecimal.ZERO:existing.getAmount();
        return invoice.getGrossAmount().subtract(alreadyTransferred).subtract(paid.subtract(priorCapacity).max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
    }
    private CreditView creditView(PmsCreditAccount a,HotelProperty p) { return new CreditView(a.getOrganization().getId(),a.getOrganization().getName(),a.isEnabled(),a.getCreditLimit(),a.getPaymentTermsDays(),outstanding(p.getId(),a.getOrganization().getId()),p.getCurrencyCode()); }
    private ReceivableView view(PmsReceivable r,LocalDate date) {
        String status=r.getInvoice().getStatus()==InvoiceStatus.CREDITED && "REISSUE".equals(r.getInvoice().getCorrectionMode()) ? "REISSUE_PENDING" : r.balance().signum()<0 ? "CREDIT_BALANCE" : r.balance().signum()==0 ? (r.getCreditedAmount().signum()>0?"CREDITED":"PAID") : r.getDueDate().isBefore(date)?"OVERDUE":"OPEN";
        return new ReceivableView(r.getId(),r.getInvoice().getId(),r.getInvoice().getInvoiceNumber(),r.getInvoice().getFolio().getId(),r.getInvoice().getFolio().getReservation().getGroupBooking()==null?null:r.getInvoice().getFolio().getReservation().getGroupBooking().getId(),r.getOrganization().getId(),r.getOrganization().getName(),r.getInvoice().getCurrencyCode(),r.getDueDate(),r.getAmount(),r.getSettledAmount(),r.getCreditedAmount(),r.balance(),status,r.balance().signum()>0?Math.max(0,ChronoUnit.DAYS.between(r.getDueDate(),date)):0,r.getReminderLevel(),r.getLastReminderAt(),settlements.findAllByReceivable_IdOrderByIdAsc(r.getId()).stream().map(s -> new SettlementView(s.getId(),s.getAmount(),s.getPostingDate(),s.getBankReference(),s.getCreatedBy())).toList());
    }
    private String json(String value) { return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r"); }
    private ResponseStatusException conflict(String text) { return error(HttpStatus.CONFLICT,text); }
    private ResponseStatusException error(HttpStatus status,String text) { return new ResponseStatusException(status,text); }
}
