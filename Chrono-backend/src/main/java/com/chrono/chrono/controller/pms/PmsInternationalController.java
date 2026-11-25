package com.chrono.chrono.controller.pms;
import com.chrono.chrono.services.pms.PmsInternationalSettingsService;
import com.chrono.chrono.services.pms.PmsStructuredInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/pms/properties/{propertyId}")
public class PmsInternationalController {
 private final PmsInternationalSettingsService settings;private final PmsStructuredInvoiceService invoices;
 @GetMapping("/international-settings") public PmsInternationalSettingsService.Settings get(@PathVariable Long propertyId,Principal p){return settings.get(p.getName(),propertyId);}
 @PutMapping("/international-settings") public PmsInternationalSettingsService.Settings save(@PathVariable Long propertyId,@RequestBody PmsInternationalSettingsService.Settings input,Principal p){return settings.save(p.getName(),propertyId,input);}
 @GetMapping("/invoices/{invoiceId}/structured-invoice") public PmsStructuredInvoiceService.Validation check(@PathVariable Long propertyId,@PathVariable Long invoiceId,Principal p){return invoices.check(p.getName(),propertyId,invoiceId);}
 @PostMapping("/invoices/{invoiceId}/structured-invoice") public PmsStructuredInvoiceService.Validation create(@PathVariable Long propertyId,@PathVariable Long invoiceId,@RequestBody(required=false) PmsStructuredInvoiceService.Options options,Principal p){return invoices.create(p.getName(),propertyId,invoiceId,options);}
 @GetMapping("/invoices/{invoiceId}/ubl.xml") public ResponseEntity<byte[]> download(@PathVariable Long propertyId,@PathVariable Long invoiceId,Principal p){return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).header("Content-Disposition","attachment; filename=\"invoice-"+invoiceId+".xml\"").header("Cache-Control","no-store").body(invoices.download(p.getName(),propertyId,invoiceId));}
}
