package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.entities.accounting.AssetStatus;
import com.chrono.chrono.services.accounting.AccountingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({AccountingService.class, AssetManagementService.class})
@ActiveProfiles("test")
class AssetManagementServiceTest {

    @Autowired
    private AssetManagementService assetManagementService;

    @Autowired private CompanyRepository companies;
    private Company company;
    @BeforeEach void company() { company = companies.save(new Company("Praxis")); }

    @Test
    void registerAssetPostsAcquisitionEntry() {
        Asset asset = new Asset();
        asset.setAssetName("Laser Cutter");
        asset.setAcquisitionDate(LocalDate.of(2024, 1, 1));
        asset.setAcquisitionCost(new BigDecimal("12000.00"));
        asset.setUsefulLifeMonths(60);
        asset.setResidualValue(new BigDecimal("1000.00"));

        Asset saved = assetManagementService.registerAsset(company, asset);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAcquisitionEntry()).isNotNull();
        assertThat(saved.getAcquisitionEntry().getLines()).hasSize(2);
    }

    @Test
    void runMonthlyDepreciationUpdatesAccumulatedDepreciation() {
        Asset asset = new Asset();
        asset.setAssetName("Server Rack");
        asset.setAcquisitionDate(LocalDate.of(2024, 1, 1));
        asset.setAcquisitionCost(new BigDecimal("6000.00"));
        asset.setUsefulLifeMonths(36);
        asset.setResidualValue(BigDecimal.ZERO);

        Asset saved = assetManagementService.registerAsset(company, asset);
        BigDecimal initial = saved.getAccumulatedDepreciation();

        Asset depreciated = assetManagementService.runMonthlyDepreciation(company, saved);

        assertThat(depreciated.getAccumulatedDepreciation())
                .isGreaterThan(initial)
                .isEqualByComparingTo(new BigDecimal("166.67"));
        assertThat(depreciated.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(depreciated.getLastDepreciationDate()).isEqualTo(LocalDate.now());

        // Run until fully depreciated
        Asset current = depreciated;
        for (int i = 0; i < 40; i++) {
            current = assetManagementService.runMonthlyDepreciation(company, current);
        }
        assertThat(current.getStatus()).isIn(AssetStatus.ACTIVE, AssetStatus.FULLY_DEPRECIATED);
        assertThat(current.getAccumulatedDepreciation())
                .isGreaterThanOrEqualTo(new BigDecimal("6000.00"));
    }
}
