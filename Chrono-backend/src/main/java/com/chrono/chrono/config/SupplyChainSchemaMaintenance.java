package com.chrono.chrono.config;

import com.chrono.chrono.repositories.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class SupplyChainSchemaMaintenance implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SupplyChainSchemaMaintenance.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final CompanyRepository companyRepository;
    private final boolean failOnMissingCompanyAssignment;

    public SupplyChainSchemaMaintenance(JdbcTemplate jdbcTemplate,
                                        DataSource dataSource,
                                        CompanyRepository companyRepository,
                                        @Value("${app.supply-chain.fail-on-missing-company-assignment:false}")
                                        boolean failOnMissingCompanyAssignment) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.companyRepository = companyRepository;
        this.failOnMissingCompanyAssignment = failOnMissingCompanyAssignment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isMysqlFamily()) {
            return;
        }

        String schema = resolveSchema();
        if (schema == null || schema.isBlank()) {
            logger.warn("Supply Chain schema maintenance skipped because no database schema could be resolved.");
            return;
        }

        alignExistingCompanyAssignments();
        ensureCompanyIndexes(schema);
    }

    private void alignExistingCompanyAssignments() {
        int autoResolved = reconcileMissingCompanyAssignments();
        if (autoResolved > 0) {
            logger.info("Supply Chain company assignments auto-reconciled during startup: {} rows updated.", autoResolved);
        }

        List<Long> companyIds = companyRepository.findAll().stream()
                .map(company -> company.getId())
                .filter(Objects::nonNull)
                .toList();

        Map<String, Long> missingAssignments = collectMissingAssignments();

        if (missingAssignments.isEmpty()) {
            return;
        }

        if (companyIds.size() == 1) {
            Long companyId = companyIds.get(0);
            missingAssignments.keySet().forEach(table -> assignMissingCompany(table, companyId));
            logger.info("Supply Chain company assignments backfilled for single-company setup: {}", missingAssignments);
            return;
        }

        String message = "Supply Chain data contains rows without company assignment: " + missingAssignments
                + ". Startup will continue, but these rows remain outside company-scoped queries until cleaned.";

        if (failOnMissingCompanyAssignment) {
            throw new IllegalStateException(message + " Strict mode is enabled.");
        }

        logger.error(message);
        logRemainingMissingAssignmentIds(missingAssignments);
    }

    private int reconcileMissingCompanyAssignments() {
        int totalUpdated = 0;

        for (int pass = 0; pass < 5; pass++) {
            int updatedThisPass = 0;
            updatedThisPass += smartBackfillDependentTables();
            updatedThisPass += backfillPurchaseOrdersFromProductLines();
            updatedThisPass += backfillSalesOrdersFromProductLines();
            updatedThisPass += backfillProductsFromReferences();
            updatedThisPass += backfillWarehousesFromReferences();
            updatedThisPass += backfillProductionOrdersFromProducts();
            updatedThisPass += backfillCycleCountsFromProductAndWarehouse();

            totalUpdated += updatedThisPass;
            if (updatedThisPass == 0) {
                break;
            }
        }

        return totalUpdated;
    }

    private Map<String, Long> collectMissingAssignments() {
        Map<String, Long> missingAssignments = new LinkedHashMap<>();
        for (String table : List.of(
                "inv_products",
                "inv_warehouses",
                "scm_purchase_orders",
                "scm_sales_orders",
                "scm_production_orders",
                "scm_service_requests",
                "inv_cycle_counts")) {
            long count = countRowsWithoutCompany(table);
            if (count > 0) {
                missingAssignments.put(table, count);
            }
        }
        return missingAssignments;
    }

    private int smartBackfillDependentTables() {
        int updated = 0;
        updated += jdbcTemplate.update("""
                UPDATE scm_production_orders po
                JOIN inv_products p ON p.id = po.product_id
                SET po.company_id = p.company_id
                WHERE po.company_id IS NULL AND p.company_id IS NOT NULL
                """);
        updated += jdbcTemplate.update("""
                UPDATE inv_cycle_counts cc
                JOIN inv_products p ON p.id = cc.product_id
                SET cc.company_id = p.company_id
                WHERE cc.company_id IS NULL AND p.company_id IS NOT NULL
                """);
        return updated;
    }

    private int backfillPurchaseOrdersFromProductLines() {
        return jdbcTemplate.update("""
                UPDATE scm_purchase_orders po
                JOIN (
                    SELECT pol.purchase_order_id AS purchase_order_id, MIN(p.company_id) AS company_id
                    FROM scm_purchase_order_lines pol
                    JOIN inv_products p ON p.id = pol.product_id
                    WHERE p.company_id IS NOT NULL
                    GROUP BY pol.purchase_order_id
                    HAVING COUNT(DISTINCT p.company_id) = 1
                ) inferred ON inferred.purchase_order_id = po.id
                SET po.company_id = inferred.company_id
                WHERE po.company_id IS NULL
                """);
    }

    private int backfillSalesOrdersFromProductLines() {
        return jdbcTemplate.update("""
                UPDATE scm_sales_orders so
                JOIN (
                    SELECT sol.sales_order_id AS sales_order_id, MIN(p.company_id) AS company_id
                    FROM scm_sales_order_lines sol
                    JOIN inv_products p ON p.id = sol.product_id
                    WHERE p.company_id IS NOT NULL
                    GROUP BY sol.sales_order_id
                    HAVING COUNT(DISTINCT p.company_id) = 1
                ) inferred ON inferred.sales_order_id = so.id
                SET so.company_id = inferred.company_id
                WHERE so.company_id IS NULL
                """);
    }

    private int backfillProductsFromReferences() {
        return jdbcTemplate.update("""
                UPDATE inv_products p
                JOIN (
                    SELECT product_id, MIN(company_id) AS company_id
                    FROM (
                        SELECT po.product_id AS product_id, po.company_id AS company_id
                        FROM scm_production_orders po
                        WHERE po.company_id IS NOT NULL
                        UNION ALL
                        SELECT cc.product_id AS product_id, cc.company_id AS company_id
                        FROM inv_cycle_counts cc
                        WHERE cc.company_id IS NOT NULL
                        UNION ALL
                        SELECT pol.product_id AS product_id, po.company_id AS company_id
                        FROM scm_purchase_order_lines pol
                        JOIN scm_purchase_orders po ON po.id = pol.purchase_order_id
                        WHERE po.company_id IS NOT NULL
                        UNION ALL
                        SELECT sol.product_id AS product_id, so.company_id AS company_id
                        FROM scm_sales_order_lines sol
                        JOIN scm_sales_orders so ON so.id = sol.sales_order_id
                        WHERE so.company_id IS NOT NULL
                        UNION ALL
                        SELECT sl.product_id AS product_id, w.company_id AS company_id
                        FROM inv_stock_levels sl
                        JOIN inv_warehouses w ON w.id = sl.warehouse_id
                        WHERE w.company_id IS NOT NULL
                        UNION ALL
                        SELECT sm.product_id AS product_id, w.company_id AS company_id
                        FROM inv_stock_movements sm
                        JOIN inv_warehouses w ON w.id = sm.warehouse_id
                        WHERE w.company_id IS NOT NULL
                    ) inferred_sources
                    GROUP BY product_id
                    HAVING COUNT(DISTINCT company_id) = 1
                ) inferred ON inferred.product_id = p.id
                SET p.company_id = inferred.company_id
                WHERE p.company_id IS NULL
                """);
    }

    private int backfillWarehousesFromReferences() {
        return jdbcTemplate.update("""
                UPDATE inv_warehouses w
                JOIN (
                    SELECT warehouse_id, MIN(company_id) AS company_id
                    FROM (
                        SELECT cc.warehouse_id AS warehouse_id, cc.company_id AS company_id
                        FROM inv_cycle_counts cc
                        WHERE cc.company_id IS NOT NULL
                        UNION ALL
                        SELECT sl.warehouse_id AS warehouse_id, p.company_id AS company_id
                        FROM inv_stock_levels sl
                        JOIN inv_products p ON p.id = sl.product_id
                        WHERE p.company_id IS NOT NULL
                        UNION ALL
                        SELECT sm.warehouse_id AS warehouse_id, p.company_id AS company_id
                        FROM inv_stock_movements sm
                        JOIN inv_products p ON p.id = sm.product_id
                        WHERE p.company_id IS NOT NULL
                    ) inferred_sources
                    GROUP BY warehouse_id
                    HAVING COUNT(DISTINCT company_id) = 1
                ) inferred ON inferred.warehouse_id = w.id
                SET w.company_id = inferred.company_id
                WHERE w.company_id IS NULL
                """);
    }

    private int backfillProductionOrdersFromProducts() {
        return jdbcTemplate.update("""
                UPDATE scm_production_orders po
                JOIN inv_products p ON p.id = po.product_id
                SET po.company_id = p.company_id
                WHERE po.company_id IS NULL AND p.company_id IS NOT NULL
                """);
    }

    private int backfillCycleCountsFromProductAndWarehouse() {
        return jdbcTemplate.update("""
                UPDATE inv_cycle_counts cc
                JOIN inv_products p ON p.id = cc.product_id
                JOIN inv_warehouses w ON w.id = cc.warehouse_id
                SET cc.company_id = COALESCE(
                    CASE
                        WHEN p.company_id IS NOT NULL AND w.company_id IS NOT NULL AND p.company_id = w.company_id THEN p.company_id
                        WHEN p.company_id IS NOT NULL AND w.company_id IS NULL THEN p.company_id
                        WHEN p.company_id IS NULL AND w.company_id IS NOT NULL THEN w.company_id
                        ELSE NULL
                    END,
                    cc.company_id
                )
                WHERE cc.company_id IS NULL
                  AND (
                      (p.company_id IS NOT NULL AND w.company_id IS NOT NULL AND p.company_id = w.company_id)
                      OR (p.company_id IS NOT NULL AND w.company_id IS NULL)
                      OR (p.company_id IS NULL AND w.company_id IS NOT NULL)
                  )
                """);
    }

    private void logRemainingMissingAssignmentIds(Map<String, Long> missingAssignments) {
        Map<String, List<Long>> unresolvedIds = new LinkedHashMap<>();
        for (String tableName : missingAssignments.keySet()) {
            List<Long> ids = findRowsWithoutCompany(tableName, 10);
            if (!ids.isEmpty()) {
                unresolvedIds.put(tableName, ids);
            }
        }
        if (!unresolvedIds.isEmpty()) {
            logger.error("Supply Chain rows still missing company assignment after auto-repair: {}", unresolvedIds);
        }
    }

    private List<Long> findRowsWithoutCompany(String tableName, int limit) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM " + tableName + " WHERE company_id IS NULL ORDER BY id LIMIT ?",
                (rs, rowNum) -> rs.getLong(1),
                limit
        );
        return ids == null ? new ArrayList<>() : ids;
    }

    private long countRowsWithoutCompany(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE company_id IS NULL",
                Long.class
        );
        return count == null ? 0L : count;
    }

    private void assignMissingCompany(String tableName, Long companyId) {
        jdbcTemplate.update("UPDATE " + tableName + " SET company_id = ? WHERE company_id IS NULL", companyId);
    }

    private void ensureCompanyIndexes(String schema) {
        List<ScopedIndexDefinition> definitions = List.of(
                new ScopedIndexDefinition("inv_products", "sku", "idx_inv_products_company", "uk_inv_products_company_sku"),
                new ScopedIndexDefinition("inv_warehouses", "code", "idx_inv_warehouses_company", "uk_inv_warehouses_company_code"),
                new ScopedIndexDefinition("scm_purchase_orders", "order_number", "idx_scm_purchase_orders_company", "uk_scm_purchase_orders_company_number"),
                new ScopedIndexDefinition("scm_sales_orders", "order_number", "idx_scm_sales_orders_company", "uk_scm_sales_orders_company_number"),
                new ScopedIndexDefinition("scm_production_orders", "order_number", "idx_scm_production_orders_company", "uk_scm_production_orders_company_number"),
                new ScopedIndexDefinition("scm_service_requests", null, "idx_scm_service_requests_company", null),
                new ScopedIndexDefinition("inv_cycle_counts", "plan_number", "idx_inv_cycle_counts_company", "uk_inv_cycle_counts_company_plan")
        );

        for (ScopedIndexDefinition definition : definitions) {
            createPlainIndexIfMissing(schema, definition.tableName(), definition.companyIndexName(), "company_id");
            if (definition.businessColumn() != null && definition.uniqueIndexName() != null) {
                assertNoScopedDuplicates(definition.tableName(), definition.businessColumn());
                dropLegacySingleColumnUniqueIndexes(schema, definition.tableName(), definition.businessColumn(), definition.uniqueIndexName());
                createCompositeUniqueIndexIfMissing(schema, definition.tableName(), definition.uniqueIndexName(), "company_id", definition.businessColumn());
            }
        }
    }

    private void assertNoScopedDuplicates(String tableName, String businessColumn) {
        List<String> duplicates = jdbcTemplate.query("""
                        SELECT CONCAT(company_id, ':', %s, ' (', COUNT(*), ')') AS duplicate_key
                        FROM %s
                        WHERE company_id IS NOT NULL
                          AND %s IS NOT NULL
                          AND TRIM(%s) <> ''
                        GROUP BY company_id, %s
                        HAVING COUNT(*) > 1
                        ORDER BY COUNT(*) DESC
                        LIMIT 5
                        """.formatted(businessColumn, tableName, businessColumn, businessColumn, businessColumn),
                (rs, rowNum) -> rs.getString(1)
        );

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Supply Chain duplicates detected in " + tableName
                    + " for scoped key " + businessColumn + ": " + duplicates);
        }
    }

    private void dropLegacySingleColumnUniqueIndexes(String schema,
                                                     String tableName,
                                                     String columnName,
                                                     String keepIndexName) {
        List<String> legacyIndexes = jdbcTemplate.query("""
                        SELECT index_name
                        FROM information_schema.statistics
                        WHERE table_schema = ?
                          AND table_name = ?
                          AND non_unique = 0
                          AND index_name <> 'PRIMARY'
                        GROUP BY index_name
                        HAVING COUNT(*) = 1
                           AND MAX(CASE WHEN column_name = ? THEN 1 ELSE 0 END) = 1
                        """,
                (rs, rowNum) -> rs.getString(1),
                schema,
                tableName,
                columnName
        );

        for (String indexName : legacyIndexes) {
            if (keepIndexName.equalsIgnoreCase(indexName)) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        }
    }

    private void createPlainIndexIfMissing(String schema,
                                           String tableName,
                                           String indexName,
                                           String columnName) {
        if (indexExists(schema, tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
    }

    private void createCompositeUniqueIndexIfMissing(String schema,
                                                     String tableName,
                                                     String indexName,
                                                     String firstColumn,
                                                     String secondColumn) {
        if (indexExists(schema, tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute("CREATE UNIQUE INDEX " + indexName + " ON " + tableName
                + " (" + firstColumn + ", " + secondColumn + ")");
    }

    private boolean indexExists(String schema, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = ?
                          AND table_name = ?
                          AND index_name = ?
                        """,
                Integer.class,
                schema,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }

    private String resolveSchema() {
        String catalog = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getCatalog();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not resolve database schema for Supply Chain maintenance", ex);
        }
    }

    private boolean isMysqlFamily() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            return productName != null && (
                    productName.toLowerCase().contains("mysql")
                            || productName.toLowerCase().contains("mariadb")
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not inspect database product for Supply Chain maintenance", ex);
        }
    }

    private record ScopedIndexDefinition(String tableName,
                                         String businessColumn,
                                         String companyIndexName,
                                         String uniqueIndexName) {
    }
}
