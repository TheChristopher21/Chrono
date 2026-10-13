package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsBillingAutomationDtos.*;
import com.chrono.chrono.dto.pms.PmsReceivablesDto.Settlement;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

/** A controlled statement import: matching suggestions never post money without confirmation. */
@Service @Transactional
public class PmsBankReconciliationService {
 private final HotelPropertyRepository properties;
 private final PmsBankImportRepository imports;
 private final PmsBankTransactionRepository transactions;
 private final PmsReceivableRepository receivables;
 private final PmsReceivablesService ledger;
 private final PmsFinancialPeriodService periods;
 private final PmsAccountingExportRunRepository exports;
 private final ObjectProvider<PmsExtensionsService> extensions;
 private final PmsAuditWriter audit;
 public PmsBankReconciliationService(HotelPropertyRepository properties,PmsBankImportRepository imports,PmsBankTransactionRepository transactions,
   PmsReceivableRepository receivables,PmsReceivablesService ledger,PmsFinancialPeriodService periods,PmsAccountingExportRunRepository exports,
   ObjectProvider<PmsExtensionsService> extensions,PmsAuditWriter audit){
  this.properties=properties;this.imports=imports;this.transactions=transactions;this.receivables=receivables;this.ledger=ledger;this.periods=periods;this.exports=exports;this.extensions=extensions;this.audit=audit;
 }
 public BankImportView preview(Long companyId,Long propertyId,String filename,byte[] bytes,String actor){
  HotelProperty property=property(companyId,propertyId,true);
  if(bytes==null||bytes.length==0||bytes.length>5*1024*1024)throw bad("CSV-Datei muss zwischen 1 Byte und 5 MB groß sein.");
  String hash=PmsDeliveryService.sha256(bytes);
  PmsBankImport existing=imports.findByProperty_IdAndFileHash(propertyId,hash).orElse(null);
  if(existing!=null)return view(existing);
  String input;
  try{input=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
  catch(CharacterCodingException e){throw bad("CSV-Datei muss UTF-8 verwenden.");}
  if(input.startsWith("\ufeff"))input=input.substring(1);
  List<List<String>> rows=parseCsv(input);
  if(rows.size()<2||rows.size()>2001)throw bad("Eine Datei muss 1 bis 2000 Transaktionen enthalten.");
  if(!rows.get(0).equals(List.of("bankAccount","transactionId","bookingDate","currency","amount","reference","debtorName")))throw bad("CSV-Kopf erwartet: bankAccount;transactionId;bookingDate;currency;amount;reference;debtorName");
  List<PmsBankTransaction> prepared=new ArrayList<>();Set<String> unique=new HashSet<>();
  for(int index=1;index<rows.size();index++){
   List<String> row=rows.get(index);if(row.size()!=7)throw bad("Zeile "+(index+1)+": sieben Spalten erforderlich.");
   PmsBankTransaction t=new PmsBankTransaction();t.setProperty(property);
   t.setBankAccount(value(row.get(0),100,true));t.setExternalId(value(row.get(1),160,true));
   if(!unique.add(t.getBankAccount()+"\u0000"+t.getExternalId()))throw bad("Doppelte Transaktionsreferenz innerhalb der Datei: "+t.getExternalId());
   try{t.setBookingDate(LocalDate.parse(row.get(2)));t.setCurrencyCode(java.util.Currency.getInstance(row.get(3).toUpperCase(Locale.ROOT)).getCurrencyCode());
    if(!row.get(4).matches("-?[0-9]+(?:\\.[0-9]{1,4})?"))throw new IllegalArgumentException();
    t.setAmount(PmsMoney.require(new BigDecimal(row.get(4)),t.getCurrencyCode()));
   }catch(IllegalArgumentException e){throw bad("Zeile "+(index+1)+": ISO-Datum, ISO-Währung und Dezimalbetrag mit Punkt erforderlich.");}
   if(t.getBookingDate().isAfter(LocalDate.now(ZoneId.of(property.getTimezone()))))throw bad("Zukünftige Buchungstage dürfen nicht importiert werden.");
   t.setReference(value(row.get(5),500,false));t.setDebtorName(value(row.get(6),180,false));
   t.setStatus(t.getAmount().signum()>0?"OPEN":"IGNORED");
   PmsBankTransaction previous=transactions.findByProperty_IdAndBankAccountAndExternalId(propertyId,t.getBankAccount(),t.getExternalId()).orElse(null);
   if(previous!=null){if(!same(previous,t))throw conflict("Die Bankreferenz "+t.getExternalId()+" ist bereits mit anderen Daten importiert.");continue;}
   prepared.add(t);
  }
  PmsBankImport batch=new PmsBankImport();batch.setProperty(property);batch.setFilename(value(filename==null?"statement.csv":filename,180,true));batch.setFileHash(hash);
  batch.setRowCount(prepared.size());batch.setCreatedAt(LocalDateTime.now());batch.setCreatedBy(actor);imports.saveAndFlush(batch);
  for(PmsBankTransaction t:prepared){t.setBankImport(batch);transactions.save(t);}transactions.flush();
  audit.append(property,"bank.import_previewed","bank_import",batch.getId().toString(),"{\"newRows\":"+prepared.size()+",\"duplicateRows\":"+(rows.size()-1-prepared.size())+"}");return view(batch);
 }
 @Transactional(readOnly=true) public List<BankImportView> list(Long companyId,Long propertyId){property(companyId,propertyId,false);return imports.findTop50ByProperty_IdOrderByIdDesc(propertyId).stream().map(i->new BankImportView(i.getId(),i.getFilename(),i.getFileHash(),i.getRowCount(),i.getCreatedAt(),List.of())).toList();}
 @Transactional(readOnly=true) public BankImportView get(Long companyId,Long propertyId,Long id){property(companyId,propertyId,false);return view(imports.findByIdAndProperty_Id(id,propertyId).orElseThrow(()->missing("Bankimport")));}
 public BankImportView confirm(Long companyId,Long propertyId,Long importId,BankConfirm request,String actor){
  HotelProperty property=property(companyId,propertyId,true);PmsBankImport batch=imports.findByIdAndProperty_Id(importId,propertyId).orElseThrow(()->missing("Bankimport"));
  if(request.matches()==null||request.matches().isEmpty()||request.matches().size()>500)throw bad("1 bis 500 Zuordnungen erforderlich.");
  Set<Long> seen=new HashSet<>();
  for(BankMatch match:request.matches()){
   if(!seen.add(match.transactionId()))throw bad("Transaktion mehrfach ausgewählt.");
   PmsBankTransaction t=transactions.findByIdAndProperty_Id(match.transactionId(),propertyId).filter(row->row.getBankImport().getId().equals(importId)).orElseThrow(()->missing("Transaktion"));
   if("MATCHED".equals(t.getStatus())){if(!t.getReceivable().getId().equals(match.receivableId()))throw conflict("Transaktion bereits anders ausgeglichen.");continue;}
   if(!"OPEN".equals(t.getStatus())||t.getAmount().signum()<=0)throw conflict("Nur positive offene Gutschriften können Forderungen ausgleichen.");
   PmsReceivable r=receivables.findByIdAndProperty_Id(match.receivableId(),propertyId).orElseThrow(()->missing("Forderung"));
   if(!r.getInvoice().getCurrencyCode().equals(t.getCurrencyCode()))throw conflict("Bankzahlung und Rechnung müssen dieselbe Währung haben. Keine automatische Währungsumrechnung.");
   String bankReference="Bankimport #"+t.getId()+": "+t.getExternalId();
   ledger.settle(property.getCompany(),propertyId,r.getId(),new Settlement(t.getAmount(),bankReference.substring(0,Math.min(180,bankReference.length())),"bank:"+t.getId()),actor,false);
   t.setStatus("MATCHED");t.setReceivable(r);t.setMatchedAt(LocalDateTime.now());t.setMatchedBy(actor);transactions.save(t);
  }
  audit.append(property,"bank.import_confirmed","bank_import",batch.getId().toString(),"{\"rows\":"+seen.size()+"}");return view(batch);
 }
 public ExportView createExport(Long companyId,Long propertyId,ExportCreate request,String actor){
  HotelProperty p=property(companyId,propertyId,true);
  if(request.requestId()==null||!request.requestId().matches("[A-Za-z0-9._:-]{1,120}"))throw bad("Gültige Vorgangs-ID erforderlich.");
  if(request.fromDate()==null||request.toExclusive()==null||!request.toExclusive().isAfter(request.fromDate())||request.toExclusive().isAfter(request.fromDate().plusYears(1)))throw bad("Exportzeitraum muss 1 Tag bis 1 Jahr umfassen.");
  PmsAccountingExportRun old=exports.findByProperty_IdAndRequestKey(propertyId,request.requestId()).orElse(null);
  if(old!=null){if(!old.getFromDate().equals(request.fromDate())||!old.getToExclusive().equals(request.toExclusive()))throw conflict("Vorgangs-ID gehört zu anderem Exportzeitraum.");return exportView(old);}
  byte[] csv=extensions.getObject().accountingExport(p.getCompany(),propertyId,request.fromDate(),request.toExclusive());
  PmsAccountingExportRun run=new PmsAccountingExportRun();run.setProperty(p);run.setRequestKey(request.requestId());run.setFromDate(request.fromDate());run.setToExclusive(request.toExclusive());
  run.setContent(csv);run.setSha256(PmsDeliveryService.sha256(csv));run.setCreatedAt(LocalDateTime.now());run.setCreatedBy(actor);exports.saveAndFlush(run);
  audit.append(p,"accounting.export_generated","export_run",run.getId().toString(),"{\"sha256\":\""+run.getSha256()+"\"}");return exportView(run);
 }
 @Transactional(readOnly=true) public List<ExportView> exports(Long companyId,Long propertyId){property(companyId,propertyId,false);return exports.findTop50ByProperty_IdOrderByIdDesc(propertyId).stream().map(this::exportView).toList();}
 @Transactional(readOnly=true) public byte[] download(Long companyId,Long propertyId,Long id){property(companyId,propertyId,false);return exports.findByIdAndProperty_Id(id,propertyId).orElseThrow(()->missing("Exportlauf")).getContent().clone();}
 public ExportView acknowledge(Long companyId,Long propertyId,Long id,ExportAck request,String actor){
  HotelProperty p=property(companyId,propertyId,true);PmsAccountingExportRun r=exports.findByIdAndProperty_Id(id,propertyId).orElseThrow(()->missing("Exportlauf"));String reference=value(request.reference(),190,true);
  if("ACKNOWLEDGED".equals(r.getStatus())){if(!reference.equals(r.getAcknowledgementReference()))throw conflict("Exportlauf bereits mit anderer Referenz bestätigt.");return exportView(r);}
  r.setStatus("ACKNOWLEDGED");r.setAcknowledgementReference(reference);r.setAcknowledgedAt(LocalDateTime.now());r.setAcknowledgedBy(actor);exports.save(r);
  audit.append(p,"accounting.export_acknowledged","export_run",id.toString(),"{}");return exportView(r);
 }
 private BankImportView view(PmsBankImport batch){
  List<PmsReceivable> open=new ArrayList<>();int page=0;boolean more;LocalDate businessDay=periods.readBusinessDate(batch.getProperty());
  do{var candidates=receivables.searchPage(batch.getProperty().getId(),null,true,false,businessDay,"",PageRequest.of(page++,100));open.addAll(candidates.getContent());more=candidates.hasNext();}while(more);
  List<BankRow> rows=transactions.findByBankImport_IdOrderByIdAsc(batch.getId()).stream().map(t->new BankRow(t.getId(),t.getBankAccount(),t.getExternalId(),t.getBookingDate(),t.getCurrencyCode(),t.getAmount(),t.getReference(),t.getDebtorName(),t.getStatus(),t.getReceivable()==null?null:t.getReceivable().getId(),
    !"OPEN".equals(t.getStatus())?List.of():open.stream().filter(r->r.getInvoice().getStatus()!=InvoiceStatus.CREDITED&&r.getInvoice().getCurrencyCode().equals(t.getCurrencyCode())&&r.balance().compareTo(t.getAmount())>=0&&referenceMatches(t.getReference(),r.getInvoice())).map(r->new BankSuggestion(r.getId(),r.getInvoice().getInvoiceNumber(),r.getOrganization().getName(),r.balance(),"Exakte Rechnungs-/QR-Referenz und passende Währung")).toList())).toList();
  return new BankImportView(batch.getId(),batch.getFilename(),batch.getFileHash(),batch.getRowCount(),batch.getCreatedAt(),rows);
 }
 private static boolean referenceMatches(String reference,PmsInvoice i){if(reference==null)return false;String ref=reference.trim();return ref.equalsIgnoreCase(i.getInvoiceNumber())||(i.getQrReference()!=null&&!i.getQrReference().isBlank()&&ref.replace(" ","").equals(i.getQrReference().replace(" ","")));}
 private ExportView exportView(PmsAccountingExportRun r){return new ExportView(r.getId(),r.getRequestKey(),r.getFromDate(),r.getToExclusive(),r.getStatus(),r.getSha256(),r.getContent().length,r.getCreatedAt(),r.getAcknowledgedAt(),r.getAcknowledgementReference());}
 private static boolean same(PmsBankTransaction a,PmsBankTransaction b){return a.getBookingDate().equals(b.getBookingDate())&&a.getCurrencyCode().equals(b.getCurrencyCode())&&a.getAmount().compareTo(b.getAmount())==0&&Objects.equals(a.getReference(),b.getReference())&&Objects.equals(a.getDebtorName(),b.getDebtorName());}
 private static String value(String value,int max,boolean required){String s=value==null?"":value.trim();if(s.length()>max||(required&&s.isEmpty())||s.indexOf('\u0000')>=0)throw bad("Ungültiger oder zu langer Bankimportwert.");return s;}
 /** RFC-style quoted fields, semicolon delimiter; no formulas or locale-dependent number inference. */
 static List<List<String>> parseCsv(String input){
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
  for(int i=0;i<input.length();i++){char c=input.charAt(i);
   if(quoted){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(c);continue;}
   if(c=='"'){if(field.length()>0||closed)throw bad("Ungültige CSV-Anführungszeichen.");quoted=true;continue;}
   if(c==';'||c=='\n'||c=='\r'){row.add(field.toString());field.setLength(0);closed=false;if(c!=';'){if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;rows.add(List.copyOf(row));row.clear();}continue;}
   if(closed)throw bad("Unerwartete Zeichen nach CSV-Anführungszeichen.");field.append(c);
  }
  if(quoted)throw bad("CSV-Anführungszeichen nicht geschlossen.");if(field.length()>0||closed||!row.isEmpty()){row.add(field.toString());rows.add(List.copyOf(row));}return rows;
 }
 private HotelProperty property(Long cid,Long pid,boolean lock){return (lock?properties.findByIdAndCompany_IdForUpdate(pid,cid):properties.findByIdAndCompany_Id(pid,cid)).orElseThrow(()->missing("Hotel"));}
 private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
 private static ResponseStatusException missing(String name){return new ResponseStatusException(HttpStatus.NOT_FOUND,name+" nicht gefunden.");}
}
