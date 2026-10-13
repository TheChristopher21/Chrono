package com.chrono.chrono.controller;

import com.chrono.chrono.dto.MobileAppFeedbackDTO;
import com.chrono.chrono.dto.MobileAppFeedbackRequest;
import com.chrono.chrono.entities.MobileAppFeedback;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.MobileAppFeedbackRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/mobile-feedback")
public class MobileAppFeedbackController {

    private final MobileAppFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public MobileAppFeedbackController(MobileAppFeedbackRepository feedbackRepository,
                                       UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MobileAppFeedbackRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String message = request != null ? clean(request.getMessage(), 5000) : "";
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body("Feedback text is required.");
        }

        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MobileAppFeedback feedback = new MobileAppFeedback();
        feedback.setUser(user);
        feedback.setCompany(user.getCompany());
        feedback.setUsername(user.getUsername());
        feedback.setDisplayName(displayName(user));
        feedback.setMessage(message);
        feedback.setAppMenuKey(clean(request.getAppMenuKey(), 80));
        feedback.setAppMenuTitle(clean(request.getAppMenuTitle(), 120));
        feedback.setAppMenuGroup(clean(request.getAppMenuGroup(), 120));
        feedback.setAppVersionName(clean(request.getAppVersionName(), 40));
        feedback.setAppVersionCode(request.getAppVersionCode());
        feedback.setDeviceInfo(clean(request.getDeviceInfo(), 300));

        MobileAppFeedback saved = feedbackRepository.save(feedback);
        return ResponseEntity.status(HttpStatus.CREATED).body(MobileAppFeedbackDTO.fromEntity(saved));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<MobileAppFeedbackDTO> list(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        int size = Math.max(1, Math.min(limit, 250));
        return feedbackRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(MobileAppFeedbackDTO::fromEntity)
                .toList();
    }

    private static String displayName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (first + " " + last).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
