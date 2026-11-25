package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.accounting.*;
import com.chrono.chrono.repositories.accounting.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class AssetManagementService {

    private final AssetRepository assetRepository;
    private final AccountingService accountingService;

    public AssetManagementService(AssetRepository assetRepository,
                                  AccountingService accountingService) {
        this.assetRepository = assetRepository;
        this.accountingService = accountingService;
    }

    @Transactional
    public Asset registerAsset(Company company, Asset asset) {
        AccountingService.requireCompany(company);
        if (asset.getId() != null) throw new IllegalArgumentException("New asset must not have an ID");
        asset.setCompany(company);
        if (asset.getAcquisitionDate() == null) {
            asset.setAcquisitionDate(LocalDate.now());
        }
        Asset saved = assetRepository.save(asset);
        postAcquisition(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Asset> listAssets(Company company) {
        AccountingService.requireCompany(company);
        return assetRepository.findByCompany_IdOrderByIdAsc(company.getId());
    }

    private void postAcquisition(Asset asset) {
        Account fixedAsset = accountingService.ensureAccount(asset.getCompany(), "1510", "Maschinen", AccountType.ASSET);
        Account bank = accountingService.ensureAccount(asset.getCompany(), "1000", "Bank", AccountType.ASSET);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(asset.getAcquisitionDate());
        entry.setDescription("Asset acquisition " + asset.getAssetName());
        entry.setSource("ASSET");
        entry.setDocumentReference("ASSET-" + asset.getId());

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(fixedAsset);
        debit.setDebit(asset.getAcquisitionCost());
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo(asset.getAssetName());

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(bank);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(asset.getAcquisitionCost());
        credit.setMemo("Payment");

        entry.setLines(java.util.List.of(debit, credit));
        JournalEntry posted = accountingService.postEntry(asset.getCompany(), entry);
        asset.setAcquisitionEntry(posted);
        assetRepository.save(asset);
    }

    @Transactional
    public Asset runMonthlyDepreciation(Company company, Asset candidate) {
        AccountingService.requireCompany(company);
        Asset asset = assetRepository.findByIdAndCompany_Id(candidate.getId(), company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            return asset;
        }
        BigDecimal depreciable = asset.getAcquisitionCost()
                .subtract(asset.getResidualValue() != null ? asset.getResidualValue() : BigDecimal.ZERO);
        if (asset.getUsefulLifeMonths() == null || asset.getUsefulLifeMonths() <= 0) {
            return asset;
        }
        BigDecimal monthly = depreciable
                .divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return asset;
        }

        Account expense = accountingService.ensureAccount(company, "5200", "Abschreibungen", AccountType.EXPENSE);
        Account contra = accountingService.ensureAccount(company, "1520", "Anlagenabschreibung", AccountType.CONTRA_ASSET);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setDescription("Depreciation " + asset.getAssetName());
        entry.setSource("ASSET");
        entry.setDocumentReference("DEP-" + asset.getId() + "-" + entry.getEntryDate());

        JournalEntryLine debit = new JournalEntryLine();
        debit.setAccount(expense);
        debit.setDebit(monthly);
        debit.setCredit(BigDecimal.ZERO);
        debit.setMemo("Depreciation");

        JournalEntryLine credit = new JournalEntryLine();
        credit.setAccount(contra);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(monthly);
        credit.setMemo("Accumulated depreciation");

        entry.setLines(java.util.List.of(debit, credit));
        accountingService.postEntry(company, entry);

        asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(monthly));
        asset.setLastDepreciationDate(LocalDate.now());
        if (asset.getAccumulatedDepreciation().compareTo(depreciable) >= 0) {
            asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
        }
        return assetRepository.save(asset);
    }
}
