package com.chrono.chrono.security;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/** A validated, request-local company view. Never changes the account's company. */
public final class CompanyScope {
    public static final String HEADER = "X-Chrono-Company";
    public static final String ATTRIBUTE = CompanyScope.class.getName();
    public record Selection(String username, Company company) {}
    private CompanyScope() {}

    public static Selection selection() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return (Selection) attributes.getRequest().getAttribute(ATTRIBUTE);
        }
        return null;
    }

    public static Company companyFor(User user) {
        Selection selected = selection();
        return user != null && selected != null && Objects.equals(selected.username(), user.getUsername())
                ? selected.company() : user == null ? null : user.getCompany();
    }

    public static boolean isUnrestrictedSuperAdmin(User user) {
        return selection() == null && user != null && user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }

    public static void requireCompany(Company company) {
        Selection selected = selection();
        if (selected != null && (company == null || !Objects.equals(selected.company().getId(), company.getId()))) {
            throw new AccessDeniedException("Der Datensatz gehört nicht zur ausgewählten Firma.");
        }
    }

    public static void requireTarget(User user) {
        requireCompany(user == null ? null : user.getCompany());
    }
}
