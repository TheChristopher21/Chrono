package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsDeliveryDtos.*;
import com.chrono.chrono.dto.pms.PmsBillingAutomationDtos.*;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.chrono.chrono.services.pms.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;
import java.io.IOException;

@RestController @RequestMapping("/api/pms/properties/{propertyId}/billing")
public class PmsBillingAutomationController {
 private final PmsPropertyAccessService access; private final HotelPropertyRepository properties;
 private final PmsBillingSettingsService settings; private final PmsDeliveryService delivery;
 private final PmsDunningService reminders; private final PmsBankReconciliationService bank;
 private final GuestCommunicationRepository communications;
 public PmsBillingAutomationController(PmsPropertyAccessService access,HotelPropertyRepository properties,PmsBillingSettingsService settings,PmsDeliveryService delivery,PmsDunningService reminders,PmsBankReconciliationService bank,GuestCommunicationRepository communications){
  this.access=access;this.properties=properties;this.settings=settings;this.delivery=delivery;this.reminders=reminders;this.bank=bank;this.communications=communications;
 }
 @GetMapping("/settings") public Settings settings(@PathVariable Long propertyId,Principal p){var actor=allowed(p,propertyId,false);return settings.view(property(actor.companyId(),propertyId));}
 @PutMapping("/settings") public Settings settings(@PathVariable Long propertyId,@Valid @RequestBody Settings input,Principal p){var actor=access.requireMaster(username(p));access.require(actor,propertyId,"FINANCE",true);return settings.save(property(actor.companyId(),propertyId),input,username(p));}
 @GetMapping("/deliveries") public DeliveryPage deliveries(@PathVariable Long propertyId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,Principal p){allowed(p,propertyId,false);return delivery.list(propertyId,page,size);}
 @PostMapping("/invoices/{invoiceId}/send") public DeliveryView invoice(@PathVariable Long propertyId,@PathVariable Long invoiceId,@Valid @RequestBody InvoiceSend input,Principal p){var actor=allowed(p,propertyId,true);return delivery.queueInvoice(actor.companyId(),propertyId,invoiceId,input,username(p));}
 @PostMapping("/deliveries/{id}/retry") public DeliveryView retry(@PathVariable Long propertyId,@PathVariable Long id,@Valid @RequestBody Retry input,Principal p){var actor=allowed(p,propertyId,true);return delivery.retry(actor.companyId(),propertyId,id,input,username(p));}
 @PostMapping("/communications/{id}/send") public DeliveryView communication(@PathVariable Long propertyId,@PathVariable Long id,Principal p){
  var actor=allowed(p,propertyId,true);GuestCommunication c=communications.findByIdAndProperty_Company_Id(id,actor.companyId()).filter(v->v.getProperty().getId().equals(propertyId)&&v.getDirection()==CommunicationDirection.OUTBOUND).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ausgehende Nachricht nicht gefunden."));
  if(c.getStatus()==CommunicationStatus.SENT)throw new ResponseStatusException(HttpStatus.CONFLICT,"Nachricht wurde bereits versandt.");return delivery.queueCommunication(c);
 }
 @PostMapping(path="/deliveries",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public DeliveryView compose(@PathVariable Long propertyId,@RequestParam String requestId,@RequestParam String recipient,@RequestParam String subject,@RequestParam String body,@RequestPart(required=false) List<MultipartFile> attachments,Principal p)throws IOException{
  var actor=allowed(p,propertyId,true);List<Attachment> files=new ArrayList<>();long size=0;
  if(attachments!=null)for(MultipartFile file:attachments){size+=file.getSize();if(size>8*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Anhänge dürfen höchstens 8 MB umfassen.");files.add(new Attachment(file.getOriginalFilename(),file.getContentType(),file.getBytes()));}
  return delivery.enqueue(property(actor.companyId(),propertyId),"MANUAL","manual:"+requestId,recipient,subject,body,files,null,null,username(p));
 }
 @GetMapping("/reminders/preview") public List<ReminderCandidate> preview(@PathVariable Long propertyId,Principal p){var a=allowed(p,propertyId,false);return reminders.preview(a.companyId(),propertyId);}
 @GetMapping("/reminders") public List<ReminderView> reminders(@PathVariable Long propertyId,Principal p){allowed(p,propertyId,false);return reminders.history(propertyId);}
 @PostMapping("/reminders/run") public ReminderRun run(@PathVariable Long propertyId,Principal p){var a=allowed(p,propertyId,true);return reminders.run(a.companyId(),propertyId,username(p));}
 @GetMapping("/bank-imports") public List<BankImportView> imports(@PathVariable Long propertyId,Principal p){var a=allowed(p,propertyId,false);return bank.list(a.companyId(),propertyId);}
 @PostMapping(path="/bank-imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public BankImportView upload(@PathVariable Long propertyId,@RequestPart MultipartFile file,Principal p)throws IOException{var a=allowed(p,propertyId,true);if(file.getSize()>5*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CSV-Datei zu groß.");return bank.preview(a.companyId(),propertyId,file.getOriginalFilename(),file.getBytes(),username(p));}
 @GetMapping("/bank-imports/{id}") public BankImportView bankImport(@PathVariable Long propertyId,@PathVariable Long id,Principal p){var a=allowed(p,propertyId,false);return bank.get(a.companyId(),propertyId,id);}
 @PostMapping("/bank-imports/{id}/confirm") public BankImportView confirm(@PathVariable Long propertyId,@PathVariable Long id,@Valid @RequestBody BankConfirm input,Principal p){var a=allowed(p,propertyId,true);return bank.confirm(a.companyId(),propertyId,id,input,username(p));}
 @GetMapping("/export-runs") public List<ExportView> exports(@PathVariable Long propertyId,Principal p){var a=allowed(p,propertyId,false);return bank.exports(a.companyId(),propertyId);}
 @PostMapping("/export-runs") public ExportView export(@PathVariable Long propertyId,@Valid @RequestBody ExportCreate input,Principal p){var a=allowed(p,propertyId,true);return bank.createExport(a.companyId(),propertyId,input,username(p));}
 @GetMapping("/export-runs/{id}/download") public ResponseEntity<byte[]> download(@PathVariable Long propertyId,@PathVariable Long id,Principal p){var a=allowed(p,propertyId,false);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=accounting-run-"+id+".csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bank.download(a.companyId(),propertyId,id));}
 @PostMapping("/export-runs/{id}/ack") public ExportView ack(@PathVariable Long propertyId,@PathVariable Long id,@Valid @RequestBody ExportAck input,Principal p){var a=allowed(p,propertyId,true);return bank.acknowledge(a.companyId(),propertyId,id,input,username(p));}
 private PmsPropertyAccessService.Access allowed(Principal p,Long id,boolean write){var a=access.access(username(p));access.require(a,id,"FINANCE",write);return a;}
 private HotelProperty property(Long cid,Long pid){return properties.findByIdAndCompany_Id(pid,cid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));}
 private String username(Principal p){if(p==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentifizierung erforderlich.");return p.getName();}
}
