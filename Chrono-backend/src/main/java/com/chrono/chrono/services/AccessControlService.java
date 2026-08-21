package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class AccessControlService {

    private final UserRepository userRepository;

    public AccessControlService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireAuthenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("Authentication required");
        }
        return requireUser(principal.getName());
    }

    public User requireUser(String username) {
        return userRepository.findByUsernameWithPermissionContext(username)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }

    public User requireTargetUser(String username) {
        return userRepository.findByUsernameWithPermissionContext(username)
                .orElseThrow(() -> new AccessDeniedException("Target user not found"));
    }

    public User requireTargetUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Target user not found"));
    }

    public boolean hasAnyRole(User user, String... roles) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> Arrays.asList(roles).contains(role.getRoleName()));
    }

    public boolean isSuperAdmin(User user) {
        return hasAnyRole(user, "ROLE_SUPERADMIN");
    }

    public boolean isAdmin(User user) {
        return hasAnyRole(user, "ROLE_ADMIN", "ROLE_SUPERADMIN");
    }

    public boolean isPayrollAdmin(User user) {
        return hasAnyRole(user, "ROLE_PAYROLL_ADMIN", "ROLE_ADMIN", "ROLE_SUPERADMIN");
    }

    public boolean isSelf(User actor, User target) {
        return actor != null
                && target != null
                && actor.getUsername() != null
                && actor.getUsername().equals(target.getUsername());
    }

    public boolean sameCompany(User actor, User target) {
        return actor != null
                && target != null
                && sameCompany(actor.getCompany(), target.getCompany());
    }

    public boolean sameCompany(Company actorCompany, Company targetCompany) {
        return actorCompany != null
                && targetCompany != null
                && Objects.equals(actorCompany.getId(), targetCompany.getId());
    }

    public boolean canAccessUser(User actor, User target) {
        return isSelf(actor, target)
                || isSuperAdmin(actor)
                || (isAdmin(actor) && sameCompany(actor, target));
    }

    public void requireCanAccessUser(User actor, User target) {
        if (!canAccessUser(actor, target)) {
            throw new AccessDeniedException("Not allowed to access this user");
        }
    }

    public void requireCanManageUser(User actor, User target) {
        if (isSuperAdmin(actor)) {
            return;
        }
        if (hasAnyRole(target, "ROLE_SUPERADMIN")) {
            throw new AccessDeniedException("Not allowed to manage this user");
        }
        if (!isAdmin(actor) || !sameCompany(actor, target)) {
            throw new AccessDeniedException("Not allowed to manage this user");
        }
        if (hasAnyRole(target, "ROLE_ADMIN") && !isSuperAdmin(actor)) {
            throw new AccessDeniedException("Not allowed to manage equal or higher roles");
        }
    }

    public void requirePayrollAccess(User actor, User target) {
        if (isSuperAdmin(actor)) {
            return;
        }
        if (!isPayrollAdmin(actor) || !sameCompany(actor, target)) {
            throw new AccessDeniedException("Not allowed to access payroll data");
        }
    }

    public void requireCompanyScope(User actor, Company targetCompany) {
        if (isSuperAdmin(actor)) {
            return;
        }
        if (targetCompany == null || actor.getCompany() == null || !sameCompany(actor.getCompany(), targetCompany)) {
            throw new AccessDeniedException("Not allowed to access this company data");
        }
    }

    public Long requireCompanyIdForTenantAdmin(User actor) {
        if (isSuperAdmin(actor)) {
            return null;
        }
        if (actor.getCompany() == null || actor.getCompany().getId() == null) {
            throw new AccessDeniedException("Company scoped admin required");
        }
        return actor.getCompany().getId();
    }

    public List<User> visibleUsersForAdmin(User actor) {
        if (isSuperAdmin(actor)) {
            return userRepository.findOperationalUsersDeletedFalse();
        }
        Long companyId = requireCompanyIdForTenantAdmin(actor);
        return userRepository.findOperationalUsersByCompanyIdAndDeletedFalse(companyId);
    }
}
