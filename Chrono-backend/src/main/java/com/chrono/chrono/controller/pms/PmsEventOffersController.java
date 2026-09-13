package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsEventOfferDtos.*;
import com.chrono.chrono.services.pms.PmsEventOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/pms/properties/{propertyId}/resource-bookings/{bookingId}/offers")
public class PmsEventOffersController {
 private final PmsEventOfferService service;
 @GetMapping public List<View> list(@PathVariable Long propertyId,@PathVariable Long bookingId,Principal p){return service.list(p.getName(),propertyId,bookingId);}
 @PostMapping public View create(@PathVariable Long propertyId,@PathVariable Long bookingId,@RequestBody Create body,Principal p){return service.create(p.getName(),propertyId,bookingId,body);}
 @PostMapping("/{offerId}/link") public Link link(@PathVariable Long propertyId,@PathVariable Long bookingId,@PathVariable Long offerId,Principal p){return service.link(p.getName(),propertyId,bookingId,offerId);}
 @PostMapping("/{offerId}/apply") public View apply(@PathVariable Long propertyId,@PathVariable Long bookingId,@PathVariable Long offerId,Principal p){return service.apply(p.getName(),propertyId,bookingId,offerId);}
}
