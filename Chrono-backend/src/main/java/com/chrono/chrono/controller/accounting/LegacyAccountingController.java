package com.chrono.chrono.controller.accounting;

import com.chrono.chrono.dto.accounting.AccountDTO;
import com.chrono.chrono.dto.accounting.AssetDTO;
import com.chrono.chrono.dto.accounting.JournalEntryDTO;
import com.chrono.chrono.repositories.accounting.AccountRepository;
import com.chrono.chrono.repositories.accounting.AssetRepository;
import com.chrono.chrono.repositories.accounting.JournalEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Preserved, ownerless history is read-only and operator-only, never guessed. */
@RestController
@RequestMapping("/api/superadmin/accounting/legacy")
@PreAuthorize("hasRole('SUPERADMIN')")
public class LegacyAccountingController {
    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final AssetRepository assets;

    public LegacyAccountingController(AccountRepository accounts, JournalEntryRepository entries, AssetRepository assets) {
        this.accounts = accounts; this.entries = entries; this.assets = assets;
    }

    public record LegacyView(List<AccountDTO> accounts, Page<JournalEntryDTO> journal, List<AssetDTO> assets) {}

    @GetMapping
    @Transactional(readOnly = true)
    public LegacyView list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return new LegacyView(accounts.findByCompanyIsNullOrderByIdAsc().stream().map(AccountDTO::from).toList(),
                entries.findByCompanyIsNullOrderByIdAsc(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))))
                        .map(JournalEntryDTO::from),
                assets.findByCompanyIsNullOrderByIdAsc().stream().map(AssetDTO::from).toList());
    }
}
