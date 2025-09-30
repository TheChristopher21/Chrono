package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.AssetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AssetDTO {
    private final Long id;
    private final String assetName;
    private final AssetStatus status;
    private final BigDecimal acquisitionCost;
    private final LocalDate acquisitionDate;
    private final Integer usefulLifeMonths;
    private final BigDecimal residualValue;
    private final BigDecimal accumulatedDepreciation;
    private final LocalDate lastDepreciationDate;

    public AssetDTO(Long id, String assetName, AssetStatus status, BigDecimal acquisitionCost,
                    LocalDate acquisitionDate, Integer usefulLifeMonths, BigDecimal residualValue,
                    BigDecimal accumulatedDepreciation, LocalDate lastDepreciationDate) {
        this.id = id;
        this.assetName = assetName;
        this.status = status;
        this.acquisitionCost = acquisitionCost;
        this.acquisitionDate = acquisitionDate;
        this.usefulLifeMonths = usefulLifeMonths;
        this.residualValue = residualValue;
        this.accumulatedDepreciation = accumulatedDepreciation;
        this.lastDepreciationDate = lastDepreciationDate;
    }

    public static AssetDTO from(Asset asset) {
        return new AssetDTO(
                asset.getId(),
                asset.getAssetName(),
                asset.getStatus(),
                asset.getAcquisitionCost(),
                asset.getAcquisitionDate(),
                asset.getUsefulLifeMonths(),
                asset.getResidualValue(),
                asset.getAccumulatedDepreciation(),
                asset.getLastDepreciationDate());
    }

    public Long getId() {
        return id;
    }

    public String getAssetName() {
        return assetName;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public BigDecimal getAcquisitionCost() {
        return acquisitionCost;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public Integer getUsefulLifeMonths() {
        return usefulLifeMonths;
    }

    public BigDecimal getResidualValue() {
        return residualValue;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public LocalDate getLastDepreciationDate() {
        return lastDepreciationDate;
    }
}
