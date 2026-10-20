package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.security.CompanyScope;
import com.chrono.chrono.services.AccessControlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyScopeMvcConfigurationTest {
    final UserRepository users = mock(UserRepository.class);
    final CompanyRepository companies = mock(CompanyRepository.class);
    final CompanyScopeMvcConfiguration config = new CompanyScopeMvcConfiguration(users, companies);
    final MockHttpServletRequest request = new MockHttpServletRequest();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
    Company company(long id) { Company company = new Company(); company.setId(id); return company; }
    User login(String role) {
        User actor = new User(); actor.setUsername("root"); actor.setCompany(company(1)); actor.setRoles(Set.of(new Role(role)));
        when(users.findByUsernameWithPermissionContext("root")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("root", null, java.util.List.of()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return actor;
    }
    @Test void requiresAuthenticatedSuperadminForASelection() {
        request.addHeader(CompanyScope.HEADER, "2");
        assertThatThrownBy(() -> config.selectCompany(request)).isInstanceOf(ResponseStatusException.class);
        login("ROLE_ADMIN");
        assertThatThrownBy(() -> config.selectCompany(request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(companies);
    }
    @Test void rejectsMalformedAndMissingCompanies() {
        for (String id : new String[]{"0", "-1", "1,2", "abc", "99999999999999999999"}) {
            request.removeHeader(CompanyScope.HEADER); request.addHeader(CompanyScope.HEADER, id);
            assertThatThrownBy(() -> config.selectCompany(request)).isInstanceOf(ResponseStatusException.class);
        }
        login("ROLE_SUPERADMIN");
        request.removeHeader(CompanyScope.HEADER); request.addHeader(CompanyScope.HEADER, "2");
        assertThatThrownBy(() -> config.selectCompany(request)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void selectedCompanyNeverMutatesAccountAndDoesNotLeakIntoNextRequest() {
        User actor = login("ROLE_SUPERADMIN");
        Company selected = company(2);
        when(companies.findWithFeaturesById(2L)).thenReturn(Optional.of(selected));
        request.addHeader(CompanyScope.HEADER, "2");
        assertThat(config.selectCompany(request)).isTrue();
        assertThat(CompanyScope.companyFor(actor)).isSameAs(selected);
        assertThat(actor.getCompany().getId()).isEqualTo(1);
        User own = new User(); own.setUsername("worker"); own.setCompany(selected);
        User other = new User(); other.setUsername("other"); other.setCompany(company(3));
        AccessControlService access = new AccessControlService(users);
        assertThat(access.requireCompanyIdForTenantAdmin(actor)).isEqualTo(2);
        assertThat(access.canAccessUser(actor, own)).isTrue();
        assertThatThrownBy(() -> access.requirePayrollAccess(actor, other)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireCanManageUser(actor, other)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertThat(CompanyScope.companyFor(actor).getId()).isEqualTo(1);
        assertThat(access.requireCompanyIdForTenantAdmin(actor)).isNull();
    }
    @Test void requestsWithoutSelectionKeepExistingBehaviour() {
        assertThat(config.selectCompany(request)).isTrue();
        verifyNoInteractions(users, companies);
    }
}
