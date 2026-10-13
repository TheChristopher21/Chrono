package com.chrono.chrono.controller.pms;
import com.chrono.chrono.dto.pms.PmsEventOfferDtos.*;
import com.chrono.chrono.services.pms.PmsEventOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/public/pms/event-offers/{token}")
public class PmsPublicEventOfferController {
 private final PmsEventOfferService service;
 @GetMapping public ResponseEntity<View> view(@PathVariable String token){return response(service.publicView(token));}
 @PostMapping("/decision") public ResponseEntity<View> decide(@PathVariable String token,@RequestBody Decision body){return response(service.decide(token,body));}
 private ResponseEntity<View> response(View view){return ResponseEntity.ok().header("Cache-Control","no-store").header("Referrer-Policy","no-referrer").body(view);}
}
