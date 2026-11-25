package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UiPreferenceResponse;
import com.chrono.chrono.dto.UiPreferenceUpdateRequest;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.services.UserUiPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/ui/preferences")
public class UserUiPreferenceController {

    private final UserUiPreferenceService preferenceService;

    public UserUiPreferenceController(UserUiPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/{area}")
    public ResponseEntity<UiPreferenceResponse> get(
            @PathVariable String area,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) Long propertyId,
            Principal principal
    ) {
        return ResponseEntity.ok(preferenceService.get(
                principal,
                UserUiPreferenceArea.fromApiValue(area),
                context,
                propertyId
        ));
    }

    @PutMapping("/{area}")
    public ResponseEntity<UiPreferenceResponse> put(
            @PathVariable String area,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) Long propertyId,
            @Valid @RequestBody UiPreferenceUpdateRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(preferenceService.put(
                principal,
                UserUiPreferenceArea.fromApiValue(area),
                context,
                propertyId,
                request
        ));
    }

    @DeleteMapping("/{area}")
    public ResponseEntity<Void> delete(
            @PathVariable String area,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) Long propertyId,
            @RequestParam long revision,
            Principal principal
    ) {
        preferenceService.delete(
                principal,
                UserUiPreferenceArea.fromApiValue(area),
                context,
                propertyId,
                revision
        );
        return ResponseEntity.noContent().build();
    }
}
