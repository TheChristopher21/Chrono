package com.chrono.chrono.config;

import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.security.CompanyScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class CompanyScopeMvcConfiguration implements WebMvcConfigurer {
    private final UserRepository users;
    private final CompanyRepository companies;
    public CompanyScopeMvcConfiguration(UserRepository users, CompanyRepository companies) {
        this.users = users;
        this.companies = companies;
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                return selectCompany(request);
            }
        }).addPathPatterns("/api/**").order(-100);
    }

    boolean selectCompany(HttpServletRequest request) {
        String header = request.getHeader(CompanyScope.HEADER);
        if (header == null) return true;
        if (!header.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Firmenauswahl.");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Firmenwechsel erfordert Superadmin-Rechte.");
        }
        var actor = users.findByUsernameWithPermissionContext(authentication.getName())
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (actor.getRoles().stream().noneMatch(role -> "ROLE_SUPERADMIN".equals(role.getRoleName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Firmenwechsel erfordert Superadmin-Rechte.");
        }
        long companyId;
        try { companyId = Long.parseLong(header); }
        catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Firmenauswahl."); }
        var company = companies.findWithFeaturesById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Firma nicht gefunden."));
        request.setAttribute(CompanyScope.ATTRIBUTE, new CompanyScope.Selection(actor.getUsername(), company));
        return true;
    }
}
