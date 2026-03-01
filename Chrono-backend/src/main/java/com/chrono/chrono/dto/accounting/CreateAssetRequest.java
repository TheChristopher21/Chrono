package com.chrono.chrono.dto.accounting;

import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.AssetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateAssetRequest {
    private String assetName;
    private BigDecimal acquisitionCost;
    private LocalDate acquisitionDate;
    private Integer usefulLifeMonths;
    private BigDecimal residualValue;
    private AssetStatus status = AssetStatus.ACTIVE;

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public BigDecimal getAcquisitionCost() {
        return acquisitionCost;
    }

    public void setAcquisitionCost(BigDecimal acquisitionCost) {
        this.acquisitionCost = acquisitionCost;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public void setAcquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = acquisitionDate;
    }

    public Integer getUsefulLifeMonths() {
        return usefulLifeMonths;
    }

    public void setUsefulLifeMonths(Integer usefulLifeMonths) {
        this.usefulLifeMonths = usefulLifeMonths;
    }

    public BigDecimal getResidualValue() {
        return residualValue;
    }

    public void setResidualValue(BigDecimal residualValue) {
        this.residualValue = residualValue;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }

    public Asset toEntity() {
        Asset asset = new Asset();
        asset.setAssetName(assetName);
        asset.setAcquisitionCost(acquisitionCost);
        asset.setAcquisitionDate(acquisitionDate);
        asset.setUsefulLifeMonths(usefulLifeMonths);
        if (residualValue != null) {
            asset.setResidualValue(residualValue);
        }
        asset.setStatus(status);
        return asset;
    }
}
