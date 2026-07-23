package com.chrono.chrono.config;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ApiPagePermissionInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final List<PageRule> RULES = List.of(
            rule("/api/accounting/receivables/open",
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING, UserPermissionService.PAGE_BANKING),
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING)),
            rule("/api/accounting/payables/open",
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING, UserPermissionService.PAGE_BANKING),
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING)),
            rule("/api/accounting/**",
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING),
                    pages(UserPermissionService.PAGE_ADMIN_ACCOUNTING)),
            rule("/api/banking/**",
                    pages(UserPermissionService.PAGE_BANKING),
                    pages(UserPermissionService.PAGE_BANKING)),
            rule("/api/crm/**",
                    pages(UserPermissionService.PAGE_CRM),
                    pages(UserPermissionService.PAGE_CRM)),
            rule("/api/chrono2/**",
                    pages(UserPermissionService.PAGE_CHRONO_TWO),
                    pages(UserPermissionService.PAGE_CHRONO_TWO)),
            rule("/api/supply-chain/**",
                    pages(UserPermissionService.PAGE_SUPPLY_CHAIN),
                    pages(UserPermissionService.PAGE_SUPPLY_CHAIN)),

            rule("/api/customers/recent",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_CRM,
                            UserPermissionService.PAGE_ADMIN_CUSTOMERS, UserPermissionService.PAGE_ADMIN_PROJECTS),
                    pages(UserPermissionService.PAGE_ADMIN_CUSTOMERS, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_CRM)),
            rule("/api/customers/**",
                    pages(UserPermissionService.PAGE_ADMIN_CUSTOMERS, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_CRM),
                    pages(UserPermissionService.PAGE_ADMIN_CUSTOMERS, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_CRM)),
            rule("/api/projects/**",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_ADMIN_PROJECT_REPORT),
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS, UserPermissionService.PAGE_ADMIN_TASKS)),
            rule("/api/tasks/**",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_ADMIN_TASKS),
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS, UserPermissionService.PAGE_ADMIN_TASKS)),
            rule("/api/integrations/**",
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS),
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/billing/**",
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS),
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/audit/**",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS,
                            UserPermissionService.PAGE_ADMIN_ANALYTICS, UserPermissionService.PAGE_SUPPLY_CHAIN),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),

            rule("/api/report/analytics/projects",
                    pages(UserPermissionService.PAGE_ADMIN_PROJECTS, UserPermissionService.PAGE_ADMIN_PROJECT_REPORT,
                            UserPermissionService.PAGE_ADMIN_ANALYTICS),
                    pages()),
            rule("/api/report/project/**",
                    pages(UserPermissionService.PAGE_ADMIN_PROJECT_REPORT),
                    pages()),
            rule("/api/report/timesheet/pdf",
                    pages(UserPermissionService.PAGE_PRINT_REPORT, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/report/timesheet/csv",
                    pages(UserPermissionService.PAGE_PRINT_REPORT, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/report/timesheet/ics",
                    pages(UserPermissionService.PAGE_PRINT_REPORT, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),

            rule("/api/dashboard/**",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/users/profile/**",
                    pages(UserPermissionService.PAGE_PERSONAL_DATA, UserPermissionService.PAGE_DASHBOARD,
                            UserPermissionService.PAGE_PRINT_REPORT, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/user/change-password",
                    pages(UserPermissionService.PAGE_PERSONAL_DATA, UserPermissionService.PAGE_ADMIN_CHANGE_PASSWORD),
                    pages(UserPermissionService.PAGE_PERSONAL_DATA, UserPermissionService.PAGE_ADMIN_CHANGE_PASSWORD)),
            rule("/api/user/update",
                    pages(UserPermissionService.PAGE_PERSONAL_DATA),
                    pages(UserPermissionService.PAGE_PERSONAL_DATA)),
            rule("/api/users/**",
                    pages(UserPermissionService.PAGE_ADMIN_USERS),
                    pages(UserPermissionService.PAGE_ADMIN_USERS)),

            rule("/api/timetracking/history",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PRINT_REPORT,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/timetracking/daily-summary",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PRINT_REPORT,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/timetracking/period-summary",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PRINT_REPORT,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/timetracking/report",
                    pages(UserPermissionService.PAGE_PRINT_REPORT, UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/timetracking/work-difference",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PRINT_REPORT,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/timetracking/daily-note",
                    pages(UserPermissionService.PAGE_DASHBOARD),
                    pages(UserPermissionService.PAGE_DASHBOARD)),
            rule("/api/timetracking/entry/*/customer",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/timetracking/day/customer",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/timetracking/range/customer",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/timetracking/entry/*/project",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/timetracking/day/project",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_ADMIN_PROJECTS)),
            rule("/api/timetracking/entry/*/approve",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/timetracking/entry/*/revoke-approval",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),

            rule("/api/payslips/my",
                    pages(UserPermissionService.PAGE_PAYSLIPS, UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages()),
            rule("/api/payslips/pdf/**",
                    pages(UserPermissionService.PAGE_PAYSLIPS, UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages()),
            rule("/api/payslips/user/**",
                    pages(UserPermissionService.PAGE_PAYSLIPS, UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages()),
            rule("/api/payslips/admin/**",
                    pages(UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages(UserPermissionService.PAGE_ADMIN_PAYSLIPS)),
            rule("/api/payslips/**",
                    pages(UserPermissionService.PAGE_PAYSLIPS, UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages(UserPermissionService.PAGE_ADMIN_PAYSLIPS)),

            rule("/api/admin/users",
                    pages(UserPermissionService.PAGE_ADMIN_USERS, UserPermissionService.PAGE_ADMIN_DASHBOARD,
                            UserPermissionService.PAGE_ADMIN_ANALYTICS, UserPermissionService.PAGE_ADMIN_PAYSLIPS,
                            UserPermissionService.PAGE_ADMIN_SCHEDULE, UserPermissionService.PAGE_ADMIN_PRINT_SCHEDULE),
                    pages(UserPermissionService.PAGE_ADMIN_USERS)),
            rule("/api/admin/users/**",
                    pages(UserPermissionService.PAGE_ADMIN_USERS),
                    pages(UserPermissionService.PAGE_ADMIN_USERS)),
            rule("/api/admin/company/logo",
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS, UserPermissionService.PAGE_ADMIN_PAYSLIPS),
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS, UserPermissionService.PAGE_ADMIN_PAYSLIPS)),
            rule("/api/admin/company/settings",
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS),
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS)),
            rule("/api/admin/company/holiday-catalog",
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS),
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS)),
            rule("/api/admin/company",
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS),
                    pages(UserPermissionService.PAGE_COMPANY_SETTINGS)),
            rule("/api/admin/knowledge/**",
                    pages(UserPermissionService.PAGE_ADMIN_KNOWLEDGE),
                    pages(UserPermissionService.PAGE_ADMIN_KNOWLEDGE)),
            rule("/api/admin/schedule/**",
                    pages(UserPermissionService.PAGE_ADMIN_SCHEDULE),
                    pages(UserPermissionService.PAGE_ADMIN_SCHEDULE)),
            rule("/api/admin/shift-definitions/**",
                    pages(UserPermissionService.PAGE_ADMIN_SCHEDULE, UserPermissionService.PAGE_ADMIN_PRINT_SCHEDULE,
                            UserPermissionService.PAGE_ADMIN_SHIFT_RULES),
                    pages(UserPermissionService.PAGE_ADMIN_SHIFT_RULES)),
            rule("/api/admin/schedule-rules/**",
                    pages(UserPermissionService.PAGE_ADMIN_SHIFT_RULES),
                    pages(UserPermissionService.PAGE_ADMIN_SHIFT_RULES)),
            rule("/api/admin/timetracking/import",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_IMPORT_TIMES)),
            rule("/api/admin/timetracking/import/json",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_IMPORT_TIMES)),
            rule("/api/admin/timetracking/rebuild-balances",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD, UserPermissionService.PAGE_ADMIN_IMPORT_TIMES)),
            rule("/api/admin/timetracking/**",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/admin/user-holiday-options/**",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),

            rule("/api/vacation/all",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD, UserPermissionService.PAGE_ADMIN_SCHEDULE),
                    pages()),
            rule("/api/vacation/adminCreate",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/vacation/companyCreate",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/vacation/approve/**",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/vacation/deny/**",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/correction/all",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/correction/approve/**",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/correction/deny/**",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/sick-leave/company",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/sick-leave/user/**",
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/sick-leave/my",
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PERSONAL_DATA,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD),
                    pages()),
            rule("/api/sick-leave/report",
                    pages(),
                    pages(UserPermissionService.PAGE_DASHBOARD, UserPermissionService.PAGE_PERSONAL_DATA,
                            UserPermissionService.PAGE_ADMIN_DASHBOARD)),
            rule("/api/sick-leave/**",
                    pages(),
                    pages(UserPermissionService.PAGE_ADMIN_DASHBOARD))
    );

    private final UserRepository userRepository;
    private final UserPermissionService userPermissionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiPagePermissionInterceptor(UserRepository userRepository,
                                        UserPermissionService userPermissionService) {
        this.userRepository = userRepository;
        this.userPermissionService = userPermissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = normalizedPath(request);
        Optional<PageRule> matchedRule = RULES.stream()
                .filter(rule -> pathMatcher.match(rule.pattern(), path))
                .findFirst();

        if (matchedRule.isEmpty()) {
            return true;
        }

        String method = request.getMethod().toUpperCase();
        List<String> allowedPages;
        String requiredLevel;
        if (READ_METHODS.contains(method)) {
            allowedPages = matchedRule.get().readPages();
            requiredLevel = UserPermissionService.ACCESS_VIEW;
        } else if (WRITE_METHODS.contains(method)) {
            allowedPages = matchedRule.get().writePages();
            requiredLevel = UserPermissionService.ACCESS_MANAGE;
        } else {
            return true;
        }

        if (allowedPages.isEmpty()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        User user = userRepository.findByUsernameWithPermissionContext(authentication.getName()).orElse(null);
        if (user == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        boolean permitted = allowedPages.stream()
                .anyMatch(pageKey -> userPermissionService.hasPageAccess(user, pageKey, requiredLevel));
        if (!permitted) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        return true;
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private static PageRule rule(String pattern, List<String> readPages, List<String> writePages) {
        return new PageRule(pattern, readPages, writePages);
    }

    private static List<String> pages(String... pageKeys) {
        return List.of(pageKeys);
    }

    private record PageRule(String pattern, List<String> readPages, List<String> writePages) {
    }
}
