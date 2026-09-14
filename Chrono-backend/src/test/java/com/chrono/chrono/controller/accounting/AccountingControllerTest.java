package com.chrono.chrono.controller.accounting;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.AssetRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import com.chrono.chrono.services.accounting.AccountsReceivableService;
import com.chrono.chrono.services.accounting.AssetManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingControllerTest {

    private AccountingService accountingService;
    private AssetManagementService assetManagementService;
    private AssetRepository assetRepository;
    private UserRepository userRepository;
    private AccountingController controller;

    @BeforeEach
    void setUp() {
        accountingService = mock(AccountingService.class);
        AccountsReceivableService accountsReceivableService = mock(AccountsReceivableService.class);
        AccountsPayableService accountsPayableService = mock(AccountsPayableService.class);
        assetManagementService = mock(AssetManagementService.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        assetRepository = mock(AssetRepository.class);
        userRepository = mock(UserRepository.class);

        controller = new AccountingController(
                accountingService,
                accountsReceivableService,
                accountsPayableService,
                assetManagementService,
                accountRepository,
                assetRepository,
                userRepository,
                new UserPermissionService()
        );
    }

    @Test
    void accountingViewPermissionCanReadAccountingSetupEndpoints() {
        User user = accountingAdmin(UserPermissionService.ACCESS_VIEW);
        when(userRepository.findByUsernameWithPermissionContext("admin")).thenReturn(Optional.of(user));
        when(accountingService.listAccounts()).thenReturn(List.of());
        when(accountingService.listEntries(any(Pageable.class))).thenReturn(Page.empty());
        when(assetManagementService.listAssets()).thenReturn(List.of());

        ResponseEntity<?> accounts = controller.listAccounts(principal("admin"));
        ResponseEntity<?> journal = controller.listJournal(0, 5, principal("admin"));
        ResponseEntity<?> assets = controller.listAssets(principal("admin"));

        assertEquals(200, accounts.getStatusCode().value());
        assertEquals(200, journal.getStatusCode().value());
        assertEquals(200, assets.getStatusCode().value());
        verify(accountingService).listAccounts();
        verify(accountingService).listEntries(any(Pageable.class));
        verify(assetManagementService).listAssets();
    }

    @Test
    void accountingViewPermissionCannotWriteAccountingSetup() {
        User user = accountingAdmin(UserPermissionService.ACCESS_VIEW);
        when(userRepository.findByUsernameWithPermissionContext("admin")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.depreciate(42L, principal("admin"))
        );

        assertEquals(403, ex.getStatusCode().value());
    }

    private User accountingAdmin(String accessLevel) {
        Company company = new Company("Demo Company");
        company.setEnabledFeatures(Set.of("accounting"));

        User user = new User();
        user.setUsername("admin");
        user.setCompany(company);
        user.setRoles(Set.of(new Role("ROLE_ADMIN")));
        user.setPagePermissions(Map.of(UserPermissionService.PAGE_ADMIN_ACCOUNTING, accessLevel));
        return user;
    }

    private Principal principal(String username) {
        return () -> username;
    }
}
