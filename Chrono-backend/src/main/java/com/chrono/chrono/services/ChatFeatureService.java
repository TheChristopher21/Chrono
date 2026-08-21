package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.utils.RegistrationFeatures;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ChatFeatureService {

    public boolean isChatbotEnabled(User user) {
        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }
        if (user.getCompany() == null) {
            return false;
        }
        Set<String> features = new LinkedHashSet<>(RegistrationFeatures.ALWAYS_AVAILABLE_FEATURES);
        features.addAll(RegistrationFeatures.sanitizeOptionalFeatures(user.getCompany().getEnabledFeatures()));
        return features.contains("chatbot");
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role != null && "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }
}
