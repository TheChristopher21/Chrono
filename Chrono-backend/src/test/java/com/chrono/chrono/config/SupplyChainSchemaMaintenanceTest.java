package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SupplyChainSchemaMaintenanceTest {

    private JdbcTemplate jdbcTemplate;
    private CompanyRepository companyRepository;
    private SupplyChainSchemaMaintenance maintenance;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        companyRepository = mock(CompanyRepository.class);
        maintenance = new SupplyChainSchemaMaintenance(
                jdbcTemplate,
                mock(DataSource.class),
                companyRepository,
                false
        );
    }

    @Test
    void alignExistingCompanyAssignments_backfillsSingleCompanyData() {
        when(companyRepository.findAll()).thenReturn(List.of(company(7L)));
        stubMissingAssignmentCounts(3L, 1L, 0L, 0L, 0L, 0L, 0L);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(maintenance, "alignExistingCompanyAssignments"));

        verify(jdbcTemplate).update("UPDATE inv_products SET company_id = ? WHERE company_id IS NULL", 7L);
        verify(jdbcTemplate).update("UPDATE inv_warehouses SET company_id = ? WHERE company_id IS NULL", 7L);
        verify(jdbcTemplate, never()).update(eq("UPDATE scm_purchase_orders SET company_id = ? WHERE company_id IS NULL"), anyLong());
    }

    @Test
    void alignExistingCompanyAssignments_allowsStartupForMultiCompanyLegacyDataByDefault() {
        when(companyRepository.findAll()).thenReturn(List.of(company(1L), company(2L)));
        stubMissingAssignmentCounts(3L, 1L, 0L, 0L, 0L, 0L, 0L);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(maintenance, "alignExistingCompanyAssignments"));

        verify(jdbcTemplate, never()).update(eq("UPDATE inv_products SET company_id = ? WHERE company_id IS NULL"), anyLong());
        verify(jdbcTemplate, never()).update(eq("UPDATE inv_warehouses SET company_id = ? WHERE company_id IS NULL"), anyLong());
    }

    @Test
    void alignExistingCompanyAssignments_throwsInStrictModeForMultiCompanyLegacyData() {
        SupplyChainSchemaMaintenance strictMaintenance = new SupplyChainSchemaMaintenance(
                jdbcTemplate,
                mock(DataSource.class),
                companyRepository,
                true
        );
        when(companyRepository.findAll()).thenReturn(List.of(company(1L), company(2L)));
        stubMissingAssignmentCounts(3L, 1L, 0L, 0L, 0L, 0L, 0L);

        assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(strictMaintenance, "alignExistingCompanyAssignments")
        );
    }

    private void stubMissingAssignmentCounts(long products,
                                             long warehouses,
                                             long purchaseOrders,
                                             long salesOrders,
                                             long productionOrders,
                                             long serviceRequests,
                                             long cycleCounts) {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inv_products WHERE company_id IS NULL", Long.class))
                .thenReturn(products);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inv_warehouses WHERE company_id IS NULL", Long.class))
                .thenReturn(warehouses);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_purchase_orders WHERE company_id IS NULL", Long.class))
                .thenReturn(purchaseOrders);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_sales_orders WHERE company_id IS NULL", Long.class))
                .thenReturn(salesOrders);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_production_orders WHERE company_id IS NULL", Long.class))
                .thenReturn(productionOrders);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_service_requests WHERE company_id IS NULL", Long.class))
                .thenReturn(serviceRequests);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inv_cycle_counts WHERE company_id IS NULL", Long.class))
                .thenReturn(cycleCounts);
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }
}
